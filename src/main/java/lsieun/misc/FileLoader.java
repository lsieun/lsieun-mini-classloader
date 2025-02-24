package lsieun.misc;

import sun.net.www.ParseUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/*
 * FileLoader class used to represent a loader of classes and resources
 * from a file URL that refers to a directory.
 */
class FileLoader extends Loader {
    /* Canonicalized File */
    private File dir;

    FileLoader(URL url) throws IOException {
        super(url);
        if (!"file".equals(url.getProtocol())) {
            throw new IllegalArgumentException("url");
        }
        String path = url.getFile().replace('/', File.separatorChar);
        path = ParseUtil.decode(path);
        dir = (new File(path)).getCanonicalFile();
    }

    /*
     * Returns the URL for a resource with the specified name
     */
    URL findResource(final String name, boolean check) {
        Resource rsc = getResource(name, check);
        if (rsc != null) {
            return rsc.getURL();
        }
        return null;
    }

    Resource getResource(final String name, boolean check) {
        final URL url;
        try {
            URL normalizedBase = new URL(getBaseURL(), ".");
            url = new URL(getBaseURL(), ParseUtil.encodePath(name, false));

            if (url.getFile().startsWith(normalizedBase.getFile()) == false) {
                // requested resource had ../..'s in path
                return null;
            }

            // 这个方法的重点是找一个具体的文件，存到file变量里
            final File file;
            if (name.indexOf("..") != -1) {
                file = (new File(dir, name.replace('/', File.separatorChar)))
                        .getCanonicalFile();
                if (!((file.getPath()).startsWith(dir.getPath()))) {
                    /* outside of base dir */
                    return null;
                }
            }
            else {
                file = new File(dir, name.replace('/', File.separatorChar));
            }

            if (file.exists()) {
                return new Resource() {
                    public String getName() {
                        return name;
                    }

                    public URL getURL() {
                        return url;
                    }

                    public URL getCodeSourceURL() {
                        return getBaseURL();
                    }

                    public InputStream getInputStream() throws IOException {
                        return new FileInputStream(file);
                    }

                    public int getContentLength() throws IOException {
                        return (int) file.length();
                    }

                };
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
