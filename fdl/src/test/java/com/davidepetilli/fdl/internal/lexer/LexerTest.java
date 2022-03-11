package com.davidepetilli.fdl.internal.lexer;

import com.davidepetilli.fdl.internal.error.ErrorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.davidepetilli.fdl.internal.lexer.TokenType.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LexerTest {

    @Mock
    ErrorService errorService;

    @Test
    void canIdentifyEOF() {
        var source = "";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(1);
        assertThat(tokens.get(0).type()).isEqualTo(EOF);
    }

    @Test
    void canScanNumbers() {
        var source = "1234567890";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(NUMBER);
        assertThat(tokens.get(0).lexeme()).isEqualTo("1234567890");
        assertThat(tokens.get(0).literal()).isEqualTo(1234567890);
    }

    // Internal representation is always integer since it is used for indexes.
    // Assigned values are always strings.
    @Test
    void ignoresFractionalPartInNumbers() {
        var source = "123.456";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(4);
        assertThat(tokens.get(0).type()).isEqualTo(NUMBER);
        assertThat(tokens.get(0).lexeme()).isEqualTo("123");
        assertThat(tokens.get(0).literal()).isEqualTo(123);
    }

    @Test
    void canScanEqual() {
        var source = "=";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(EQUAL);
        assertThat(tokens.get(0).lexeme()).isEqualTo("=");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanSemicolon() {
        var source = ";";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(SEMICOLON);
        assertThat(tokens.get(0).lexeme()).isEqualTo(";");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanDot() {
        var source = ".";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(DOT);
        assertThat(tokens.get(0).lexeme()).isEqualTo(".");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanLeftParen() {
        var source = "(";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(LEFT_PAREN);
        assertThat(tokens.get(0).lexeme()).isEqualTo("(");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanRightParen() {
        var source = ")";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(RIGHT_PAREN);
        assertThat(tokens.get(0).lexeme()).isEqualTo(")");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanLeftSquareBracket() {
        var source = "[";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(LEFT_BRACKET);
        assertThat(tokens.get(0).lexeme()).isEqualTo("[");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanRightSquareBracket() {
        var source = "]";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(RIGHT_BRACKET);
        assertThat(tokens.get(0).lexeme()).isEqualTo("]");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canSkipCommentLines() {
        var source = "//";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(1);
        assertThat(tokens.get(0).type()).isEqualTo(EOF);
    }

    @Test
    void canScanAsKeyword() {
        var source = "as";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(AS);
        assertThat(tokens.get(0).lexeme()).isEqualTo("as");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanBooleanKeyword() {
        var source = "boolean";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(BOOLEAN);
        assertThat(tokens.get(0).lexeme()).isEqualTo("boolean");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanIntegerKeyword() {
        var source = "integer";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(INTEGER);
        assertThat(tokens.get(0).lexeme()).isEqualTo("integer");
        assertThat(tokens.get(0).literal()).isNull();
    }


    @Test
    void canScanDoubleKeyword() {
        var source = "decimal";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(DECIMAL);
        assertThat(tokens.get(0).lexeme()).isEqualTo("decimal");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanDateKeyword() {
        var source = "date";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(DATE);
        assertThat(tokens.get(0).lexeme()).isEqualTo("date");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanFatArrowKeyword() {
        var source = "=>";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(FAT_ARROW);
        assertThat(tokens.get(0).lexeme()).isEqualTo("=>");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanIdentifier() {
        var source = "fieldName";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(IDENTIFIER);
        assertThat(tokens.get(0).lexeme()).isEqualTo("fieldName");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void identifierCanContainLatinCharacters() {
        var source = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(IDENTIFIER);
        assertThat(tokens.get(0).lexeme()).isEqualTo("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @ParameterizedTest
    @ValueSource(chars = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'})
    void resourcesStartWithUppercaseLatinCharacter(char uppercaseCharacter) {
        var source = uppercaseCharacter + "restOfResource";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(ELEMENT);
        assertThat(tokens.get(0).lexeme()).isEqualTo(source);
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanString() {
        var source = "\"a string\"";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(STRING);
        assertThat(tokens.get(0).lexeme()).isEqualTo("\"a string\"");
        assertThat(tokens.get(0).literal()).isEqualTo("a string");
    }

    @Test
    void canScanUnicodeString() {
        var source = "\"日本\"";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(STRING);
        assertThat(tokens.get(0).lexeme()).isEqualTo("\"日本\"");
        assertThat(tokens.get(0).literal()).isEqualTo("日本");
    }

    @Test
    void canScanResource() {
        var source = "ResourceName";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(2);
        assertThat(tokens.get(0).type()).isEqualTo(ELEMENT);
        assertThat(tokens.get(0).lexeme()).isEqualTo("ResourceName");
        assertThat(tokens.get(0).literal()).isNull();
    }

    @Test
    void canScanWhitespaces() {
        var source = "Patient . name = \"Davide\"";

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(6);
        assertThat(tokens.get(0).type()).isEqualTo(ELEMENT);
        assertThat(tokens.get(1).type()).isEqualTo(DOT);
        assertThat(tokens.get(2).type()).isEqualTo(IDENTIFIER);
        assertThat(tokens.get(3).type()).isEqualTo(EQUAL);
        assertThat(tokens.get(4).type()).isEqualTo(STRING);
        assertThat(tokens.get(5).type()).isEqualTo(EOF);
    }

    @Test
    void reportsStaticErrorOnUnknownCharacter() {
        var source = "日本";

        var lexer = new Lexer(source, errorService);
        lexer.scanTokens();

        verify(errorService, times(2)).staticError(1, "unexpected character");
    }

    @Test
    void reportsStaticErrorOnUnterminatedString() {
        var source = "\"Davide";

        var lexer = new Lexer(source, errorService);
        lexer.scanTokens();

        verify(errorService, times(1)).staticError(1, "unterminated string");
    }

    @Test
    void canScanMultipleLines() {
        var source =
                """
                        Patient
                        .name = HumanName
                        .familyName = "Davide"
                        """;

        var lexer = new Lexer(source, errorService);
        List<Token> tokens = lexer.scanTokens();

        assertThat(tokens.size()).isEqualTo(10);
        // Patient
        assertThat(tokens.get(0).type()).isEqualTo(ELEMENT);
        assertThat(tokens.get(0).line()).isEqualTo(1);

        // .
        assertThat(tokens.get(1).type()).isEqualTo(DOT);
        assertThat(tokens.get(1).line()).isEqualTo(2);

        // name
        assertThat(tokens.get(2).type()).isEqualTo(IDENTIFIER);
        assertThat(tokens.get(2).line()).isEqualTo(2);

        // =
        assertThat(tokens.get(3).type()).isEqualTo(EQUAL);
        assertThat(tokens.get(3).line()).isEqualTo(2);

        // HumanName
        assertThat(tokens.get(4).type()).isEqualTo(ELEMENT);
        assertThat(tokens.get(4).line()).isEqualTo(2);

        // .
        assertThat(tokens.get(5).type()).isEqualTo(DOT);
        assertThat(tokens.get(5).line()).isEqualTo(3);

        // familyName
        assertThat(tokens.get(6).type()).isEqualTo(IDENTIFIER);
        assertThat(tokens.get(6).line()).isEqualTo(3);

        // =
        assertThat(tokens.get(7).type()).isEqualTo(EQUAL);
        assertThat(tokens.get(7).line()).isEqualTo(3);

        // "Davide"
        assertThat(tokens.get(8).type()).isEqualTo(STRING);
        assertThat(tokens.get(8).line()).isEqualTo(3);

        // EOF
        assertThat(tokens.get(9).type()).isEqualTo(EOF);
        assertThat(tokens.get(9).line()).isEqualTo(4);
    }
}