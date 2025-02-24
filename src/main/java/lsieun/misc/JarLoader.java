package lsieun.misc;

import lsieun.utils.JarUtils;
import sun.cst.Const;
import sun.misc.FileURLMapper;
import sun.net.util.URLUtil;
import sun.net.www.ParseUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.PrivilegedExceptionAction;
import java.security.cert.Certificate;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/*
 * JarLoader class used to represent a Loader of resources from a JAR URL.
 */
public class JarLoader extends Loader {
    private JarFile jar;
    private final URL code_source_url;
    private JarIndex index;
    private MetaIndex metaIndex;
    private final URLStreamHandler handler;
    private final HashMap<String, Loader> lmap;
    private boolean closed = false;

    /*
     * Creates a new JarLoader for the specified URL referring to a JAR file.
     */
    JarLoader(URL url, URLStreamHandler jarHandler, HashMap<String, Loader> loaderMap) throws IOException {
        super(new URL("jar", "", -1, url + "!/", jarHandler));
        code_source_url = url;
        handler = jarHandler;
        lmap = loaderMap;

        if (!isOptimizable(url)) {
            ensureOpen();
        }
        else {
            String fileName = url.getFile();
            if (fileName != null) {
                fileName = ParseUtil.decode(fileName);
                File f = new File(fileName);
                metaIndex = MetaIndex.forJar(f);
                // If the meta index is found but the file is not
                // installed, set metaIndex to null. A typical
                // senario is charsets.jar which won't be installed
                // when the user is running in certain locale environment.
                // The side effect of null metaIndex will cause
                // ensureOpen get called so that IOException is thrown.
                if (metaIndex != null && !f.exists()) {
                    metaIndex = null;
                }
            }

            // metaIndex is null when either there is no such jar file
            // entry recorded in meta-index file or such jar file is
            // missing in JRE. See bug 6340399.
            if (metaIndex == null) {
                ensureOpen();
            }
        }
    }

    @Override
    public void close() throws IOException {
        // closing is synchronized at higher level
        if (!closed) {
            closed = true;
            // in case not already open.
            ensureOpen();
            jar.close();
        }
    }

    JarFile getJarFile() {
        return jar;
    }

    private boolean isOptimizable(URL url) {
        return "file".equals(url.getProtocol());
    }

    private void ensureOpen() throws IOException {
        if (jar == null) {
            if (Const.DEBUG) {
                System.err.println("Opening " + code_source_url);
                Thread.dumpStack();
            }

            jar = getJarFile(code_source_url);
            index = JarIndex.getJarIndex(jar, metaIndex);
            if (index != null) {
                String[] jarfiles = index.getJarFiles();
                // Add all the dependent URLs to the lmap so that loaders
                // will not be created for them by URLClassPath.getLoader(int)
                // if the same URL occurs later on the main class path.  We set
                // Loader to null here to avoid creating a Loader for each
                // URL until we actually need to try to load something from them.
                for (int i = 0; i < jarfiles.length; i++) {
                    try {
                        URL jarURL = new URL(code_source_url, jarfiles[i]);
                        // If a non-null loader already exists, leave it alone.
                        String urlNoFragString = URLUtil.urlNoFragString(jarURL);
                        if (!lmap.containsKey(urlNoFragString)) {
                            lmap.put(urlNoFragString, null);
                        }
                    } catch (MalformedURLException e) {
                        continue;
                    }
                }
            }
        }
    }

    /* Throws if the given jar file is does not start with the correct LOC */
    static JarFile checkJar(JarFile jar) throws IOException {
        return jar;
    }

    private JarFile getJarFile(URL url) throws IOException {
        // Optimize case where url refers to a local jar file
        if (isOptimizable(url)) {
            FileURLMapper p = new FileURLMapper(url);
            if (!p.exists()) {
                throw new FileNotFoundException(p.getPath());
            }
            return checkJar(new JarFile(p.getPath()));
        }
        URLConnection uc = getBaseURL().openConnection();
        uc.setRequestProperty(URLClassPath.USER_AGENT_JAVA_VERSION, URLClassPath.JAVA_VERSION);
        JarFile jarFile = ((JarURLConnection) uc).getJarFile();
        return checkJar(jarFile);
    }

    /*
     * Returns the index of this JarLoader if it exists.
     */
    JarIndex getIndex() {
        try {
            ensureOpen();
        } catch (IOException e) {
            throw new InternalError(e);
        }
        return index;
    }

    /*
     * Creates the resource and if the check flag is set to true, checks if
     * is its okay to return the resource.
     */
    Resource checkResource(final String name, boolean check, final JarEntry entry) {

        final URL url;
        try {
            url = new URL(getBaseURL(), ParseUtil.encodePath(name, false));
            if (check) {
                URLClassPath.check(url);
            }
        } catch (MalformedURLException e) {
            return null;
            // throw new IllegalArgumentException("name");
        } catch (IOException e) {
            return null;
        } catch (AccessControlException e) {
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
                return code_source_url;
            }

            public InputStream getInputStream() throws IOException {
                return jar.getInputStream(entry);
            }

            public int getContentLength() {
                return (int) entry.getSize();
            }

            public Manifest getManifest() throws IOException {
                return jar.getManifest();
            }

            ;

            public Certificate[] getCertificates() {
                return entry.getCertificates();
            }

            ;

            public CodeSigner[] getCodeSigners() {
                return entry.getCodeSigners();
            }

            ;
        };
    }


    /*
     * Returns true iff atleast one resource in the jar file has the same
     * package name as that of the specified resource name.
     */
    boolean validIndex(final String name) {
        String packageName = name;
        int pos;
        if ((pos = name.lastIndexOf("/")) != -1) {
            packageName = name.substring(0, pos);
        }

        String entryName;
        ZipEntry entry;
        Enumeration<JarEntry> enum_ = jar.entries();
        while (enum_.hasMoreElements()) {
            entry = enum_.nextElement();
            entryName = entry.getName();
            if ((pos = entryName.lastIndexOf("/")) != -1)
                entryName = entryName.substring(0, pos);
            if (entryName.equals(packageName)) {
                return true;
            }
        }
        return false;
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

    /*
     * Returns the JAR Resource for the specified name.
     */
    Resource getResource(final String name, boolean check) {
        if (metaIndex != null) {
            if (!metaIndex.mayContain(name)) {
                return null;
            }
        }

        try {
            ensureOpen();
        } catch (IOException e) {
            throw new InternalError(e);
        }
        final JarEntry entry = jar.getJarEntry(name);
        if (entry != null)
            return checkResource(name, check, entry);

        if (index == null)
            return null;

        HashSet<String> visited = new HashSet<String>();
        return getResource(name, check, visited);
    }

    /*
     * Version of getResource() that tracks the jar files that have been
     * visited by linking through the index files. This helper method uses
     * a HashSet to store the URLs of jar files that have been searched and
     * uses it to avoid going into an infinite loop, looking for a
     * non-existent resource
     */
    Resource getResource(final String name, boolean check, Set<String> visited) {

        Resource res;
        String[] jarFiles;
        int count = 0;
        LinkedList<String> jarFilesList = null;

        /* If there no jar files in the index that can potential contain
         * this resource then return immediately.
         */
        if ((jarFilesList = index.get(name)) == null)
            return null;

        do {
            int size = jarFilesList.size();
            jarFiles = jarFilesList.toArray(new String[size]);
            /* loop through the mapped jar file list */
            while (count < size) {
                String jarName = jarFiles[count++];
                JarLoader newLoader;
                final URL url;

                try {
                    url = new URL(code_source_url, jarName);
                    String urlNoFragString = URLUtil.urlNoFragString(url);
                    if ((newLoader = (JarLoader) lmap.get(urlNoFragString)) == null) {
                        /* no loader has been set up for this jar file before */
                        newLoader = new JarLoader(url, handler, lmap);

                        /* this newly opened jar file has its own index,
                         * merge it into the parent's index, taking into
                         * account the relative path.
                         */
                        JarIndex newIndex = newLoader.getIndex();
                        if (newIndex != null) {
                            int pos = jarName.lastIndexOf("/");
                            newIndex.merge(this.index, (pos == -1 ?
                                    null : jarName.substring(0, pos + 1)));
                        }

                        /* put it in the global hashtable */
                        lmap.put(urlNoFragString, newLoader);
                    }
                } catch (MalformedURLException e) {
                    continue;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }


                /* Note that the addition of the url to the list of visited
                 * jars incorporates a check for presence in the hashmap
                 */
                boolean visitedURL = !visited.add(URLUtil.urlNoFragString(url));
                if (!visitedURL) {
                    try {
                        newLoader.ensureOpen();
                    } catch (IOException e) {
                        throw new InternalError(e);
                    }
                    final JarEntry entry = newLoader.jar.getJarEntry(name);
                    if (entry != null) {
                        return newLoader.checkResource(name, check, entry);
                    }

                    /* Verify that at least one other resource with the
                     * same package name as the lookedup resource is
                     * present in the new jar
                     */
                    if (!newLoader.validIndex(name)) {
                        /* the mapping is wrong */
                        throw new InvalidJarIndexException("Invalid index");
                    }
                }

                /* If newLoader is the current loader or if it is a
                 * loader that has already been searched or if the new
                 * loader does not have an index then skip it
                 * and move on to the next loader.
                 */
                if (visitedURL || newLoader == this ||
                        newLoader.getIndex() == null) {
                    continue;
                }

                /* Process the index of the new loader
                 */
                if ((res = newLoader.getResource(name, check, visited))
                        != null) {
                    return res;
                }
            }
            // Get the list of jar files again as the list could have grown
            // due to merging of index files.
            jarFilesList = index.get(name);

            // If the count is unchanged, we are done.
        } while (count < jarFilesList.size());
        return null;
    }


    /*
     * Returns the JAR file local class path, or null if none.
     */
    URL[] getClassPath() throws IOException {
        if (index != null) {
            return null;
        }

        if (metaIndex != null) {
            return null;
        }

        ensureOpen();
        parseExtensionsDependencies();

        if (JarUtils.jarFileHasClassPathAttribute(jar)) { // Only get manifest when necessary
            Manifest man = jar.getManifest();
            if (man != null) {
                Attributes attr = man.getMainAttributes();
                if (attr != null) {
                    String value = attr.getValue(Attributes.Name.CLASS_PATH);
                    if (value != null) {
                        return parseClassPath(code_source_url, value);
                    }
                }
            }
        }
        return null;
    }

    /*
     * parse the standard extension dependencies
     */
    private void parseExtensionsDependencies() throws IOException {
        // ... 忽略原来的内容
    }

    /*
     * Parses value of the Class-Path manifest attribute and
     *  returns an array of URLs relative to the specified base URL.
     */
    private URL[] parseClassPath(URL base, String value) throws MalformedURLException {
        StringTokenizer st = new StringTokenizer(value);
        URL[] urls = new URL[st.countTokens()];
        int i = 0;
        while (st.hasMoreTokens()) {
            String path = st.nextToken();
            urls[i] = new URL(base, path);
            i++;
        }
        return urls;
    }
}
