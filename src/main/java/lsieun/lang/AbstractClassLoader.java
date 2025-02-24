package lsieun.lang;

import lsieun.misc.*;
import sun.misc.CompoundEnumeration;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class loader is an object that is responsible for loading classes. The
 * class <b>ClassLoader</b> is an abstract class.
 * Given the <b>binary name</b> of a class, a class loader should attempt to
 * locate or generate data that constitutes a definition for the class.  A
 * typical strategy is to transform the name into a file name and then read a
 * "class file" of that name from a file system.
 *
 * <p> Every {@link Class <b>Class</b>} object contains a {@link
 * Class#getClassLoader() reference} to the <b>ClassLoader</b> that defined
 * it.
 *
 * <p> <b>Class</b> objects for array classes are not created by class
 * loaders, but are created automatically as required by the Java runtime.
 * The class loader for an array class, as returned by {@link
 * Class#getClassLoader()} is the same as the class loader for its element
 * type; if the element type is a primitive type, then the array class has no
 * class loader.
 *
 * <p> Applications implement subclasses of <b>ClassLoader</b> in order to
 * extend the manner in which the Java virtual machine dynamically loads
 * classes.
 *
 * <p> Class loaders may typically be used by security managers to indicate
 * security domains.
 *
 * <p> The <b>ClassLoader</b> class uses a delegation model to search for
 * classes and resources.  Each instance of <b>ClassLoader</b> has an
 * associated parent class loader.  When requested to find a class or
 * resource, a <b>ClassLoader</b> instance will delegate the search for the
 * class or resource to its parent class loader before attempting to find the
 * class or resource itself.  The virtual machine's built-in class loader,
 * called the "bootstrap class loader", does not itself have a parent but may
 * serve as the parent of a <b>ClassLoader</b> instance.
 *
 * <p> Class loaders that support concurrent loading of classes are known as
 * <em>parallel capable</em> class loaders and are required to register
 * themselves at their class initialization time by invoking the
 * {@link
 * #registerAsParallelCapable <b>ClassLoader.registerAsParallelCapable</b>}
 * method. Note that the <b>ClassLoader</b> class is registered as parallel
 * capable by default. However, its subclasses still need to register themselves
 * if they are parallel capable. <br>
 * In environments in which the delegation model is not strictly
 * hierarchical, class loaders need to be parallel capable, otherwise class
 * loading can lead to deadlocks because the loader lock is held for the
 * duration of the class loading process (see {@link #loadClass
 * <b>loadClass</b>} methods).
 *
 * <p> Normally, the Java virtual machine loads classes from the local file
 * system in a platform-dependent manner.  For example, on UNIX systems, the
 * virtual machine loads classes from the directory defined by the
 * <b>CLASSPATH</b> environment variable.
 *
 * <p> However, some classes may not originate from a file; they may originate
 * from other sources, such as the network, or they could be constructed by an
 * application.  The method {@link #defineClass(String, byte[], int, int)
 * <b>defineClass</b>} converts an array of bytes into an instance of class
 * <b>Class</b>. Instances of this newly defined class can be created using
 * {@link Class#newInstance <b>Class.newInstance</b>}.
 *
 * <p> The methods and constructors of objects created by a class loader may
 * reference other classes.  To determine the class(es) referred to, the Java
 * virtual machine invokes the {@link #loadClass <b>loadClass</b>} method of
 * the class loader that originally created the class.
 *
 * <p> For example, an application could create a network class loader to
 * download class files from a server.  Sample code might look like:
 *
 * <blockquote><pre>
 *   ClassLoader loader&nbsp;= new NetworkClassLoader(host,&nbsp;port);
 *   Object main&nbsp;= loader.loadClass("Main", true).newInstance();
 *       &nbsp;.&nbsp;.&nbsp;.
 * </pre></blockquote>
 *
 * <p> The network class loader subclass must define the methods {@link
 * #findClass <b>findClass</b>} and <b>loadClassData</b> to load a class
 * from the network.  Once it has downloaded the bytes that make up the class,
 * it should use the method {@link #defineClass <b>defineClass</b>} to
 * create a class instance.  A sample implementation is:
 *
 * <blockquote><pre>
 *     class NetworkClassLoader extends ClassLoader {
 *         String host;
 *         int port;
 *
 *         public Class findClass(String name) {
 *             byte[] b = loadClassData(name);
 *             return defineClass(name, b, 0, b.length);
 *         }
 *
 *         private byte[] loadClassData(String name) {
 *             // load the class data from the connection
 *             &nbsp;.&nbsp;.&nbsp;.
 *         }
 *     }
 * </pre></blockquote>
 *
 * <h3> <b>Binary names</b> </h3>
 *
 * <p> Any class name provided as a {@link String} parameter to methods in
 * <b>ClassLoader</b> must be a binary name as defined by
 * <cite>The Java&trade; Language Specification</cite>.
 *
 * <p> Examples of valid class names include:
 * <blockquote><pre>
 *   "java.lang.String"
 *   "javax.swing.JSpinner$DefaultEditor"
 *   "java.security.KeyStore$Builder$FileBuilder$1"
 *   "java.net.URLClassLoader$3$1"
 * </pre></blockquote>
 *
 * @see #resolveClass(Class)
 * @since 1.0
 */
public abstract class AbstractClassLoader {

    private static native void registerNatives();

    static {
        registerNatives();
    }

    // The parent class loader for delegation
    // Note: VM hardcoded the offset of this field, thus all new fields
    // must be added *after* it.
    private final AbstractClassLoader parent;

    // Maps class name to the corresponding lock object when the current
    // class loader is parallel capable.
    // Note: VM also uses this field to decide if the current class loader
    // is parallel capable and the appropriate lock object for class loading.
    private final ConcurrentHashMap<String, Object> parallelLockMap;

    // The classes loaded by this class loader. The only purpose of this table
    // is to keep the classes from being GC'ed until the loader is GC'ed.
    private final Vector<Class<?>> classes = new Vector<>();

    // Invoked by the VM to record every loaded class with this loader.
    void addClass(Class<?> c) {
        classes.addElement(c);
    }

    private static Void checkCreateClassLoader() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkCreateClassLoader();
        }
        return null;
    }

    private AbstractClassLoader(Void unused, AbstractClassLoader parent) {
        this.parent = parent;
        if (ParallelLoaders.isRegistered(this.getClass())) {
            parallelLockMap = new ConcurrentHashMap<>();
        }
        else {
            // no finer-grained lock; lock on the classloader instance
            parallelLockMap = null;
        }
    }

    /**
     * Creates a new class loader using the specified parent class loader for
     * delegation.
     *
     * <p> If there is a security manager, its {@link
     * SecurityManager#checkCreateClassLoader()
     * <b>checkCreateClassLoader</b>} method is invoked.  This may result in
     * a security exception.  </p>
     *
     * @param parent The parent class loader
     * @throws SecurityException If a security manager exists and its
     *                           <b>checkCreateClassLoader</b> method doesn't allow creation
     *                           of a new class loader.
     * @since 1.2
     */
    protected AbstractClassLoader(AbstractClassLoader parent) {
        this(checkCreateClassLoader(), parent);
    }

    /**
     * Creates a new class loader using the <b>ClassLoader</b> returned by
     * the method {@link #getSystemClassLoader()
     * <b>getSystemClassLoader()</b>} as the parent class loader.
     *
     * <p> If there is a security manager, its {@link
     * SecurityManager#checkCreateClassLoader()
     * <b>checkCreateClassLoader</b>} method is invoked.  This may result in
     * a security exception.  </p>
     *
     * @throws SecurityException If a security manager exists and its
     *                           <b>checkCreateClassLoader</b> method doesn't allow creation
     *                           of a new class loader.
     */
    protected AbstractClassLoader() {
        this(checkCreateClassLoader(), getSystemClassLoader());
    }

    // region -- Class --

    /**
     * Loads the class with the specified <b>binary name</b>.<br/>
     * This method searches for classes in the same manner as the {@link #loadClass(String, boolean)} method.
     * It is invoked by the Java virtual machine to resolve class references.
     * Invoking this method is equivalent to invoking {@link #loadClass(String, boolean) <b>loadClass(name, false)</b>}.
     *
     * @param name The <b>binary name</b> of the class
     * @return The resulting <b>Class</b> object
     * @throws ClassNotFoundException If the class was not found
     */
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    /**
     * Loads the class with the specified <b>binary name</b>.  The
     * default implementation of this method searches for classes in the
     * following order:
     *
     * <ol>
     *
     *   <li><p> Invoke {@link #findLoadedClass(String)} to check if the class
     *   has already been loaded.  </p></li>
     *
     *   <li><p> Invoke the {@link #loadClass(String) <b>loadClass</b>} method
     *   on the parent class loader.  If the parent is <b>null</b> the class
     *   loader built-in to the virtual machine is used, instead.  </p></li>
     *
     *   <li><p> Invoke the {@link #findClass(String)} method to find the
     *   class.  </p></li>
     *
     * </ol>
     *
     * <p> If the class was found using the above steps, and the
     * <b>resolve</b> flag is true, this method will then invoke the {@link
     * #resolveClass(Class)} method on the resulting <b>Class</b> object.
     *
     * <p> Subclasses of <b>ClassLoader</b> are encouraged to override {@link
     * #findClass(String)}, rather than this method.  </p>
     *
     * <p> Unless overridden, this method synchronizes on the result of
     * {@link #getClassLoadingLock <b>getClassLoadingLock</b>} method
     * during the entire class loading process.
     *
     * @param name    The <b>binary name</b> of the class
     * @param resolve If <b>true</b> then resolve the class
     * @return The resulting <b>Class</b> object
     * @throws ClassNotFoundException If the class could not be found
     */
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                long t0 = System.nanoTime();
                try {
                    if (parent != null) {
                        c = parent.loadClass(name, false);
                    }
                    else {
                        c = findBootstrapClassOrNull(name);
                    }
                } catch (ClassNotFoundException e) {
                    // ClassNotFoundException thrown if class not found
                    // from the non-null parent class loader
                }

                if (c == null) {
                    // If still not found, then invoke findClass in order
                    // to find the class.
                    long t1 = System.nanoTime();
                    c = findClass(name);

                    // this is the defining class loader; record the stats
                    sun.misc.PerfCounter.getParentDelegationTime().addTime(t1 - t0);
                    sun.misc.PerfCounter.getFindClassTime().addElapsedTimeFrom(t1);
                    sun.misc.PerfCounter.getFindClasses().increment();
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    /**
     * Returns the lock object for class loading operations.
     * For backward compatibility, the default implementation of this method
     * behaves as follows. If this ClassLoader object is registered as
     * parallel capable, the method returns a dedicated object associated
     * with the specified class name. Otherwise, the method returns this
     * ClassLoader object.
     *
     * @param className The name of the to-be-loaded class
     * @return the lock for class loading operations
     * @throws NullPointerException If registered as parallel capable and <b>className</b> is null
     * @see #loadClass(String, boolean)
     * @since 1.7
     */
    protected Object getClassLoadingLock(String className) {
        Object lock = this;
        if (parallelLockMap != null) {
            Object newLock = new Object();
            lock = parallelLockMap.putIfAbsent(className, newLock);
            if (lock == null) {
                lock = newLock;
            }
        }
        return lock;
    }

    /**
     * Finds the class with the specified <b>binary name</b>.
     * This method should be overridden by class loader implementations that
     * follow the delegation model for loading classes, and will be invoked by
     * the {@link #loadClass <b>loadClass</b>} method after checking the
     * parent class loader for the requested class.  The default implementation
     * throws a <b>ClassNotFoundException</b>.
     *
     * @param name The <b>binary name</b> of the class
     * @return The resulting <b>Class</b> object
     * @throws ClassNotFoundException If the class could not be found
     * @since 1.2
     */
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }

    /**
     * Converts an array of bytes into an instance of class <b>Class</b>.
     * Before the <b>Class</b> can be used it must be resolved.  This method
     * is deprecated in favor of the version that takes a <b>binary name</b> as its first argument, and is more secure.
     *
     * @param bytes The bytes that make up the class data.  The bytes in positions
     *              <b>off</b> through <b>off+len-1</b> should have the format
     *              of a valid class file as defined by
     *              <cite>The Java&trade; Virtual Machine Specification</cite>.
     * @param off   The start offset in <b>b</b> of the class data
     * @param len   The length of the class data
     * @return The <b>Class</b> object that was created from the specified class data
     * @throws ClassFormatError          If the data did not contain a valid class
     * @throws IndexOutOfBoundsException If either <b>off</b> or <b>len</b> is negative, or if <b>off+len</b> is greater than <b>b.length</b>.
     * @throws SecurityException         If an attempt is made to add this class to a package that
     *                                   contains classes that were signed by a different set of
     *                                   certificates than this class, or if an attempt is made
     *                                   to define a class in a package with a fully-qualified name
     *                                   that starts with "{@code java.}".
     * @see #loadClass(String, boolean)
     * @see #resolveClass(Class)
     * @deprecated Replaced by {@link #defineClass(String, byte[], int, int) defineClass(String, byte[], int, int)}
     */
    @Deprecated
    protected final Class<?> defineClass(byte[] bytes, int off, int len) throws ClassFormatError {
        return defineClass(null, bytes, off, len, null);
    }

    /**
     * Converts an array of bytes into an instance of class <b>Class</b>.
     * Before the <b>Class</b> can be used it must be resolved.
     *
     * <p> This method assigns a default {@link java.security.ProtectionDomain
     * <b>ProtectionDomain</b>} to the newly defined class.  The
     * <b>ProtectionDomain</b> is effectively granted the same set of
     * permissions returned when {@link
     * java.security.Policy#getPermissions(java.security.CodeSource)
     * <b>Policy.getPolicy().getPermissions(new CodeSource(null, null))</b>}
     * is invoked.  The default domain is created on the first invocation of
     * {@link #defineClass(String, byte[], int, int) <b>defineClass</b>},
     * and re-used on subsequent invocations.
     *
     * <p> To assign a specific <b>ProtectionDomain</b> to the class, use
     * the {@link #defineClass(String, byte[], int, int,
     * java.security.ProtectionDomain) <b>defineClass</b>} method that takes a
     * <b>ProtectionDomain</b> as one of its arguments.  </p>
     *
     * @param name  The expected <b>binary name</b> of the class, or
     *              <b>null</b> if not known
     * @param bytes The bytes that make up the class data.  The bytes in positions
     *              <b>off</b> through <b>off+len-1</b> should have the format
     *              of a valid class file as defined by
     *              <cite>The Java&trade; Virtual Machine Specification</cite>.
     * @param off   The start offset in <b>b</b> of the class data
     * @param len   The length of the class data
     * @return The <b>Class</b> object that was created from the specified
     * class data.
     * @throws ClassFormatError          If the data did not contain a valid class
     * @throws IndexOutOfBoundsException If either <b>off</b> or <b>len</b> is negative, or if
     *                                   <b>off+len</b> is greater than <b>b.length</b>.
     * @throws SecurityException         If an attempt is made to add this class to a package that
     *                                   contains classes that were signed by a different set of
     *                                   certificates than this class (which is unsigned), or if
     *                                   <b>name</b> begins with "<b>java.</b>".
     * @see #loadClass(String, boolean)
     * @see #resolveClass(Class)
     * @see java.security.CodeSource
     * @see java.security.SecureClassLoader
     * @since 1.1
     */
    protected final Class<?> defineClass(String name, byte[] bytes, int off, int len) throws ClassFormatError {
        return defineClass(name, bytes, off, len, null);
    }

    private String defineClassSourceLocation(ProtectionDomain pd) {
        CodeSource cs = pd.getCodeSource();
        String source = null;
        if (cs != null && cs.getLocation() != null) {
            source = cs.getLocation().toString();
        }
        return source;
    }

    /**
     * Converts an array of bytes into an instance of class <b>Class</b>,
     * with an optional <b>ProtectionDomain</b>.  If the domain is
     * <b>null</b>, then a default domain will be assigned to the class as
     * specified in the documentation for {@link #defineClass(String, byte[],
     * int, int)}.  Before the class can be used it must be resolved.
     *
     * <p> The first class defined in a package determines the exact set of
     * certificates that all subsequent classes defined in that package must
     * contain.  The set of certificates for a class is obtained from the
     * {@link java.security.CodeSource <b>CodeSource</b>} within the
     * <b>ProtectionDomain</b> of the class.  Any classes added to that
     * package must contain the same set of certificates or a
     * <b>SecurityException</b> will be thrown.  Note that if
     * <b>name</b> is <b>null</b>, this check is not performed.
     * You should always pass in the <b>binary name</b> of the
     * class you are defining as well as the bytes.  This ensures that the
     * class you are defining is indeed the class you think it is.
     *
     * <p> The specified <b>name</b> cannot begin with "<b>java.</b>", since
     * all classes in the "<b>java.*</b> packages can only be defined by the
     * bootstrap class loader.  If <b>name</b> is not <b>null</b>, it
     * must be equal to the <b>binary name</b> of the class
     * specified by the byte array "<b>b</b>", otherwise a {@link
     * NoClassDefFoundError <b>NoClassDefFoundError</b>} will be thrown. </p>
     *
     * @param name             The expected <b>binary name</b> of the class, or
     *                         <b>null</b> if not known
     * @param bytes            The bytes that make up the class data. The bytes in positions
     *                         <b>off</b> through <b>off+len-1</b> should have the format
     *                         of a valid class file as defined by
     *                         <cite>The Java&trade; Virtual Machine Specification</cite>.
     * @param off              The start offset in <b>b</b> of the class data
     * @param len              The length of the class data
     * @param protectionDomain The ProtectionDomain of the class
     * @return The <b>Class</b> object created from the data,
     * and optional <b>ProtectionDomain</b>.
     * @throws ClassFormatError          If the data did not contain a valid class
     * @throws NoClassDefFoundError      If <b>name</b> is not equal to the <b>binary
     *                                   name</b> of the class specified by <b>b</b>
     * @throws IndexOutOfBoundsException If either <b>off</b> or <b>len</b> is negative, or if
     *                                   <b>off+len</b> is greater than <b>b.length</b>.
     * @throws SecurityException         If an attempt is made to add this class to a package that
     *                                   contains classes that were signed by a different set of
     *                                   certificates than this class, or if <b>name</b> begins with
     *                                   "<b>java.</b>".
     */
    protected final Class<?> defineClass(String name, byte[] bytes, int off, int len, ProtectionDomain protectionDomain) throws ClassFormatError {
        protectionDomain = null;
        String source = defineClassSourceLocation(protectionDomain);
        Class<?> c = defineClass1(name, bytes, off, len, protectionDomain, source);
        return c;
    }

    /**
     * Converts a {@link java.nio.ByteBuffer <b>ByteBuffer</b>}
     * into an instance of class <b>Class</b>,
     * with an optional <b>ProtectionDomain</b>.  If the domain is
     * <b>null</b>, then a default domain will be assigned to the class as
     * specified in the documentation for {@link #defineClass(String, byte[],
     * int, int)}.  Before the class can be used it must be resolved.
     *
     * <p>The rules about the first class defined in a package determining the
     * set of certificates for the package, and the restrictions on class names
     * are identical to those specified in the documentation for {@link
     * #defineClass(String, byte[], int, int, ProtectionDomain)}.
     *
     * <p> An invocation of this method of the form
     * <i>cl</i><b>.defineClass(</b><i>name</i><b>,</b>
     * <i>bBuffer</i><b>,</b> <i>pd</i><b>)</b> yields exactly the same
     * result as the statements
     *
     * <p> <b>
     * ...<br>
     * byte[] temp = new byte[bBuffer.{@link
     * java.nio.ByteBuffer#remaining remaining}()];<br>
     * bBuffer.{@link java.nio.ByteBuffer#get(byte[])
     * get}(temp);<br>
     * return {@link #defineClass(String, byte[], int, int, ProtectionDomain)
     * cl.defineClass}(name, temp, 0,
     * temp.length, pd);<br>
     * </b></p>
     *
     * @param name             The expected <b>binary name</b>. of the class, or
     *                         <b>null</b> if not known
     * @param b                The bytes that make up the class data. The bytes from positions
     *                         <b>b.position()</b> through <b>b.position() + b.limit() -1
     *                         </b> should have the format of a valid class file as defined by
     *                         <cite>The Java&trade; Virtual Machine Specification</cite>.
     * @param protectionDomain The ProtectionDomain of the class, or <b>null</b>.
     * @return The <b>Class</b> object created from the data,
     * and optional <b>ProtectionDomain</b>.
     * @throws ClassFormatError     If the data did not contain a valid class.
     * @throws NoClassDefFoundError If <b>name</b> is not equal to the <b>binary
     *                              name</b> of the class specified by <b>b</b>
     * @throws SecurityException    If an attempt is made to add this class to a package that
     *                              contains classes that were signed by a different set of
     *                              certificates than this class, or if <b>name</b> begins with
     *                              "<b>java.</b>".
     * @see #defineClass(String, byte[], int, int, ProtectionDomain)
     * @since 1.5
     */
    protected final Class<?> defineClass(String name, java.nio.ByteBuffer b, ProtectionDomain protectionDomain) throws ClassFormatError {
        int len = b.remaining();

        // Use byte[] if not a direct ByteBufer:
        if (!b.isDirect()) {
            if (b.hasArray()) {
                return defineClass(name, b.array(),
                        b.position() + b.arrayOffset(), len,
                        protectionDomain);
            }
            else {
                // no array, or read-only array
                byte[] tb = new byte[len];
                b.get(tb);  // get bytes out of byte buffer.
                return defineClass(name, tb, 0, len, protectionDomain);
            }
        }

        protectionDomain = null;
        String source = defineClassSourceLocation(protectionDomain);
        Class<?> c = defineClass2(name, b, b.position(), len, protectionDomain, source);
        return c;
    }

    private native Class<?> defineClass0(String name, byte[] b, int off, int len, ProtectionDomain pd);

    private native Class<?> defineClass1(String name, byte[] b, int off, int len, ProtectionDomain pd, String source);

    private native Class<?> defineClass2(String name, java.nio.ByteBuffer b,
                                         int off, int len, ProtectionDomain pd,
                                         String source);

    // true if the name is null or has the potential to be a valid binary name
    private boolean checkName(String name) {
        if ((name == null) || (name.length() == 0))
            return true;
        if ((name.indexOf('/') != -1) || ((name.charAt(0) == '[')))
            return false;
        return true;
    }

    /**
     * Links the specified class.  This (misleadingly named) method may be
     * used by a class loader to link a class.  If the class <b>c</b> has
     * already been linked, then this method simply returns. Otherwise, the
     * class is linked as described in the "Execution" chapter of
     * <cite>The Java&trade; Language Specification</cite>.
     *
     * @param c The class to link
     * @throws NullPointerException If <b>c</b> is <b>null</b>.
     * @see #defineClass(String, byte[], int, int)
     */
    protected final void resolveClass(Class<?> c) {
        resolveClass0(c);
    }

    private native void resolveClass0(Class<?> c);

    /**
     * Finds a class with the specified <b>binary name</b>,
     * loading it if necessary.
     *
     * <p> This method loads the class through the system class loader (see
     * {@link #getSystemClassLoader()}).  The <b>Class</b> object returned
     * might have more than one <b>ClassLoader</b> associated with it.
     * Subclasses of <b>ClassLoader</b> need not usually invoke this method,
     * because most class loaders need to override just {@link
     * #findClass(String)}.  </p>
     *
     * @param name The <b>binary name</b> of the class
     * @return The <b>Class</b> object for the specified <b>name</b>
     * @throws ClassNotFoundException If the class could not be found
     * @see #AbstractClassLoader(AbstractClassLoader)
     * @see #getParent()
     */
    protected final Class<?> findSystemClass(String name) throws ClassNotFoundException {
        AbstractClassLoader system = getSystemClassLoader();
        if (system == null) {
            Class<?> cls = findBootstrapClass(name);
            if (cls == null) {
                throw new ClassNotFoundException(name);
            }
            return cls;
        }
        return system.loadClass(name);
    }

    /**
     * Returns a class loaded by the bootstrap class loader;
     * or return null if not found.
     */
    private Class<?> findBootstrapClassOrNull(String name) {
        if (!checkName(name)) return null;

        return findBootstrapClass(name);
    }

    // return null if not found
    private native Class<?> findBootstrapClass(String name);

    /**
     * Returns the class with the given <b>binary name</b> if this
     * loader has been recorded by the Java virtual machine as an initiating
     * loader of a class with that <b>binary name</b>.  Otherwise
     * <b>null</b> is returned.
     *
     * @param name The <b>binary name</b> of the class
     * @return The <b>Class</b> object, or <b>null</b> if the class has not been loaded
     * @since 1.1
     */
    protected final Class<?> findLoadedClass(String name) {
        if (!checkName(name))
            return null;
        return findLoadedClass0(name);
    }

    private native final Class<?> findLoadedClass0(String name);

    // endregion

    // region -- Resource --

    /**
     * Finds the resource with the given name.  A resource is some data
     * (images, audio, text, etc) that can be accessed by class code in a way
     * that is independent of the location of the code.
     *
     * <p> The name of a resource is a '<b>/</b>'-separated path name that
     * identifies the resource.
     *
     * <p> This method will first search the parent class loader for the
     * resource; if the parent is <b>null</b> the path of the class loader
     * built-in to the virtual machine is searched.  That failing, this method
     * will invoke {@link #findResource(String)} to find the resource.  </p>
     *
     * @param name The resource name
     * @return A <b>URL</b> object for reading the resource, or
     * <b>null</b> if the resource could not be found or the invoker
     * doesn't have adequate  privileges to get the resource.
     * @apiNote When overriding this method it is recommended that an
     * implementation ensures that any delegation is consistent with the {@link
     * #getResources(java.lang.String) getResources(String)} method.
     * @since 1.1
     */
    public URL getResource(String name) {
        URL url;
        if (parent != null) {
            url = parent.getResource(name);
        }
        else {
            url = getBootstrapResource(name);
        }
        if (url == null) {
            url = findResource(name);
        }
        return url;
    }

    /**
     * Finds all the resources with the given name. A resource is some data
     * (images, audio, text, etc) that can be accessed by class code in a way
     * that is independent of the location of the code.
     *
     * <p>The name of a resource is a <b>/</b>-separated path name that
     * identifies the resource.
     *
     * <p> The search order is described in the documentation for {@link
     * #getResource(String)}.  </p>
     *
     * @param name The resource name
     * @return An enumeration of {@link java.net.URL <b>URL</b>} objects for
     * the resource.  If no resources could  be found, the enumeration
     * will be empty.  Resources that the class loader doesn't have
     * access to will not be in the enumeration.
     * @throws IOException If I/O errors occur
     * @apiNote When overriding this method it is recommended that an
     * implementation ensures that any delegation is consistent with the {@link
     * #getResource(java.lang.String) getResource(String)} method. This should
     * ensure that the first element returned by the Enumeration's
     * {@code nextElement} method is the same resource that the
     * {@code getResource(String)} method would return.
     * @see #findResources(String)
     * @since 1.2
     */
    public Enumeration<URL> getResources(String name) throws IOException {
        @SuppressWarnings("unchecked")
        Enumeration<URL>[] tmp = (Enumeration<URL>[]) new Enumeration<?>[2];
        if (parent != null) {
            tmp[0] = parent.getResources(name);
        }
        else {
            tmp[0] = getBootstrapResources(name);
        }
        tmp[1] = findResources(name);

        return new CompoundEnumeration<>(tmp);
    }

    /**
     * Finds the resource with the given name. Class loader implementations
     * should override this method to specify where to find resources.
     *
     * @param name The resource name
     * @return A <b>URL</b> object for reading the resource, or
     * <b>null</b> if the resource could not be found
     * @since 1.2
     */
    protected URL findResource(String name) {
        return null;
    }

    /**
     * Returns an enumeration of {@link java.net.URL <b>URL</b>} objects
     * representing all the resources with the given name. Class loader
     * implementations should override this method to specify where to load
     * resources from.
     *
     * @param name The resource name
     * @return An enumeration of {@link java.net.URL <b>URL</b>} objects for the resources
     * @throws IOException If I/O errors occur
     * @since 1.2
     */
    protected Enumeration<URL> findResources(String name) throws IOException {
        return java.util.Collections.emptyEnumeration();
    }

    /**
     * Registers the caller as parallel capable.
     * The registration succeeds if and only if all of the following
     * conditions are met:
     * <ol>
     * <li> no instance of the caller has been created</li>
     * <li> all of the super classes (except class Object) of the caller are
     * registered as parallel capable</li>
     * </ol>
     * <p>Note that once a class loader is registered as parallel capable, there
     * is no way to change it back.</p>
     *
     * @return true if the caller is successfully registered as
     * parallel capable and false if otherwise.
     * @since 1.7
     */
    @CallerSensitive
    protected static boolean registerAsParallelCapable() {
        Class<? extends AbstractClassLoader> callerClass =
                Reflection.getCallerClass().asSubclass(AbstractClassLoader.class);
        return ParallelLoaders.register(callerClass);
    }

    /**
     * Find a resource of the specified name from the search path used to load classes.<br/>
     * This method locates the resource through the system class loader (see {@link #getSystemClassLoader()}).
     *
     * @param name The resource name
     * @return A {@link java.net.URL <b>URL</b>} object for reading the resource,
     *         or <b>null</b> if the resource could not be found
     * @since 1.1
     */
    public static URL getSystemResource(String name) {
        AbstractClassLoader system = getSystemClassLoader();
        if (system == null) {
            return getBootstrapResource(name);
        }
        return system.getResource(name);
    }

    /**
     * Finds all resources of the specified name from the search path used to load classes.
     * The resources thus found are returned as an {@link java.util.Enumeration <b>Enumeration</b>} of
     * {@link java.net.URL <b>URL</b>} objects.
     *
     * <p> The search order is described in the documentation for {@link #getSystemResource(String)}.</p>
     *
     * @param name The resource name
     * @return An enumeration of resource {@link java.net.URL <b>URL</b>}
     * objects
     * @throws IOException If I/O errors occur
     * @since 1.2
     */
    public static Enumeration<URL> getSystemResources(String name)
            throws IOException {
        AbstractClassLoader system = getSystemClassLoader();
        if (system == null) {
            return getBootstrapResources(name);
        }
        return system.getResources(name);
    }

    /**
     * Find resources from the VM's built-in classloader.
     */
    private static URL getBootstrapResource(String name) {
        URLClassPath ucp = getBootstrapClassPath();
        Resource res = ucp.getResource(name);
        return res != null ? res.getURL() : null;
    }

    /**
     * Find resources from the VM's built-in classloader.
     */
    private static Enumeration<URL> getBootstrapResources(String name) throws IOException {
        final Enumeration<Resource> e = getBootstrapClassPath().getResources(name);
        return new Enumeration<URL>() {
            public URL nextElement() {
                return e.nextElement().getURL();
            }

            public boolean hasMoreElements() {
                return e.hasMoreElements();
            }
        };
    }

    // Returns the URLClassPath that is used for finding system resources.
    static URLClassPath getBootstrapClassPath() {
        return Launcher.getBootstrapClassPath();
    }


    /**
     * Returns an input stream for reading the specified resource.
     *
     * <p> The search order is described in the documentation for {@link #getResource(String)}. </p>
     *
     * @param name The resource name
     * @return An input stream for reading the resource, or <b>null</b> if the resource could not be found
     * @since 1.1
     */
    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Open for reading, a resource of the specified name from the search path
     * used to load classes.  This method locates the resource through the
     * system class loader (see {@link #getSystemClassLoader()}).
     *
     * @param name The resource name
     * @return An input stream for reading the resource, or <b>null</b> if the resource could not be found
     * @since 1.1
     */
    public static InputStream getSystemResourceAsStream(String name) {
        URL url = getSystemResource(name);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException e) {
            return null;
        }
    }

    // endregion

    // region -- Hierarchy --

    /**
     * Returns the parent class loader for delegation. Some implementations may
     * use <b>null</b> to represent the bootstrap class loader. This method
     * will return <b>null</b> in such implementations if this class loader's
     * parent is the bootstrap class loader.
     *
     * <p> If a security manager is present, and the invoker's class loader is
     * not <b>null</b> and is not an ancestor of this class loader, then this
     * method invokes the security manager's {@link
     * SecurityManager#checkPermission(java.security.Permission)
     * <b>checkPermission</b>} method with a {@link
     * RuntimePermission#RuntimePermission(String)
     * <b>RuntimePermission("getClassLoader")</b>} permission to verify
     * access to the parent class loader is permitted.  If not, a
     * <b>SecurityException</b> will be thrown.  </p>
     *
     * @return The parent <b>ClassLoader</b>
     * @throws SecurityException If a security manager exists and its <b>checkPermission</b>
     *                           method doesn't allow access to this class loader's parent class
     *                           loader.
     * @since 1.2
     */
    @CallerSensitive
    public final AbstractClassLoader getParent() {
        if (parent == null)
            return null;
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkClassLoaderPermission(this, Reflection.getCallerClass());
        }
        return parent;
    }

    // Returns true if the specified class loader can be found in this class
    // loader's delegation chain.
    boolean isAncestor(AbstractClassLoader cl) {
        AbstractClassLoader acl = this;
        do {
            acl = acl.parent;
            if (cl == acl) {
                return true;
            }
        } while (acl != null);
        return false;
    }

    // endregion

    // Returns the class's class loader, or null if none.
    static java.lang.ClassLoader getClassLoader(Class<?> caller) {
        // This can be null if the VM is requesting it
        if (caller == null) {
            return null;
        }
        // Circumvent security check since this is package-private
        return caller.getClassLoader();
    }

    static void checkClassLoaderPermission(AbstractClassLoader cl, Class<?> caller) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            // caller can be null if the VM is requesting it
            java.lang.ClassLoader ccl = getClassLoader(caller);
        }
    }

    // region -- System ClassLoader --
    // The class loader for the system
    // @GuardedBy("ClassLoader.class")
    private static AbstractClassLoader systemClassLoader;

    // Set to true once the system class loader has been set
    // @GuardedBy("ClassLoader.class")
    private static boolean sclSet;


    /**
     * Returns the system class loader for delegation.  This is the default
     * delegation parent for new <b>ClassLoader</b> instances, and is
     * typically the class loader used to start the application.
     *
     * <p> This method is first invoked early in the runtime's startup
     * sequence, at which point it creates the system class loader and sets it
     * as the context class loader of the invoking <b>Thread</b>.
     *
     * <p> The default system class loader is an implementation-dependent
     * instance of this class.
     *
     * <p> If the system property "<b>java.system.class.loader</b>" is defined
     * when this method is first invoked then the value of that property is
     * taken to be the name of a class that will be returned as the system
     * class loader.  The class is loaded using the default system class loader
     * and must define a public constructor that takes a single parameter of
     * type <b>ClassLoader</b> which is used as the delegation parent.  An
     * instance is then created using this constructor with the default system
     * class loader as the parameter.  The resulting class loader is defined
     * to be the system class loader.
     *
     * <p> If a security manager is present, and the invoker's class loader is
     * not <b>null</b> and the invoker's class loader is not the same as or
     * an ancestor of the system class loader, then this method invokes the
     * security manager's {@link
     * SecurityManager#checkPermission(java.security.Permission)
     * <b>checkPermission</b>} method with a {@link
     * RuntimePermission#RuntimePermission(String)
     * <b>RuntimePermission("getClassLoader")</b>} permission to verify
     * access to the system class loader.  If not, a
     * <b>SecurityException</b> will be thrown.  </p>
     *
     * @return The system <b>ClassLoader</b> for delegation, or
     * <b>null</b> if none
     * @throws SecurityException     If a security manager exists and its <b>checkPermission</b>
     *                               method doesn't allow access to the system class loader.
     * @throws IllegalStateException If invoked recursively during the construction of the class
     *                               loader specified by the "<b>java.system.class.loader</b>"
     *                               property.
     * @throws Error                 If the system property "<b>java.system.class.loader</b>"
     *                               is defined but the named class could not be loaded, the
     *                               provider class does not define the required constructor, or an
     *                               exception is thrown by that constructor when it is invoked. The
     *                               underlying cause of the error can be retrieved via the
     *                               {@link Throwable#getCause()} method.
     * @revised 1.4
     */
    @CallerSensitive
    public static AbstractClassLoader getSystemClassLoader() {
        initSystemClassLoader();
        if (systemClassLoader == null) {
            return null;
        }
        return systemClassLoader;
    }

    private static synchronized void initSystemClassLoader() {
        if (!sclSet) {
            if (systemClassLoader != null)
                throw new IllegalStateException("recursive invocation");
            Launcher launcher = Launcher.getLauncher();
            if (launcher != null) {
                systemClassLoader = launcher.getClassLoader();

            }
            sclSet = true;
        }
    }
    // endregion
}


