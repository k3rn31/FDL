package com.davidepetilli.fdl.internal.error;

import com.davidepetilli.fdl.internal.lexer.Lexer;

/**
 * Raised by the FDL {@link Lexer} when it cannot proceed further
 * with processing.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class PanicError extends RuntimeException {
}
