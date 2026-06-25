package project;
/**
 * SemanticException.java
 * Thrown by SemanticAnalyzer when a semantic rule is violated.
 */
public class SemanticException extends Exception {
    public SemanticException(String message) {
        super(message);
    }
}
