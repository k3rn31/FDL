package com.davidepetilli.fdl.internal.lexer;

/**
 * Represent a token supported by FDL.
 * It keeps track of the type of the token, the lexeme as found in the source, the literal value and the line where
 * the token has been found.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public record Token(
        TokenType type,
        String lexeme,
        Object literal,
        int line) {
}
