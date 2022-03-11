package com.davidepetilli.fdl.internal.error;

import com.davidepetilli.fdl.internal.lexer.Token;
import com.davidepetilli.fdl.internal.lexer.TokenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ErrorServiceTest {

    @Test
    void hasStaticErrorList() {
        var errorService = new ErrorService();
        errorService.staticError(0, "Static error message 1.");
        errorService.staticError(0, "Static error message 2.");

        // Should not be matched.
        errorService.runtimeError(0, "Runtime error message 2.");

        var staticErrors = errorService.staticErrors();
        assertThat(staticErrors.size()).isEqualTo(2);
    }

    @Test
    void hasRuntimeErrorList() {
        var errorService = new ErrorService();
        errorService.runtimeError(0, "Runtime error message 1.");
        errorService.runtimeError(0, "Runtime error message 2.");

        // Should not be matched.
        errorService.staticError(0, "Static error message 2.");

        var runtimeErrors = errorService.runtimeErrors();
        assertThat(runtimeErrors.size()).isEqualTo(2);
    }

    @Test
    void errorMethodsCanGetToken() {
        var errorService = new ErrorService();
        errorService.staticError(new Token(TokenType.EOF, "", "", 1), "Static error message 1.");
        errorService.runtimeError(new Token(TokenType.EOF, "", "", 1), "Static error message 2.");

        var staticErrors = errorService.staticErrors();
        var runtimeErrors = errorService.runtimeErrors();
        assertThat(staticErrors.size()).isEqualTo(1);
        assertThat(runtimeErrors.size()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"static", "runtime"})
    void hasErrorIsTrueForBothRuntimeAndStaticErrors(String errorType) {
        var errorService = new ErrorService();

        assertThat(errorService.hasError()).isFalse();
        if ("static".equals(errorType)) {
            errorService.staticError(0, "Runtime error message 1.");
            assertThat(errorService.hasError()).isTrue();
        } else if ("runtime".equals(errorType)) {
            errorService.runtimeError(0, "Runtime error message 1.");
            assertThat(errorService.hasError()).isTrue();
        }
    }
}