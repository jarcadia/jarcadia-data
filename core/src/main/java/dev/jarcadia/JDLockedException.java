package dev.jarcadia;

public class JDLockedException extends Exception {

    protected JDLockedException(String message) {
        super(message);
    }

    protected JDLockedException(String message, Throwable cause) {
        super(message, cause);
    }

    protected JDLockedException(Throwable cause) {
        super(cause);
    }
}
