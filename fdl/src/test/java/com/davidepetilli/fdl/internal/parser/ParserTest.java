package com.davidepetilli.fdl.internal.parser;

import com.davidepetilli.fdl.Expr;
import com.davidepetilli.fdl.Stmt;
import com.davidepetilli.fdl.internal.error.ErrorService;
import com.davidepetilli.fdl.internal.interpreter.TypeService;
import com.davidepetilli.fdl.internal.lexer.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.davidepetilli.fdl.internal.lexer.TokenType.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ParserTest {

    @Mock
    private ErrorService errorService;

    @Test
    void canParseResourceStatement() {
        // Patient;
        final var patientResourceToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final var tokens = List.of(
                patientResourceToken,
                new Token(SEMICOLON, ";", ";", 1),
                new Token(EOF, "", null, 1));

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        assertThat(statements.size()).isEqualTo(1);
        var statement = statements.get(0);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Element.class);
            var resource = (Expr.Element) stmt.expression;
            assertThat(resource.name).isEqualTo(patientResourceToken);
            return null;
        });
    }

    @Test
    void canParseResourceListWithIntegerMatcher() {
        // Patient[0];
        final var patientResourceToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final var tokens = List.of(
                patientResourceToken,
                new Token(LEFT_BRACKET, "[", null, 1),
                new Token(NUMBER, "0", 0, 1),
                new Token(RIGHT_BRACKET, "]", null, 1),
                new Token(SEMICOLON, ";", ";", 1),
                new Token(EOF, "", null, 1));

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        assertThat(statements.size()).isEqualTo(1);
        var statement = statements.get(0);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Element.class);
            var resource = (Expr.Element) stmt.expression;
            assertThat(resource.name).isEqualTo(patientResourceToken);
            assertThat(((Expr.Literal) resource.matcher).value).isEqualTo(0);
            return null;
        });
    }

    @Test
    void canParseResourceListWithStringMatcher() {
        // Patient["test"];
        final var patientResourceToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final var tokens = List.of(
                patientResourceToken,
                new Token(LEFT_BRACKET, "[", null, 1),
                new Token(STRING, "\"test\"", "test", 1),
                new Token(RIGHT_BRACKET, "]", null, 1),
                new Token(SEMICOLON, ";", ";", 1),
                new Token(EOF, "", null, 1));

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        assertThat(statements.size()).isEqualTo(1);
        var statement = statements.get(0);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Element.class);
            var resource = (Expr.Element) stmt.expression;
            assertThat(resource.name).isEqualTo(patientResourceToken);
            assertThat(((Expr.Literal) resource.matcher).value).isEqualTo("test");
            return null;
        });
    }

    @Test
    void catchesInvalidMatcher() {
        // Patient[11.2];
        final var patientResourceToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final Token decimalToken = new Token(DECIMAL, "11.2", "11.2", 1);
        final var tokens = List.of(
                patientResourceToken,
                new Token(LEFT_BRACKET, "[", null, 1),
                decimalToken,
                new Token(RIGHT_BRACKET, "]", null, 1),
                new Token(SEMICOLON, ";", ";", 1),
                new Token(EOF, "", null, 1));

        var parser = new Parser(tokens, errorService);
        parser.parse();

        verify(errorService, times(1))
                .staticError(decimalToken, "expect an integer number or a string.");
    }

    @Test
    void canParseGetFieldListElementWithIntegerIndex() {
        // Patient.name[0];
        final var identifierToken = new Token(IDENTIFIER, "name", "name", 1);
        final var tokens = List.of(
                new Token(ELEMENT, "Patient", "Patient", 1),
                new Token(DOT, ".", ".", 1),
                identifierToken,
                new Token(LEFT_BRACKET, "[", null, 1),
                new Token(NUMBER, "0", 0, 1),
                new Token(RIGHT_BRACKET, "]", null, 1),
                new Token(SEMICOLON, ";", ";", 1),
                new Token(EOF, "", null, 1));

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        assertThat(statements.size()).isEqualTo(1);
        var statement = statements.get(0);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Get.class);
            var get = (Expr.Get) stmt.expression;
            assertThat(get.name).isEqualTo(identifierToken);
            final var index = (Expr.Literal) get.index;
            assertThat(index.value).isEqualTo(0);
            return null;
        });
    }

    @Test
    void canParseSetFieldListElementWithIntegerIndex() {
        // Patient.name[0]="10";
        final var identifierToken = new Token(IDENTIFIER, "name", "name", 1);
        final var tokens = List.of(
                new Token(ELEMENT, "Patient", "Patient", 1),
                new Token(DOT, ".", ".", 1),
                identifierToken,
                new Token(LEFT_BRACKET, "[", null, 1),
                new Token(NUMBER, "0", 0, 1),
                new Token(RIGHT_BRACKET, "]", null, 1),
                new Token(EQUAL, "=", null, 1),
                new Token(STRING, "\"10\"", "10", 1),
                new Token(SEMICOLON, ";", ";", 1),
                new Token(EOF, "", null, 1));

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        assertThat(statements.size()).isEqualTo(1);
        var statement = statements.get(0);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Set.class);
            var set = (Expr.Set) stmt.expression;
            assertThat(set.name).isEqualTo(identifierToken);
            final var value = (Expr.Literal) set.value;
            assertThat(value.value).isEqualTo("10");
            final var index = (Expr.Literal) set.index;
            assertThat(index.value).isEqualTo(0);
            return null;
        });
    }

    @Test
    void fieldListElementMustHaveIntegerIndex() {
        // Patient.name["0"];
        final var identifierToken = new Token(IDENTIFIER, "name", "name", 1);
        final var stringToken = new Token(STRING, "\"0\"", "0", 1);
        final var tokens = List.of(
                new Token(ELEMENT, "Patient", "Patient", 1),
                new Token(DOT, ".", ".", 1),
                identifierToken,
                new Token(LEFT_BRACKET, "[", null, 1),
                stringToken,
                new Token(RIGHT_BRACKET, "]", null, 1),
                new Token(SEMICOLON, ";", ";", 1),
                new Token(EOF, "", null, 1));

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        assertThat(statements.size()).isEqualTo(1);
        verify(errorService, times(1)).staticError(stringToken, "expect a number.");
    }

    @Test
    void canParseResourceAssignment() {
        // Patient.active="true";
        final var patientResourceToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final var identifierToken = new Token(IDENTIFIER, "active", "active", 1);
        final var tokens = List.of(
                patientResourceToken,
                new Token(DOT, ".", ".", 1),
                identifierToken,
                new Token(EQUAL, "=", "=", 1),
                new Token(STRING, "\"true\"", "true", 1),
                new Token(SEMICOLON, ";", ";", 1),
                new Token(EOF, "", null, 1));

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        assertThat(statements.size()).isEqualTo(1);
        var statement = statements.get(0);
        assertThat(statement).isInstanceOf(Stmt.Expression.class);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Set.class);
            var set = (Expr.Set) stmt.expression;
            assertThat(set.name).isEqualTo(identifierToken);
            assertThat(set.object).isInstanceOf(Expr.Element.class);
            assertThat(set.value).isInstanceOf(Expr.Literal.class);
            var resource = (Expr.Element) set.object;
            var value = (Expr.Literal) set.value;
            assertThat(resource.name).isEqualTo(patientResourceToken);
            assertThat(value.value).isEqualTo("true");
            return null;
        });
    }

    @Test
    void resourceAssignmentMustBeAssignableType() {
        // Patient.active=10;
        final var patientResourceToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final var identifierToken = new Token(IDENTIFIER, "active", "active", 1);
        final var numberToken = new Token(NUMBER, "10", 10, 1);
        final var tokens = List.of(
                patientResourceToken,
                new Token(DOT, ".", ".", 1),
                identifierToken,
                new Token(EQUAL, "=", "=", 1),
                numberToken,
                new Token(SEMICOLON, ";", ";", 1),
                new Token(EOF, "", null, 1));

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        assertThat(statements.size()).isEqualTo(1);
        verify(errorService, times(1))
                .staticError(numberToken, "expect a string.");
    }

    @Test
    void catchesMissingPropertyAfterDot() {
        // Patient.10;
        final var patientResourceToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final var numberToken = new Token(NUMBER, "10", 10, 1);
        final var tokens = List.of(
                patientResourceToken,
                new Token(DOT, ".", ".", 1),
                numberToken,
                new Token(SEMICOLON, ";", ";", 1),
                new Token(EOF, "", null, 1));

        var parser = new Parser(tokens, errorService);
        parser.parse();

        verify(errorService, times(1))
                .staticError(numberToken, "expect a property after '.'.");
    }

    @Test
    void expectsASemicolonToEndTheStatement() {
        // Patient
        final var tokenEOF = new Token(EOF, "", null, 1);
        var tokens = List.of(
                new Token(ELEMENT, "Patient", "Patient", 1),
                tokenEOF);

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        assertThat(statements.size()).isEqualTo(1);
        verify(errorService, times(1))
                .staticError(tokenEOF, "expect ';' after expression.");
    }

    @Test
    void canParseDateTypeDefinition() {
        // Patient.text=("8/27/1929" as date);
        var tokens = List.of(
                new Token(ELEMENT, "Patient", "Patient", 1),
                new Token(DOT, ".", null, 1),
                new Token(IDENTIFIER, "text", "text", 1),
                new Token(EQUAL, "=", null, 1),
                new Token(LEFT_PAREN, "(", null, 1),
                new Token(STRING, "\"8/27/1929\"", "8/27/1929", 1),
                new Token(AS, "as", null, 1),
                new Token(DATE, "date", null, 1),
                new Token(RIGHT_PAREN, ")", null, 1),
                new Token(SEMICOLON, ";", null, 1),
                new Token(EOF, "", null, 1)
        );

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        var statement = statements.get(0);
        assertThat(statement).isInstanceOf(Stmt.Expression.class);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Set.class);
            var set = (Expr.Set) stmt.expression;
            assertThat(set.value).isInstanceOf(Expr.Date.class);
            var date = (Expr.Date) set.value;
            assertThat(date.value).isEqualTo("8/27/1929");
            assertThat(date.format).isNull();
            return null;
        });
    }

    @Test
    void canParseDateTypeWithFormatDefinition() {
        // Patient.text=("8/27/1929" as date => "MM/dd/yyy");
        var tokens = List.of(
                new Token(ELEMENT, "Patient", "Patient", 1),
                new Token(DOT, ".", null, 1),
                new Token(IDENTIFIER, "text", "text", 1),
                new Token(EQUAL, "=", null, 1),
                new Token(LEFT_PAREN, "(", null, 1),
                new Token(STRING, "\"8/27/1929\"", "8/27/1929", 1),
                new Token(AS, "as", null, 1),
                new Token(DATE, "date", null, 1),
                new Token(FAT_ARROW, "=>", null, 1),
                new Token(STRING, "\"MM/dd/yyyy\"", "MM/dd/yyyy", 1),
                new Token(RIGHT_PAREN, ")", null, 1),
                new Token(SEMICOLON, ";", null, 1),
                new Token(EOF, "", null, 1)
        );

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        var statement = statements.get(0);
        assertThat(statement).isInstanceOf(Stmt.Expression.class);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Set.class);
            var set = (Expr.Set) stmt.expression;
            assertThat(set.value).isInstanceOf(Expr.Date.class);
            var date = (Expr.Date) set.value;
            assertThat(date.value).isEqualTo("8/27/1929");
            assertThat(date.format).isEqualTo("MM/dd/yyyy");
            return null;
        });
    }

    @Test
    void reportsErrorIfTypeIsNotValid() {
        // Patient.text=("8/27/1929" as novalidtype => "MM/dd/yyy");
        var tokens = List.of(
                new Token(ELEMENT, "Patient", "Patient", 1),
                new Token(DOT, ".", null, 1),
                new Token(IDENTIFIER, "text", "text", 1),
                new Token(EQUAL, "=", null, 1),
                new Token(LEFT_PAREN, "(", null, 1),
                new Token(STRING, "\"8/27/1929\"", "8/27/1929", 1),
                new Token(AS, "as", null, 1),
                new Token(IDENTIFIER, "novalidtype", null, 1),
                new Token(FAT_ARROW, "=>", null, 1),
                new Token(STRING, "\"MM/dd/yyyy\"", "MM/dd/yyyy", 1),
                new Token(RIGHT_PAREN, ")", null, 1),
                new Token(SEMICOLON, ";", null, 1),
                new Token(EOF, "", null, 1)
        );

        var parser = new Parser(tokens, errorService);
        parser.parse();

        verify(errorService, times(1))
                .staticError(any(), eq("expect a valid type keyword."));
    }

    @Test
    void canParseBooleanType() {
        // Patient.active=("yes" as boolean);
        var tokens = List.of(
                new Token(ELEMENT, "Patient", "Patient", 1),
                new Token(DOT, ".", null, 1),
                new Token(IDENTIFIER, "active", "active", 1),
                new Token(EQUAL, "=", null, 1),
                new Token(LEFT_PAREN, "(", null, 1),
                new Token(STRING, "\"yes\"", "yes", 1),
                new Token(AS, "as", null, 1),
                new Token(BOOLEAN, "boolean", null, 1),
                new Token(RIGHT_PAREN, ")", null, 1),
                new Token(SEMICOLON, ";", null, 1),
                new Token(EOF, "", null, 1)
        );

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        var statement = statements.get(0);
        assertThat(statement).isInstanceOf(Stmt.Expression.class);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Set.class);
            var set = (Expr.Set) stmt.expression;
            assertThat(set.value).isInstanceOf(Expr.Literal.class);
            var date = (Expr.Literal) set.value;
            assertThat(date.value).isEqualTo("yes");
            assertThat(date.type).isEqualTo(TypeService.Types.BOOLEAN);
            return null;
        });
    }

    @Test
    void canParseIntegerType() {
        // Goal.target.detail=("10" as integer);
        var tokens = List.of(
                new Token(ELEMENT, "Goal", "Goal", 1),
                new Token(DOT, ".", null, 1),
                new Token(IDENTIFIER, "target", "target", 1),
                new Token(DOT, ".", null, 1),
                new Token(IDENTIFIER, "detail", "detail", 1),
                new Token(EQUAL, "=", null, 1),
                new Token(LEFT_PAREN, "(", null, 1),
                new Token(STRING, "\"10\"", "10", 1),
                new Token(AS, "as", null, 1),
                new Token(INTEGER, "integer", null, 1),
                new Token(RIGHT_PAREN, ")", null, 1),
                new Token(SEMICOLON, ";", null, 1),
                new Token(EOF, "", null, 1)
        );

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        var statement = statements.get(0);
        assertThat(statement).isInstanceOf(Stmt.Expression.class);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Set.class);
            var set = (Expr.Set) stmt.expression;
            assertThat(set.value).isInstanceOf(Expr.Literal.class);
            var date = (Expr.Literal) set.value;
            assertThat(date.value).isEqualTo("10");
            assertThat(date.type).isEqualTo(TypeService.Types.INTEGER);
            return null;
        });
    }

    @Test
    void canParseDecimalType() {
        // Quantity.value=("11.2" as decimal);
        var tokens = List.of(
                new Token(ELEMENT, "Quantity", "Quantity", 1),
                new Token(DOT, ".", null, 1),
                new Token(IDENTIFIER, "value", "value", 1),
                new Token(EQUAL, "=", null, 1),
                new Token(LEFT_PAREN, "(", null, 1),
                new Token(STRING, "\"11.2\"", "11.2", 1),
                new Token(AS, "as", null, 1),
                new Token(DECIMAL, "decimal", null, 1),
                new Token(RIGHT_PAREN, ")", null, 1),
                new Token(SEMICOLON, ";", null, 1),
                new Token(EOF, "", null, 1)
        );

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();

        var statement = statements.get(0);
        assertThat(statement).isInstanceOf(Stmt.Expression.class);
        statement.accept(stmt -> {
            assertThat(stmt.expression).isInstanceOf(Expr.Set.class);
            var set = (Expr.Set) stmt.expression;
            assertThat(set.value).isInstanceOf(Expr.Literal.class);
            var date = (Expr.Literal) set.value;
            assertThat(date.value).isEqualTo("11.2");
            assertThat(date.type).isEqualTo(TypeService.Types.DECIMAL);
            return null;
        });
    }

    @Test
    void catchesInvalidTargetAssignment() {
        // "a string"=("11.2" as decimal);
        final Token equalToken = new Token(EQUAL, "=", null, 1);
        var tokens = List.of(
                new Token(STRING, "a string", "a string", 1),
                equalToken,
                new Token(STRING, "\"11.2\"", "11.2", 1),
                new Token(SEMICOLON, ";", null, 1),
                new Token(EOF, "", null, 1)
        );

        var parser = new Parser(tokens, errorService);
        parser.parse();

        verify(errorService, times(1))
                .staticError(equalToken, "invalid assignment target.");
    }
}