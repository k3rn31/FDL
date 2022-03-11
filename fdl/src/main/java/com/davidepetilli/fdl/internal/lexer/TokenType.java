package com.davidepetilli.fdl.internal.lexer;

public enum TokenType {
    // Single-character tokens.
    DOT, EQUAL, LEFT_BRACKET, LEFT_PAREN,
    RIGHT_BRACKET, RIGHT_PAREN, SEMICOLON,

    // Two-character tokens
    FAT_ARROW,

    // Literals.
    DECIMAL, ELEMENT, IDENTIFIER, INTEGER, NUMBER, STRING,

    // Keywords
    AS, BOOLEAN, DATE,

    EOF
}
