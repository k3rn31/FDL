package com.davidepetilli.fdl.internal.error;

import com.davidepetilli.fdl.internal.lexer.Token;
import com.davidepetilli.fdl.internal.lexer.TokenType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class FdlErrorTest {

    @Test
    void hasLineAndMessage() {
        var error = new FdlError(1, "error message");

        assertThat(error.getErrorReport()).isEqualTo("[line 1] Error: error message");
    }

    @Test
    void hasErrorLocationLexeme() {
        var error = new FdlError(new Token(TokenType.ELEMENT, "Patient", "Patient", 10),
                "error message");

        assertThat(error.getErrorReport()).isEqualTo("[line 10] Error at 'Patient': error message");
    }

    @Test
    void hasErrorLocationEOF() {
        var error = new FdlError(new Token(TokenType.EOF, "Patient", "Patient", 10),
                "error message");

        assertThat(error.getErrorReport()).isEqualTo("[line 10] Error at end: error message");
    }
}