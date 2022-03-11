package com.davidepetilli.fdl.internal.interpreter;


import com.davidepetilli.fdl.Expr;

/**
 * Extend the {@link ASTVisitor} interface in order to interpret the Abstract Syntax Tree.
 * <p>
 * The  {@code interpret} method, used to kick-off the whole interpretation job.
 * <p>
 * Each implementation can specialize in various outputs, for example FHIR or HL7, but currently only FHIR is supported.
 *
 * @param <T> the type for the result of {@code interpret()}.
 * @author Davide Petilli
 * @since 0.1
 */
public interface Interpreter<T> extends ASTVisitor {

    /**
     * The main interpretation method for the {@code Interpreter} class.
     *
     * @return {@code T} the results of the interpretation.
     */
    T interpret();

    /**
     * Associates the path resolution provided by a static analyzer, associated with a specific {@link Expr}.
     *
     * @param expr the expression.
     * @param path the path of the object associated with the expression.
     */
    void resolveElement(Expr expr, String path);
}
