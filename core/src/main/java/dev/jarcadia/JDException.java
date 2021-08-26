package dev.jarcadia;

public class JDException extends Exception {

    protected JDException(String message) {
        super(message);
    }

    protected JDException(String message, Throwable cause) {
        super(message, cause);
    }

    protected JDException(Throwable cause) {
        super(cause);
    }
}
