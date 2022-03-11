package com.davidepetilli.fdl.internal.error;

import com.davidepetilli.fdl.internal.lexer.Token;

/**
 * A runtime error is raised during the interpretation steps od FDL. Usually this is a "stop the world" error,
 * since when it is raised the processing stops and the error is reported.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class RuntimeError extends RuntimeException {
    private final Token token;

    public RuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }

    public Token getToken() {
        return token;
    }
}
