package lsieun.misc;

/**
 * Thrown if the URLClassLoader finds the INDEX.LIST file of
 * a jar file contains incorrect information.
 *
 * @author   Zhenghua Li
 * @since   1.3
 */

public class InvalidJarIndexException extends RuntimeException {

    static final long serialVersionUID = -6159797516569680148L;

    /**
     * Constructs an <code>InvalidJarIndexException</code> with no
     * detail message.
     */
    public InvalidJarIndexException() {
        super();
    }

    /**
     * Constructs an <code>InvalidJarIndexException</code> with the
     * specified detail message.
     *
     * @param   s   the detail message.
     */
    public InvalidJarIndexException(String s) {
        super(s);
    }
}