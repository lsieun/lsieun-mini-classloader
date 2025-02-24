package sample;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class HelloWorld {
    public static void main(String[] args) throws IOException {
        URL url = new URL("file:/D:/git-repo/learn-java-agent/target/TheAgent.jar");
        JarFile jar = getJarFile(url);
        Manifest manifest = jar.getManifest();

        if (manifest == null) {
            System.out.println("No Manifest");
            return;
        }

        Attributes attr = manifest.getMainAttributes();
        if (attr == null) {
            System.out.println("No MainAttributes");
            return;
        }

        String value = attr.getValue(Attributes.Name.CLASS_PATH);
        if (value == null) {
            System.out.println("No Class-Path");
            return;
        }

        URL[] urls = parseClassPath(url, value);
        for (URL u : urls) {
            System.out.println(u);
        }
    }

    private static JarFile getJarFile(URL url) throws IOException {
        URL baseURL = new URL("jar", "", -1, url + "!/", null);
        URLConnection uc = baseURL.openConnection();
        JarFile jarFile = ((JarURLConnection) uc).getJarFile();
        return jarFile;
    }

    private static URL[] parseClassPath(URL base, String value) throws MalformedURLException {
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
