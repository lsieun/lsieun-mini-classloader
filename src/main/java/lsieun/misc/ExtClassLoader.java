package lsieun.misc;

import lsieun.lang.AbstractClassLoader;
import lsieun.net.URLClassLoader;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.Vector;

/*
 * The class loader used for loading installed extensions.
 */
class ExtClassLoader extends URLClassLoader {
    /*
     * Creates a new ExtClassLoader for the specified directories.
     */
    public ExtClassLoader(File[] dirs) throws IOException {
        super(getExtURLs(dirs), null, Launcher.factory);
    }

    void addExtURL(URL url) {
        super.addURL(url);
    }


    static {
        AbstractClassLoader.registerAsParallelCapable();
    }

    /**
     * create an ExtClassLoader. The ExtClassLoader is created
     * within a context that limits which files it can read
     */
    public static ExtClassLoader getExtClassLoader() throws IOException {
        final File[] dirs = getExtDirs();
        for (File dir : dirs) {
            MetaIndex.registerDirectory(dir);
        }
        return new ExtClassLoader(dirs);
    }

    private static File[] getExtDirs() {
        String s = System.getProperty("java.ext.dirs");
        File[] dirs;
        if (s != null) {
            StringTokenizer st = new StringTokenizer(s, File.pathSeparator);
            int count = st.countTokens();
            dirs = new File[count];
            for (int i = 0; i < count; i++) {
                dirs[i] = new File(st.nextToken());
            }
        }
        else {
            dirs = new File[0];
        }
        return dirs;
    }

    private static URL[] getExtURLs(File[] dirs) throws IOException {
        Vector<URL> urls = new Vector<URL>();
        for (File dir : dirs) {
            String[] files = dir.list();
            if (files != null) {
                for (String file : files) {
                    if (!file.equals("meta-index")) {
                        File f = new File(dir, file);
                        urls.add(Launcher.getFileURL(f));
                    }
                }
            }
        }
        URL[] ua = new URL[urls.size()];
        urls.copyInto(ua);
        return ua;
    }
}
