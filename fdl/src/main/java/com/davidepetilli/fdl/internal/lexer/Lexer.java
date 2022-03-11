package com.davidepetilli.fdl.internal.lexer;

import com.davidepetilli.fdl.internal.error.ErrorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.davidepetilli.fdl.internal.lexer.TokenType.*;

/**
 * Scan a source string containing FDL (FHIR Definition Language) expressions and statements
 * and, upon {@code scanTokens()} invocation, return a list of {@link Token}s in the order found.
 * <p>
 * The list of {@link Token} must be parsed and interpreted.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class Lexer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Lexer.class);

    private static final String UNTERMINATED_STRING_ERROR = "unterminated string";
    private static final String UNEXPECTED_CHARACTER_ERROR = "unexpected character";

    private final ErrorService errors;
    private final String source;
    private final Map<String, TokenType> keywords;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int line = 1;

    public Lexer(String source, ErrorService errors) {
        this.source = source;
        this.errors = errors;
        this.keywords = initKeywords();
    }

    private Map<String, TokenType> initKeywords() {
        return Map.of(
                "as", AS,
                "boolean", BOOLEAN,
                "integer", INTEGER,
                "decimal", DECIMAL,
                "date", DATE
        );
    }

    /**
     * Scan the source string of the {@code Lexer} and find all the tokens supported by FDL.
     *
     * @return {@link List<Token>} the tokens found in the source.
     */
    public List<Token> scanTokens() {
        LOGGER.debug("starting token scan.");
        while (!isAtEnd()) {
            // We are at the beginning of the lexeme.
            start = current;
            scanToken();
        }
        tokens.add(new Token(EOF, "", null, line));

        LOGGER.debug("token scan complete.");
        return tokens;
    }

    private void scanToken() {
        char c = advance();
        switch (c) {
            case ' ', '\r', '\t':
                // Ignore whitespaces.
                break;
            case '\n':
                line++;
                break;
            case '.':
                addToken(DOT);
                break;
            case '=':
                addToken(match('>') ? FAT_ARROW : EQUAL);
                break;
            case ';':
                addToken(SEMICOLON);
                break;
            case '(':
                addToken(LEFT_PAREN);
                break;
            case ')':
                addToken(RIGHT_PAREN);
                break;
            case '[':
                addToken(LEFT_BRACKET);
                break;
            case ']':
                addToken(RIGHT_BRACKET);
                break;
            case '"':
                string();
                break;
            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line.
                    while (peek() != '\n' && !isAtEnd()) advance();
                }
                break;
            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlphaUppercase(c)) {
                    element();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    errors.staticError(line, UNEXPECTED_CHARACTER_ERROR);
                }
                break;
        }
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
//            if (peek() == '\n') line++;
            advance();
        }

        if (isAtEnd()) {
            errors.staticError(line, UNTERMINATED_STRING_ERROR);
            return;
        }

        advance();

        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    private void number() {
        while (isDigit(peek())) advance();
        addToken(NUMBER, Integer.parseInt(source.substring(start, current)));
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) type = IDENTIFIER;

        addToken(type);
    }

    private void element() {
        while (isAlpha(peek())) advance();

        addToken(ELEMENT);
    }

    private char advance() {
        return source.charAt(current++);
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;

        return true;
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        // Should we support also '_' in IDENTIFIER? If needed add it here: || c == '_'
        return (c >= 'a' && c <= 'z') || isAlphaUppercase(c);
    }

    private boolean isAlphaUppercase(char c) {
        return (c >= 'A' && c <= 'Z');
    }
}
