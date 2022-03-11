package com.davidepetilli.fdl.internal.interpreter;

import com.davidepetilli.fdl.internal.error.RuntimeError;
import com.davidepetilli.fdl.internal.lexer.Token;
import com.github.sisyphsu.dateparser.DateParserUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Support type conversions.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class TypeService {
    private static final List<String> POSSIBLE_TRUE_VALUES = List.of("true", "yes", "y");
    private static final List<String> POSSIBLE_FALSE_VALUES = List.of("false", "no", "n");


    public enum Types {
        BOOLEAN, DECIMAL, INTEGER
    }

    /**
     * Converts a string to boolean. It interprets some string values as 'true' and some as 'false'.
     *
     * @param booleanString the string representing the boolean
     * @param token         the token on which it operates (used for error reporting)
     * @return the converted boolean value
     */
    public Boolean getAsBoolean(String booleanString, Token token) {
        if (POSSIBLE_TRUE_VALUES.contains(booleanString.toLowerCase())) {
            return true;
        } else if (POSSIBLE_FALSE_VALUES.contains(booleanString.toLowerCase())) {
            return false;
        }
        var errorMessage = String.format("impossible to interpret '%s' as 'boolean' value.", booleanString);
        throw new RuntimeError(token, errorMessage);
    }

    /**
     * Gets a local date from a date string and an optional format string. It performs guessing on the date format
     * if the format is not supplied.
     *
     * @param field  the field on which it operates (used for error reporting)
     * @param value  the date string
     * @param format the optional format string
     * @return the {@link LocalDate} generated
     */
    public LocalDate getAsLocalDate(Token field, Object value, Object format) {
        LocalDate result;

        if (format == null) {
            try {
                result = DateParserUtils.parseDateTime((String) value).toLocalDate();
            } catch (DateTimeParseException e) {
                var errorMessage = String.format("unrecognized date format: '%s'.", value);
                throw new RuntimeError(field, errorMessage);
            }
        } else {
            var formatter = DateTimeFormatter.ofPattern((String) format, Locale.ENGLISH);
            result = LocalDate.parse((String) value, formatter);
        }

        return result;
    }

    /**
     * Gets the double value from a string.
     *
     * @param value the string containing the double
     * @param token the token associated
     * @return the double parsed
     */
    public Double getAsDecimal(String value, Token token) {
        return Double.parseDouble(value);
    }


    /**
     * Gets the integer value from a string.
     *
     * @param value the string containing the integer
     * @param token the token associated
     * @return the integer parsed
     */
    public Integer getAsInteger(String value, Token token) {
        return Integer.parseInt(value);
    }
}
