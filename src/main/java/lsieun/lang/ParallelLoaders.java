package lsieun.lang;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Encapsulates the set of parallel capable loader types.
 */
class ParallelLoaders {
    private ParallelLoaders() {
    }

    // the set of parallel capable loader types
    private static final Set<Class<? extends AbstractClassLoader>> loaderTypes =
            Collections.newSetFromMap(new WeakHashMap<>());

    static {
        synchronized (loaderTypes) {
            loaderTypes.add(AbstractClassLoader.class);
        }
    }

    /**
     * Registers the given class loader type as parallel capabale.
     * Returns {@code true} is successfully registered; {@code false} if
     * loader's super class is not registered.
     */
    static boolean register(Class<? extends AbstractClassLoader> c) {
        synchronized (loaderTypes) {
            if (loaderTypes.contains(c.getSuperclass())) {
                // register the class loader as parallel capable
                // if and only if all of its super classes are.
                // Note: given current classloading sequence, if
                // the immediate super class is parallel capable,
                // all the super classes higher up must be too.
                loaderTypes.add(c);
                return true;
            }
            else {
                return false;
            }
        }
    }

    /**
     * Returns {@code true} if the given class loader type is
     * registered as parallel capable.
     */
    static boolean isRegistered(Class<? extends AbstractClassLoader> c) {
        synchronized (loaderTypes) {
            return loaderTypes.contains(c);
        }
    }
}
