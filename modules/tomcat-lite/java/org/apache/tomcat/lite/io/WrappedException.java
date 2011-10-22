/*
 */
package org.apache.tomcat.lite.io;

import java.io.IOException;

/**
 * For specific exceptions - also has cause ( good if compiling against
 * JDK1.5 )
 *
 * @author Costin Manolache
 */
public class WrappedException extends IOException {

    public WrappedException() {
        super();
    }

    public WrappedException(String message) {
        super(message);
    }

    public WrappedException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }

    public WrappedException(Throwable cause) {
        super("");
        initCause(cause);
    }


    public static class ClientAbortException extends WrappedException {
        public ClientAbortException(Throwable throwable) {
            super(null, throwable);
        }
    }

}
