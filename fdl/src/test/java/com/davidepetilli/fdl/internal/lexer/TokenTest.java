package com.davidepetilli.fdl.internal.lexer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class TokenTest {

    @Test
    void canConstructWithTypeLexemeLiteralLine() {
        final Object LITERAL = "literal";

        var token = new Token(TokenType.ELEMENT, "lexeme", LITERAL, 0);
        assertThat(token.type()).isEqualTo(TokenType.ELEMENT);
        assertThat(token.lexeme()).isEqualTo("lexeme");
        assertThat(token.literal()).isSameAs(LITERAL);
        assertThat(token.line()).isEqualTo(0);
    }
}