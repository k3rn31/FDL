package com.davidepetilli.fdl.internal.interpreter;


import com.davidepetilli.fdl.Expr;
import com.davidepetilli.fdl.Stmt;

/**
 * Define the Visitor of both {@link Expr.Visitor} and {@link Stmt.Visitor}.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public interface ASTVisitor extends Expr.Visitor<Object>, Stmt.Visitor<Void> {
}
