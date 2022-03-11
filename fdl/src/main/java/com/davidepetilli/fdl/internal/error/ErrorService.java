package com.davidepetilli.fdl.internal.error;

import com.davidepetilli.fdl.internal.lexer.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Keep track of the errors found in the source. Static errors are syntactic problems in the source code, for example
 * unsupported characters. Runtime errors are all those errors that occur during interpretation, and cause the
 * interpretation to fail (ex. invalid resources not existing in FHIR: {@code FakeResource.name}).
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class ErrorService {
    private final List<FdlError> staticErrors = new ArrayList<>();
    private final List<FdlError> runtimeErrors = new ArrayList<>();

    /**
     * Add a static error to the errors.
     *
     * @param line         the line on which the error occurred
     * @param errorMessage the error message to add to the report
     */
    public void staticError(int line, String errorMessage) {
        staticErrors.add(new FdlError(line, errorMessage));
    }

    /**
     * Add a static error to the errors.
     *
     * @param token        the {@link Token} where the error occurred
     * @param errorMessage the error message to add to the report
     */
    public void staticError(Token token, String errorMessage) {
        staticErrors.add(new FdlError(token, errorMessage));
    }

    /**
     * Get the static errors reported.
     *
     * @return the list of static errors
     */
    public List<FdlError> staticErrors() {
        return staticErrors;
    }

    /**
     * Add a runtime error to the errors.
     *
     * @param line         the line where the error occurred
     * @param errorMessage the error message to add to the report
     */
    public void runtimeError(int line, String errorMessage) {
        runtimeErrors.add(new FdlError(line, errorMessage));
    }

    /**
     * Add a runtime error to the errors.
     *
     * @param token        the {@link Token} where the error occurred
     * @param errorMessage the error message to add to the report
     */
    public void runtimeError(Token token, String errorMessage) {
        runtimeErrors.add(new FdlError(token, errorMessage));
    }

    /**
     * Get the list of runtime errors reported.
     *
     * @return the list of runtime errors
     */
    public List<FdlError> runtimeErrors() {
        return runtimeErrors;
    }

    /**
     * Get the status of the errors. If any error occurred, it returns {@code true}.
     *
     * @return the status of the errors
     */
    public boolean hasError() {
        return staticErrors.size() > 0 || runtimeErrors.size() > 0;
    }
}
