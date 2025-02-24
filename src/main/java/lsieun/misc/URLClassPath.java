package lsieun.misc;

import sun.net.util.URLUtil;
import sun.net.www.ParseUtil;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * This class is used to maintain a search path of URLs for
 * loading classes and resources
 * from both JAR files and directories.
 *
 * @author David Connelly
 */
public class URLClassPath {
    final static String USER_AGENT_JAVA_VERSION = "UA-Java-Version";
    final static String JAVA_VERSION;
    public static final boolean DEBUG;
    public static final boolean DISABLE_JAR_CHECKING;

    static {
        JAVA_VERSION = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("java.version"));
        DEBUG = (java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("sun.misc.URLClassPath.debug")) != null);
        String p = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction("sun.misc.URLClassPath.disableJarChecking"));
        DISABLE_JAR_CHECKING = p != null ? p.equals("true") || p.equals("") : false;
    }

    /* The original search path of URLs. */
    private ArrayList<URL> path = new ArrayList<>();

    /* The stack of unopened URLs */
    Stack<URL> urls = new Stack<>();

    /* The resulting search path of Loaders */
    ArrayList<Loader> loaders = new ArrayList<>();

    /* Map of each URL opened to its corresponding Loader */
    HashMap<String, Loader> lmap = new HashMap<>();

    /* The jar protocol handler to use when creating new URLs */
    private URLStreamHandler jarHandler;

    /* Whether this URLClassLoader has been closed yet */
    private boolean closed = false;

    /**
     * Creates a new URLClassPath for the given URLs.
     * The URLs will be searched in the order specified for classes and resources.
     * A URL ending with a '/' is assumed to refer to a directory.
     * Otherwise, the URL is assumed to refer to a JAR file.
     *
     * @param urls    the directory and JAR file URLs to search for classes and resources
     * @param factory the URLStreamHandlerFactory to use when creating new URLs
     */
    public URLClassPath(URL[] urls, URLStreamHandlerFactory factory) {
        for (int i = 0; i < urls.length; i++) {
            path.add(urls[i]);
        }
        push(urls);
        if (factory != null) {
            jarHandler = factory.createURLStreamHandler("jar");
        }
    }

    public URLClassPath(URL[] urls) {
        this(urls, null);
    }

    public synchronized List<IOException> closeLoaders() {
        if (closed) {
            return Collections.emptyList();
        }
        List<IOException> result = new LinkedList<>();
        for (Loader loader : loaders) {
            try {
                loader.close();
            } catch (IOException e) {
                result.add(e);
            }
        }
        closed = true;
        return result;
    }

    /**
     * Appends the specified URL to the search path of directory and JAR
     * file URLs from which to load classes and resources.
     * <p>
     * If the URL specified is null or is already in the list of
     * URLs, then invoking this method has no effect.
     */
    public synchronized void addURL(URL url) {
        if (closed)
            return;
        synchronized (urls) {
            if (url == null || path.contains(url))
                return;

            urls.add(0, url);
            path.add(url);
        }
    }

    /**
     * Returns the original search path of URLs.
     */
    public URL[] getURLs() {
        synchronized (urls) {
            return path.toArray(new URL[path.size()]);
        }
    }

    /**
     * Finds the resource with the specified name on the URL search path
     * or null if not found or security check fails.
     *
     * @param name  the name of the resource
     * @param check whether to perform a security check
     * @return a <code>URL</code> for the resource, or <code>null</code>
     * if the resource could not be found.
     */
    public URL findResource(String name, boolean check) {
        Loader loader;
        for (int i = 0; (loader = getLoader(i)) != null; i++) {
            URL url = loader.findResource(name, check);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    /**
     * Finds the first Resource on the URL search path
     * which has the specified name.
     * Returns null if no Resource could be found.
     *
     * @param name  the name of the Resource
     * @param check whether to perform a security check
     * @return the Resource, or null if not found
     */
    public Resource getResource(String name, boolean check) {
        if (DEBUG) {
            System.err.println("URLClassPath.getResource(\"" + name + "\")");
        }

        Loader loader;
        for (int i = 0; (loader = getLoader(i)) != null; i++) {
            Resource res = loader.getResource(name, check);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    /**
     * Finds all resources on the URL search path with the given name.
     * Returns an enumeration of the URL objects.
     *
     * @param name the resource name
     * @return an Enumeration of all the urls having the specified name
     */
    public Enumeration<URL> findResources(final String name,
                                          final boolean check) {
        return new Enumeration<URL>() {
            private int index = 0;
            private URL url = null;

            private boolean next() {
                if (url != null) {
                    return true;
                }
                else {
                    Loader loader;
                    while ((loader = getLoader(index++)) != null) {
                        url = loader.findResource(name, check);
                        if (url != null) {
                            return true;
                        }
                    }
                    return false;
                }
            }

            public boolean hasMoreElements() {
                return next();
            }

            public URL nextElement() {
                if (!next()) {
                    throw new NoSuchElementException();
                }
                URL u = url;
                url = null;
                return u;
            }
        };
    }

    public Resource getResource(String name) {
        return getResource(name, true);
    }

    /**
     * Finds all resources on the URL search path with the given name.
     * Returns an enumeration of the Resource objects.
     *
     * @param name the resource name
     * @return an Enumeration of all the resources having the specified name
     */
    public Enumeration<Resource> getResources(final String name,
                                              final boolean check) {
        return new Enumeration<Resource>() {
            private int index = 0;
            private Resource res = null;

            private boolean next() {
                if (res != null) {
                    return true;
                }
                else {
                    Loader loader;
                    while ((loader = getLoader(index++)) != null) {
                        res = loader.getResource(name, check);
                        if (res != null) {
                            return true;
                        }
                    }
                    return false;
                }
            }

            public boolean hasMoreElements() {
                return next();
            }

            public Resource nextElement() {
                if (!next()) {
                    throw new NoSuchElementException();
                }
                Resource r = res;
                res = null;
                return r;
            }
        };
    }

    public Enumeration<Resource> getResources(final String name) {
        return getResources(name, true);
    }

    /*
     * Returns the Loader at the specified position in the URL search
     * path. The URLs are opened and expanded as needed. Returns null
     * if the specified index is out of range.
     */
    private synchronized Loader getLoader(int index) {
        if (closed) {
            return null;
        }
        // Expand URL search path until the request can be satisfied
        // or the URL stack is empty.
        while (loaders.size() < index + 1) {
            // Pop the next URL from the URL stack
            URL url;
            synchronized (urls) {
                if (urls.empty()) {
                    return null;
                }
                else {
                    url = urls.pop();
                }
            }
            // Skip this URL if it already has a Loader. (Loader
            // may be null in the case where URL has not been opened
            // but is referenced by a JAR index.)
            String urlNoFragString = URLUtil.urlNoFragString(url);
            if (lmap.containsKey(urlNoFragString)) {
                continue;
            }
            // Otherwise, create a new Loader for the URL.
            Loader loader;
            try {
                loader = getLoader(url);
                // If the loader defines a local class path then add the
                // URLs to the list of URLs to be opened.
                URL[] urls = loader.getClassPath();
                if (urls != null) {
                    push(urls);
                }
            } catch (IOException e) {
                // Silently ignore for now...
                continue;
            }
            // Finally, add the Loader to the search path.
            loaders.add(loader);
            lmap.put(urlNoFragString, loader);
        }
        return loaders.get(index);
    }

    /*
     * Returns the Loader for the specified base URL.
     */
    private Loader getLoader(final URL url) throws IOException {
        try {
            return java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedExceptionAction<Loader>() {
                        public Loader run() throws IOException {
                            String file = url.getFile();
                            if (file != null && file.endsWith("/")) {
                                if ("file".equals(url.getProtocol())) {
                                    return new FileLoader(url);
                                }
                                else {
                                    return new Loader(url);
                                }
                            }
                            else {
                                return new JarLoader(url, jarHandler, lmap);
                            }
                        }
                    });
        } catch (java.security.PrivilegedActionException pae) {
            throw (IOException) pae.getException();
        }
    }

    /*
     * Pushes the specified URLs onto the list of unopened URLs.
     */
    private void push(URL[] us) {
        synchronized (urls) {
            for (int i = us.length - 1; i >= 0; --i) {
                urls.push(us[i]);
            }
        }
    }

    /**
     * Convert class path specification into an array of file URLs.
     * <p>
     * The path of the file is encoded before conversion into URL
     * form so that reserved characters can safely appear in the path.
     */
    public static URL[] pathToURLs(String path) {
        StringTokenizer st = new StringTokenizer(path, File.pathSeparator);
        URL[] urls = new URL[st.countTokens()];
        int count = 0;
        while (st.hasMoreTokens()) {
            File f = new File(st.nextToken());
            try {
                f = new File(f.getCanonicalPath());
            } catch (IOException x) {
                // use the non-canonicalized filename
            }
            try {
                urls[count++] = ParseUtil.fileToEncodedURL(f);
            } catch (IOException x) {
            }
        }

        if (urls.length != count) {
            URL[] tmp = new URL[count];
            System.arraycopy(urls, 0, tmp, 0, count);
            urls = tmp;
        }
        return urls;
    }

    /*
     * Check whether the resource URL should be returned.
     * Return null on security check failure.
     * Called by java.net.URLClassLoader.
     */
    public URL checkURL(URL url) {
        try {
            check(url);
        } catch (Exception e) {
            return null;
        }

        return url;
    }

    /*
     * Check whether the resource URL should be returned.
     * Throw exception on failure.
     * Called internally within this file.
     */
    static void check(URL url) throws IOException {

    }

}