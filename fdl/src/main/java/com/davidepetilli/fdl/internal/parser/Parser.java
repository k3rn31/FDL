package com.davidepetilli.fdl.internal.parser;

import com.davidepetilli.fdl.internal.lexer.Lexer;
import com.davidepetilli.fdl.Expr;
import com.davidepetilli.fdl.Stmt;
import com.davidepetilli.fdl.internal.error.ErrorService;
import com.davidepetilli.fdl.internal.error.PanicError;
import com.davidepetilli.fdl.internal.interpreter.TypeService;
import com.davidepetilli.fdl.internal.lexer.Token;
import com.davidepetilli.fdl.internal.lexer.TokenType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.davidepetilli.fdl.internal.lexer.TokenType.*;

/**
 * Get a list of {@link Token} (extracted by the
 * {@link Lexer}) and generate the Abstract Syntax Tree.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class Parser {
    private static final Logger LOGGER = LoggerFactory.getLogger(Parser.class);

    private final ErrorService error;
    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens, ErrorService error) {
        this.tokens = tokens;
        this.error = error;
    }

    /**
     * Parse the tokens and generate the list of {@link Stmt}.
     *
     * @return the list of {@link Stmt}
     */
    public List<Stmt> parse() {
        LOGGER.debug("starting parsing {} tokens", tokens.size());

        var statements = new ArrayList<Stmt>();
        while (!isAtEnd()) {
            statements.add(statement());
        }
        LOGGER.debug("parsing complete; produced {} statements.", statements.size());

        return statements;
    }

    private Stmt statement() {
        try {
            Expr expr = expression();
            consume(SEMICOLON, "expect ';' after expression.");

            return new Stmt.Expression(expr);
        } catch (PanicError e) {
            // We don't stop parsing since we want to report all the errors catchable in the full set of FDL statements.
            // We skip all cascading error rising tokens and synchronize to the next statement.
            synchronize();
            return null;
        }
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = deference();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = primitive();

            if (expr instanceof Expr.Get get) {
                Expr object = get.object;
                Token name = get.name;
                Expr index = get.index;

                return new Expr.Set(object, name, value, index);
            }
            error.staticError(equals, "invalid assignment target.");
        }
        return expr;
    }

    private Expr primitive() {
        if (match(LEFT_PAREN)) {
            return typedef();
        } else {
            return assignment();
        }
    }

    @NotNull
    private Expr typedef() {
        Expr value;
        var text = consume(STRING, "expect a string after '('.").literal();
        consume(AS, "expect 'as' after type value");
        switch (advance().type()) {
            case BOOLEAN:
                value = new Expr.Literal(text, TypeService.Types.BOOLEAN);
                break;
            case DATE:
                if (match(FAT_ARROW)) {
                    var format = consume(STRING, "expect format after '=>'.");
                    value = new Expr.Date(text, format.literal());
                } else {
                    value = new Expr.Date(text, null);
                }
                break;
            case DECIMAL:
                value = new Expr.Literal(text, TypeService.Types.DECIMAL);
                break;
            case INTEGER:
                value = new Expr.Literal(text, TypeService.Types.INTEGER);
                break;
            default:
                error.staticError(peek(), "expect a valid type keyword.");
                throw new PanicError();
        }
        consume(RIGHT_PAREN, "expect ')' after type definition.");

        return value;
    }

    private Expr deference() {
        Expr expr = receiver();

        while (true) {
            if (match(DOT)) {
                if (check(IDENTIFIER)) {
                    Token name = consume(IDENTIFIER, "expect identifier.");
                    Expr index = null;

                    if (match(LEFT_BRACKET)) {
                        index = number();
                        consume(RIGHT_BRACKET, "expect ']' after index.");
                    }

                    expr = new Expr.Get(expr, name, index);
                } else {
                    error.staticError(peek(), "expect a property after '.'.");
                }
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr receiver() {
        if (check(ELEMENT)) {
            return declaration();
        } else {
            return string();
        }
    }

    private Expr declaration() {
        Token name = consume(ELEMENT, "expect element name.");
        Expr matcher = null;

        if (match(LEFT_BRACKET)) {
            matcher = matcher();
            consume(RIGHT_BRACKET, "expect ']' after position matcher.");
        }

        return new Expr.Element(name, matcher);
    }

    private Expr matcher() {
        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal(), null);
        }

        error.staticError(peek(), "expect an integer number or a string.");
        throw new PanicError();
    }

    private Expr string() {
        Token string = consume(STRING, "expect a string.");
        return new Expr.Literal(string.literal(), null);
    }

    private Expr number() {
        Token number = consume(NUMBER, "expect a number.");
        return new Expr.Literal(number.literal(), null);
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType tokenType, String errorMessage) {
        if (check(tokenType)) {
            return advance();
        }

        error.staticError(peek(), errorMessage);

        // We panic! That's unuseful to continue parsing as the errors would cascade.
        // synchronize will put us back to the next statement.
        throw new PanicError();
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            // If previous token is SEMICOLON, we are at the beginning of a new statement.
            if (previous().type() == SEMICOLON) return;

            // If new statement types are added to the language, they should be checked here...

            advance();
        }
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private boolean check(TokenType tokenType) {
        if (isAtEnd()) return false;
        return peek().type() == tokenType;
    }

    private boolean isAtEnd() {
        return peek().type() == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }
}
