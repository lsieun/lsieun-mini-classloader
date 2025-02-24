package lsieun.misc;

import lsieun.lang.AbstractClassLoader;
import lsieun.net.URLClassLoader;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * The class loader used for loading from java.class.path.
 * runs in a restricted security context.
 */
class AppClassLoader extends URLClassLoader {
    /*
     * Creates a new AppClassLoader
     */
    AppClassLoader(URL[] urls, AbstractClassLoader parent) {
        super(urls, parent, Launcher.factory);
    }

    /**
     * This class loader supports dynamic additions to the class path at runtime.
     *
     * @see java.lang.instrument.Instrumentation#appendToSystemClassPathSearch
     */
    private void appendToClassPathForInstrumentation(String path) {
        assert (Thread.holdsLock(this));

        // addURL is a no-op if path already contains the URL
        super.addURL(Launcher.getFileURL(new File(path)));
    }

    static {
        AbstractClassLoader.registerAsParallelCapable();
    }

    public static AbstractClassLoader getAppClassLoader(final AbstractClassLoader extClassLoader) throws IOException {
        final String classpath = System.getProperty("java.class.path");
        final File[] path = (classpath == null) ? new File[0] : Launcher.getClassPath(classpath);

        // Note: on bugid 4256530
        // Prior implementations of this doPrivileged() block supplied
        // a rather restrictive ACC via a call to the private method
        // AppClassLoader.getContext(). This proved overly restrictive
        // when loading  classes. Specifically it prevent
        // accessClassInPackage.sun.* grants from being honored.
        //
        URL[] urls = (classpath == null) ? new URL[0] : Launcher.pathToURLs(path);
        return new AppClassLoader(urls, extClassLoader);
    }

}
