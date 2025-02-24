package lsieun.misc;

import sun.net.www.ParseUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.jar.JarFile;

/**
 * Inner class used to represent a loader of resources and classes
 * from a base URL.
 */
public class Loader implements Closeable {
    private final URL base;
    private JarFile jarfile; // if this points to a jar file

    /*
     * Creates a new Loader for the specified URL.
     */
    public Loader(URL url) {
        base = url;
    }

    /*
     * Returns the base URL for this Loader.
     */
    URL getBaseURL() {
        return base;
    }

    URL findResource(final String name, boolean check) {
        URL url;
        try {
            url = new URL(base, ParseUtil.encodePath(name, false));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("name");
        }

        try {
            /*
             * For a HTTP connection we use the HEAD method to
             * check if the resource exists.
             */
            URLConnection uc = url.openConnection();
            if (uc instanceof HttpURLConnection) {
                HttpURLConnection hconn = (HttpURLConnection) uc;
                hconn.setRequestMethod("HEAD");
                if (hconn.getResponseCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    return null;
                }
            }
            else {
                // our best guess for the other cases
                uc.setUseCaches(false);
                InputStream is = uc.getInputStream();
                is.close();
            }
            return url;
        } catch (Exception e) {
            return null;
        }
    }

    Resource getResource(final String name, boolean check) {
        final URL url;
        try {
            url = new URL(base, ParseUtil.encodePath(name, false));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("name");
        }
        final URLConnection uc;
        try {
            uc = url.openConnection();
            InputStream in = uc.getInputStream();
            if (uc instanceof JarURLConnection) {
                /* Need to remember the jar file so it can be closed
                 * in a hurry.
                 */
                JarURLConnection juc = (JarURLConnection) uc;
                jarfile = JarLoader.checkJar(juc.getJarFile());
            }
        } catch (Exception e) {
            return null;
        }

        return new Resource() {
            public String getName() {
                return name;
            }

            public URL getURL() {
                return url;
            }

            public URL getCodeSourceURL() {
                return base;
            }

            public InputStream getInputStream() throws IOException {
                return uc.getInputStream();
            }

            public int getContentLength() throws IOException {
                return uc.getContentLength();
            }
        };
    }

    /*
     * Returns the Resource for the specified name, or null if not
     * found or the caller does not have the permission to get the
     * resource.
     */
    Resource getResource(final String name) {
        return getResource(name, true);
    }

    /*
     * close this loader and release all resources
     * method overridden in sub-classes
     */
    public void close() throws IOException {
        if (jarfile != null) {
            jarfile.close();
        }
    }

    /*
     * Returns the local class path for this loader, or null if none.
     */
    URL[] getClassPath() throws IOException {
        return null;
    }
}
