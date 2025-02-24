package lsieun.net;

import lsieun.lang.AbstractClassLoader;
import lsieun.misc.Resource;
import lsieun.misc.URLClassPath;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandlerFactory;
import java.security.*;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * This class loader is used to load classes and resources from a search
 * path of URLs referring to both JAR files and directories. Any URL that
 * ends with a '/' is assumed to refer to a directory. Otherwise, the URL
 * is assumed to refer to a JAR file which will be opened as needed.
 * <p>
 * The AccessControlContext of the thread that created the instance of
 * URLClassLoader will be used when subsequently loading classes and
 * resources.
 * <p>
 * The classes that are loaded are by default granted permission only to
 * access the URLs specified when the URLClassLoader was created.
 *
 * @author David Connelly
 * @since 1.2
 */
public class URLClassLoader extends AbstractClassLoader implements Closeable {
    /* The search path for classes and resources */
    private final URLClassPath urlClassPath;

    /* The context to be used when loading classes and resources */
    private final AccessControlContext acc;

    /**
     * Constructs a new URLClassLoader for the given URLs. The URLs will be
     * searched in the order specified for classes and resources after first
     * searching in the specified parent class loader. Any URL that ends with
     * a '/' is assumed to refer to a directory. Otherwise, the URL is assumed
     * to refer to a JAR file which will be downloaded and opened as needed.
     *
     * <p>If there is a security manager, this method first
     * calls the security manager's {@code checkCreateClassLoader} method
     * to ensure creation of a class loader is allowed.
     *
     * @param urls   the URLs from which to load classes and resources
     * @param parent the parent class loader for delegation
     * @throws SecurityException    if a security manager exists and its
     *                              {@code checkCreateClassLoader} method doesn't allow
     *                              creation of a class loader.
     * @throws NullPointerException if {@code urls} is {@code null}.
     * @see SecurityManager#checkCreateClassLoader
     */
    public URLClassLoader(URL[] urls, AbstractClassLoader parent) {
        super(parent);
        // this is to make the stack depth consistent with 1.1
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        urlClassPath = new URLClassPath(urls);
        this.acc = AccessController.getContext();
    }

    URLClassLoader(URL[] urls, AbstractClassLoader parent, AccessControlContext acc) {
        super(parent);
        // this is to make the stack depth consistent with 1.1
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        urlClassPath = new URLClassPath(urls);
        this.acc = acc;
    }

    /**
     * Constructs a new URLClassLoader for the specified URLs using the
     * default delegation parent {@code ClassLoader}. The URLs will
     * be searched in the order specified for classes and resources after
     * first searching in the parent class loader. Any URL that ends with
     * a '/' is assumed to refer to a directory. Otherwise, the URL is
     * assumed to refer to a JAR file which will be downloaded and opened
     * as needed.
     *
     * <p>If there is a security manager, this method first
     * calls the security manager's {@code checkCreateClassLoader} method
     * to ensure creation of a class loader is allowed.
     *
     * @param urls the URLs from which to load classes and resources
     * @throws SecurityException    if a security manager exists and its
     *                              {@code checkCreateClassLoader} method doesn't allow
     *                              creation of a class loader.
     * @throws NullPointerException if {@code urls} is {@code null}.
     * @see SecurityManager#checkCreateClassLoader
     */
    public URLClassLoader(URL[] urls) {
        super();
        // this is to make the stack depth consistent with 1.1
        urlClassPath = new URLClassPath(urls);
        this.acc = AccessController.getContext();
    }

    URLClassLoader(URL[] urls, AccessControlContext acc) {
        super();
        // this is to make the stack depth consistent with 1.1
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        urlClassPath = new URLClassPath(urls);
        this.acc = acc;
    }

    /**
     * Constructs a new URLClassLoader for the specified URLs, parent
     * class loader, and URLStreamHandlerFactory. The parent argument
     * will be used as the parent class loader for delegation. The
     * factory argument will be used as the stream handler factory to
     * obtain protocol handlers when creating new jar URLs.
     *
     * <p>If there is a security manager, this method first
     * calls the security manager's {@code checkCreateClassLoader} method
     * to ensure creation of a class loader is allowed.
     *
     * @param urls    the URLs from which to load classes and resources
     * @param parent  the parent class loader for delegation
     * @param factory the URLStreamHandlerFactory to use when creating URLs
     * @throws SecurityException    if a security manager exists and its
     *                              {@code checkCreateClassLoader} method doesn't allow
     *                              creation of a class loader.
     * @throws NullPointerException if {@code urls} is {@code null}.
     * @see SecurityManager#checkCreateClassLoader
     */
    public URLClassLoader(URL[] urls, AbstractClassLoader parent,
                          URLStreamHandlerFactory factory) {
        super(parent);
        // this is to make the stack depth consistent with 1.1
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        urlClassPath = new URLClassPath(urls, factory);
        acc = AccessController.getContext();
    }

    /* A map (used as a set) to keep track of closeable local resources
     * (either JarFiles or FileInputStreams). We don't care about
     * Http resources since they don't need to be closed.
     *
     * If the resource is coming from a jar file
     * we keep a (weak) reference to the JarFile object which can
     * be closed if URLClassLoader.close() called. Due to jar file
     * caching there will typically be only one JarFile object
     * per underlying jar file.
     *
     * For file resources, which is probably a less common situation
     * we have to keep a weak reference to each stream.
     */

    private final WeakHashMap<Closeable, Void> closeables = new WeakHashMap<>();

    /**
     * Returns an input stream for reading the specified resource.
     * If this loader is closed, then any resources opened by this method
     * will be closed.
     *
     * <p> The search order is described in the documentation for {@link
     * #getResource(String)}.  </p>
     *
     * @param name The resource name
     * @return An input stream for reading the resource, or {@code null}
     * if the resource could not be found
     * @since 1.7
     */
    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        try {
            if (url == null) {
                return null;
            }
            URLConnection urlc = url.openConnection();
            InputStream is = urlc.getInputStream();
            if (urlc instanceof JarURLConnection) {
                JarURLConnection juc = (JarURLConnection) urlc;
                JarFile jar = juc.getJarFile();
                synchronized (closeables) {
                    if (!closeables.containsKey(jar)) {
                        closeables.put(jar, null);
                    }
                }
            }
            else if (urlc instanceof sun.net.www.protocol.file.FileURLConnection) {
                synchronized (closeables) {
                    closeables.put(is, null);
                }
            }
            return is;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Closes this URLClassLoader, so that it can no longer be used to load
     * new classes or resources that are defined by this loader.
     * Classes and resources defined by any of this loader's parents in the
     * delegation hierarchy are still accessible. Also, any classes or resources
     * that are already loaded, are still accessible.
     * <p>
     * In the case of jar: and file: URLs, it also closes any files
     * that were opened by it. If another thread is loading a
     * class when the {@code close} method is invoked, then the result of
     * that load is undefined.
     * <p>
     * The method makes a best effort attempt to close all opened files,
     * by catching {@link IOException}s internally. Unchecked exceptions
     * and errors are not caught. Calling close on an already closed
     * loader has no effect.
     * <p>
     *
     * @throws IOException       if closing any file opened by this class loader
     *                           resulted in an IOException. Any such exceptions are caught internally.
     *                           If only one is caught, then it is re-thrown. If more than one exception
     *                           is caught, then the second and following exceptions are added
     *                           as suppressed exceptions of the first one caught, which is then re-thrown.
     * @throws SecurityException if a security manager is set, and it denies
     *                           {@link RuntimePermission}{@code ("closeClassLoader")}
     * @since 1.7
     */
    public void close() throws IOException {
        List<IOException> errors = urlClassPath.closeLoaders();

        // now close any remaining streams.

        synchronized (closeables) {
            Set<Closeable> keys = closeables.keySet();
            for (Closeable c : keys) {
                try {
                    c.close();
                } catch (IOException ioex) {
                    errors.add(ioex);
                }
            }
            closeables.clear();
        }

        if (errors.isEmpty()) {
            return;
        }

        IOException firstex = errors.remove(0);

        // Suppress any remaining exceptions

        for (IOException error : errors) {
            firstex.addSuppressed(error);
        }
        throw firstex;
    }

    /**
     * Appends the specified URL to the list of URLs to search for
     * classes and resources.
     * <p>
     * If the URL specified is {@code null} or is already in the
     * list of URLs, or if this loader is closed, then invoking this
     * method has no effect.
     *
     * @param url the URL to be added to the search path of URLs
     */
    protected void addURL(URL url) {
        urlClassPath.addURL(url);
    }

    /**
     * Returns the search path of URLs for loading classes and resources.
     * This includes the original list of URLs specified to the constructor,
     * along with any URLs subsequently appended by the addURL() method.
     *
     * @return the search path of URLs for loading classes and resources.
     */
    public URL[] getURLs() {
        return urlClassPath.getURLs();
    }

    /**
     * Finds and loads the class with the specified name from the URL search
     * path. Any URLs referring to JAR files are loaded and opened as needed
     * until the class is found.
     *
     * @param name the name of the class
     * @return the resulting class
     * @throws ClassNotFoundException if the class could not be found, or if the loader is closed.
     * @throws NullPointerException   if {@code name} is {@code null}.
     */
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        String path = name.replace('.', '/').concat(".class");
        Resource res = urlClassPath.getResource(path, false);
        if (res != null) {
            try {
                return defineClass(name, res);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
        else {
            throw new ClassNotFoundException(name);
        }
    }

    /*
     * Defines a Class using the class bytes obtained from the specified
     * Resource. The resulting Class must be resolved before it can be
     * used.
     */
    private Class<?> defineClass(String name, Resource res) throws IOException {
        long t0 = System.nanoTime();
        int i = name.lastIndexOf('.');
        URL url = res.getCodeSourceURL();

        // Now read the class bytes and define the class
        java.nio.ByteBuffer bb = res.getByteBuffer();
        if (bb != null) {
            // Use (direct) ByteBuffer:
            CodeSigner[] signers = res.getCodeSigners();
            CodeSource cs = new CodeSource(url, signers);
            sun.misc.PerfCounter.getReadClassBytesTime().addElapsedTimeFrom(t0);
            return defineClass(name, bb, null);
        }
        else {
            byte[] b = res.getBytes();
            // must read certificates AFTER reading bytes.
            CodeSigner[] signers = res.getCodeSigners();
            CodeSource cs = new CodeSource(url, signers);
            sun.misc.PerfCounter.getReadClassBytesTime().addElapsedTimeFrom(t0);
            return defineClass(name, b, 0, b.length);
        }
    }

    /*
     * Returns true if the specified package name is sealed according to the given manifest.
     */
    private boolean isSealed(String name, Manifest man) {
        String path = name.replace('.', '/').concat("/");
        Attributes attr = man.getAttributes(path);
        String sealed = null;
        if (attr != null) {
            sealed = attr.getValue(Name.SEALED);
        }
        if (sealed == null) {
            if ((attr = man.getMainAttributes()) != null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        return "true".equalsIgnoreCase(sealed);
    }

    /**
     * Finds the resource with the specified name on the URL search path.
     *
     * @param name the name of the resource
     * @return a {@code URL} for the resource, or {@code null}
     * if the resource could not be found, or if the loader is closed.
     */
    public URL findResource(final String name) {
        /*
         * The same restriction to finding classes applies to resources
         */
        URL url = urlClassPath.findResource(name, true);

        return url != null ? urlClassPath.checkURL(url) : null;
    }

    /**
     * Returns an Enumeration of URLs representing all of the resources
     * on the URL search path having the specified name.
     *
     * @param name the resource name
     * @return an {@code Enumeration} of {@code URL}s If the loader is closed, the Enumeration will be empty.
     * @throws IOException if an I/O exception occurs
     */
    public Enumeration<URL> findResources(final String name) throws IOException {
        final Enumeration<URL> e = urlClassPath.findResources(name, true);

        return new Enumeration<URL>() {
            private URL url = null;

            private boolean next() {
                if (url != null) {
                    return true;
                }
                do {
                    URL u;
                    if (!e.hasMoreElements()){
                        u = null;
                    }
                    else {
                        u = e.nextElement();
                    }

                    if (u == null)
                        break;
                    url = urlClassPath.checkURL(u);
                } while (url == null);
                return url != null;
            }

            public URL nextElement() {
                if (!next()) {
                    throw new NoSuchElementException();
                }
                URL u = url;
                url = null;
                return u;
            }

            public boolean hasMoreElements() {
                return next();
            }
        };
    }

    /**
     * Creates a new instance of URLClassLoader for the specified
     * URLs and parent class loader. If a security manager is
     * installed, the {@code loadClass} method of the URLClassLoader
     * returned by this method will invoke the
     * {@code SecurityManager.checkPackageAccess} method before
     * loading the class.
     *
     * @param urls   the URLs to search for classes and resources
     * @param parent the parent class loader for delegation
     * @return the resulting class loader
     * @throws NullPointerException if {@code urls} is {@code null}.
     */
    public static URLClassLoader newInstance(final URL[] urls, final AbstractClassLoader parent) {
        // Save the caller's context
        final AccessControlContext acc = AccessController.getContext();
        // Need a privileged block to create the class loader
        URLClassLoader ucl = new FactoryURLClassLoader(urls, parent, acc);
        return ucl;
    }

    /**
     * Creates a new instance of URLClassLoader for the specified
     * URLs and default parent class loader. If a security manager is
     * installed, the {@code loadClass} method of the URLClassLoader
     * returned by this method will invoke the
     * {@code SecurityManager.checkPackageAccess} before
     * loading the class.
     *
     * @param urls the URLs to search for classes and resources
     * @return the resulting class loader
     * @throws NullPointerException if {@code urls} is {@code null}.
     */
    public static URLClassLoader newInstance(final URL[] urls) {
        // Save the caller's context
        final AccessControlContext acc = AccessController.getContext();
        // Need a privileged block to create the class loader
        URLClassLoader ucl = new FactoryURLClassLoader(urls, acc);
        return ucl;
    }

    static {
        AbstractClassLoader.registerAsParallelCapable();
    }
}

