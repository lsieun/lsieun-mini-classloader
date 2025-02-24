package lsieun.misc;

import java.io.File;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Set;

class BootClassPathHolder {
    static final URLClassPath bootClassPath;

    static {
        URL[] urls;
        if (Launcher.bootClassPath != null) {
            File[] classPath = Launcher.getClassPath(Launcher.bootClassPath);
            Set<File> seenDirs = new HashSet<>();
            for (File file : classPath) {
                File curEntry = file;
                // Negative test used to properly handle
                // nonexistent jars on boot class path
                if (!curEntry.isDirectory()) {
                    curEntry = curEntry.getParentFile();
                }
                if (curEntry != null && seenDirs.add(curEntry)) {
                    MetaIndex.registerDirectory(curEntry);
                }
            }
            urls = Launcher.pathToURLs(classPath);
        }
        else {
            urls = new URL[0];
        }

        bootClassPath = new URLClassPath(urls, Launcher.factory);
    }
}
