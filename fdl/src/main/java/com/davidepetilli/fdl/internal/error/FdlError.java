package com.davidepetilli.fdl.internal.error;

import com.davidepetilli.fdl.internal.lexer.Token;
import com.davidepetilli.fdl.internal.lexer.TokenType;

/**
 * This is a container for both static and runtime errors. When a new {@code FdlError} is instantiated, it generates
 * a string report which is a pretty print of the error log.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class FdlError {
    private final int line;
    private final Token token;
    private final String errorReport;

    public FdlError(int line, String errorMessage) {
        this.line = line;
        this.token = null;
        this.errorReport = generateReport(errorMessage);
    }

    public FdlError(Token token, String errorMessage) {
        this.token = token;
        this.line = token.line();
        this.errorReport = generateReport(errorMessage);
    }

    private String generateReport(String errorMessage) {
        if (token != null && token.type() == TokenType.EOF) {
            return String.format("[line %s] Error %s: %s", line, "at end", errorMessage);
        } else if (token != null) {
            return String.format("[line %s] Error %s: %s", line, "at '" + token.lexeme() + "'", errorMessage);
        }
        return String.format("[line %s] Error: %s", line, errorMessage);
    }

    public String getErrorReport() {
        return errorReport;
    }
}
