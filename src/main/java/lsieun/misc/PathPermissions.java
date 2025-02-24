package lsieun.misc;

import sun.security.util.SecurityConstants;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.net.URL;
import java.security.*;

class PathPermissions extends PermissionCollection {
    // use serialVersionUID from JDK 1.2.2 for interoperability
    private static final long serialVersionUID = 8133287259134945693L;

    private File path[];
    private Permissions perms;

    URL codeBase;

    PathPermissions(File path[]) {
        this.path = path;
        this.perms = null;
        this.codeBase = null;
    }

    URL getCodeBase() {
        return codeBase;
    }

    public void add(java.security.Permission permission) {
        throw new SecurityException("attempt to add a permission");
    }

    private synchronized void init() {
        if (perms != null)
            return;

        perms = new Permissions();

        // this is needed to be able to create the classloader itself!
        perms.add(SecurityConstants.CREATE_CLASSLOADER_PERMISSION);

        // add permission to read any "java.*" property
        perms.add(new java.util.PropertyPermission("java.*",
                SecurityConstants.PROPERTY_READ_ACTION));

        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                for (int i = 0; i < path.length; i++) {
                    File f = path[i];
                    String path;
                    try {
                        path = f.getCanonicalPath();
                    } catch (IOException ioe) {
                        path = f.getAbsolutePath();
                    }
                    if (i == 0) {
                        codeBase = Launcher.getFileURL(new File(path));
                    }
                    if (f.isDirectory()) {
                        if (path.endsWith(File.separator)) {
                            perms.add(new FilePermission(path + "-",
                                    SecurityConstants.FILE_READ_ACTION));
                        }
                        else {
                            perms.add(new FilePermission(
                                    path + File.separator + "-",
                                    SecurityConstants.FILE_READ_ACTION));
                        }
                    }
                    else {
                        int endIndex = path.lastIndexOf(File.separatorChar);
                        if (endIndex != -1) {
                            path = path.substring(0, endIndex + 1) + "-";
                            perms.add(new FilePermission(path,
                                    SecurityConstants.FILE_READ_ACTION));
                        }
                        else {
                            // XXX?
                        }
                    }
                }
                return null;
            }
        });
    }

    public boolean implies(java.security.Permission permission) {
        if (perms == null)
            init();
        return perms.implies(permission);
    }

    public java.util.Enumeration<Permission> elements() {
        if (perms == null)
            init();
        synchronized (perms) {
            return perms.elements();
        }
    }

    public String toString() {
        if (perms == null)
            init();
        return perms.toString();
    }
}
