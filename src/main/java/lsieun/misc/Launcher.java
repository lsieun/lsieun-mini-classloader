package lsieun.misc;

import lsieun.lang.AbstractClassLoader;
import sun.net.www.ParseUtil;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * This Launcher class is used by the system to launch the main application.
 */
public class Launcher {
    static final URLStreamHandlerFactory factory = new Factory();
    private static final Launcher launcher = new Launcher();
    static final String bootClassPath = System.getProperty("sun.boot.class.path");

    public static Launcher getLauncher() {
        return launcher;
    }

    private final AbstractClassLoader loader;

    public Launcher() {
        // Create the extension class loader
        AbstractClassLoader extClassLoader;
        try {
            extClassLoader = ExtClassLoader.getExtClassLoader();
        } catch (IOException e) {
            throw new InternalError("Could not create extension class loader", e);
        }

        // Now create the class loader to use to launch the application
        try {
            loader = AppClassLoader.getAppClassLoader(extClassLoader);
        } catch (IOException e) {
            throw new InternalError("Could not create application class loader", e);
        }

        // Also set the context class loader for the primordial thread.
        lsieun.lang.Thread.currentThread().setContextClassLoader(loader);

        // Finally, install a security manager if requested
        String s = System.getProperty("java.security.manager");
        if (s != null) {
            SecurityManager sm = null;
            if ("".equals(s) || "default".equals(s)) {
                sm = new java.lang.SecurityManager();
            }
            else {
                try {
                    sm = (SecurityManager) loader.loadClass(s).newInstance();
                } catch (IllegalAccessException | InstantiationException |
                        ClassNotFoundException | ClassCastException ignored) {
                }
            }
            if (sm != null) {
                System.setSecurityManager(sm);
            }
            else {
                throw new InternalError("Could not create SecurityManager: " + s);
            }
        }
    }

    /*
     * Returns the class loader used to launch the main application.
     */
    public AbstractClassLoader getClassLoader() {
        return loader;
    }

    public static URLClassPath getBootstrapClassPath() {
        return BootClassPathHolder.bootClassPath;
    }

    static URL[] pathToURLs(File[] path) {
        URL[] urls = new URL[path.length];
        for (int i = 0; i < path.length; i++) {
            urls[i] = getFileURL(path[i]);
        }
        // DEBUG
        for (int i = 0; i < urls.length; i++) {
            System.out.println("urls[" + i + "] = " + '"' + urls[i] + '"');
        }
        return urls;
    }

    static File[] getClassPath(String classpath) {
        File[] path;
        if (classpath != null) {
            int count = 0, maxCount = 1;
            int pos, lastPos = 0;

            // Count the number of separators first
            while ((pos = classpath.indexOf(File.pathSeparator, lastPos)) != -1) {
                maxCount++;
                lastPos = pos + 1;
            }
            path = new File[maxCount];
            lastPos = 0;

            // Now scan for each path component
            while ((pos = classpath.indexOf(File.pathSeparator, lastPos)) != -1) {
                if (pos - lastPos > 0) {
                    path[count++] = new File(classpath.substring(lastPos, pos));
                }
                else {
                    // empty path component translates to "."
                    path[count++] = new File(".");
                }
                lastPos = pos + 1;
            }

            // Make sure we include the last path component
            if (lastPos < classpath.length()) {
                path[count++] = new File(classpath.substring(lastPos));
            }
            else {
                path[count++] = new File(".");
            }

            // Trim array to correct size
            if (count != maxCount) {
                File[] tmp = new File[count];
                System.arraycopy(path, 0, tmp, 0, count);
                path = tmp;
            }
        }
        else {
            path = new File[0];
        }

        // DEBUG
        for (int i = 0; i < path.length; i++) {
            System.out.println("path[" + i + "] = " + '"' + path[i] + '"');
        }
        return path;
    }

    static URL getFileURL(File file) {
        try {
            file = file.getCanonicalFile();
        } catch (IOException ignored) {
        }

        try {
            return ParseUtil.fileToEncodedURL(file);
        } catch (MalformedURLException e) {
            // Should never happen since we specify the protocol...
            throw new InternalError(e);
        }
    }

    /*
     * The stream handler factory for loading system protocol handlers.
     */
    private static class Factory implements URLStreamHandlerFactory {
        private static final String PREFIX = "sun.net.www.protocol";

        public URLStreamHandler createURLStreamHandler(String protocol) {
            String name = PREFIX + "." + protocol + ".Handler";
            try {
                Class<?> c = Class.forName(name);
                return (URLStreamHandler) c.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new InternalError("could not load " + protocol + "system protocol handler", e);
            }
        }
    }
}

