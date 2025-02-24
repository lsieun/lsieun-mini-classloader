package lsieun.utils;

import java.lang.reflect.Method;
import java.util.jar.JarFile;

public class JarUtils {
    public static boolean jarFileHasClassPathAttribute(JarFile jarFile) {
        try {
            Class<JarFile> clazz = JarFile.class;
            Method m = clazz.getDeclaredMethod("hasClassPathAttribute");
            m.setAccessible(true);
            Object result = m.invoke(jarFile);
            return (boolean) result;
        } catch (Exception ex) {
            ex.printStackTrace(System.out);
        }
        return false;
    }
}
