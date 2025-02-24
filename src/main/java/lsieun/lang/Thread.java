package lsieun.lang;

public class Thread {
    /* The context ClassLoader for this thread */
    private AbstractClassLoader contextClassLoader;


    public void setContextClassLoader(AbstractClassLoader cl) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("setContextClassLoader"));
        }
        contextClassLoader = cl;
    }

    public static native Thread currentThread();
}
