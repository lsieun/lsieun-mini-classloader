package lsieun.net;

import lsieun.lang.AbstractClassLoader;

import java.net.URL;
import java.security.AccessControlContext;

final class FactoryURLClassLoader extends URLClassLoader {

    static {
        AbstractClassLoader.registerAsParallelCapable();
    }

    FactoryURLClassLoader(URL[] urls, AbstractClassLoader parent, AccessControlContext acc) {
        super(urls, parent, acc);
    }

    FactoryURLClassLoader(URL[] urls, AccessControlContext acc) {
        super(urls, acc);
    }

    public final Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // First check if we have permission to access the package. This
        // should go away once we've added support for exported packages.
        return super.loadClass(name, resolve);
    }
}
