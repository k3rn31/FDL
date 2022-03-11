package com.davidepetilli.fdl.internal.interpreter.fhir;

import com.davidepetilli.fdl.Expr;
import com.davidepetilli.fdl.Stmt;
import com.davidepetilli.fdl.internal.lexer.TokenType;


import com.davidepetilli.fdl.internal.interpreter.ASTVisitor;
import com.davidepetilli.fdl.internal.interpreter.Interpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * This {@code StaticAnalyzer} class performs a pass in the interpretation of a FDL listing. It does not produce
 * side effects.
 * One of the jobs of this class is to perform a static analysis of the whole Abstract Syntax Tree, and provide a path
 * resolution to the interpreter.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class StaticAnalyzer implements ASTVisitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticAnalyzer.class);

    private final Interpreter<?> interpreter;
    private final Deque<StringBuilder> pathsStack = new ArrayDeque<>();
    private int currentLevel = 0;

    public StaticAnalyzer(Interpreter<?> interpreter) {
        this.interpreter = interpreter;
    }

    /**
     * Takes the AST as input and resolves each statement.
     *
     * @param statements the list of statements to be resolved.
     */
    public void resolve(List<Stmt> statements) {
        LOGGER.debug("starting static analysis.");
        statements.forEach(this::resolve);
        LOGGER.debug("static analysis complete.");
    }

    private void resolve(Stmt statement) {
        statement.accept(this);
    }

    private void resolve(Expr expression) {
        expression.accept(this);
    }

    private void resolveDownLevel(Expr expression) {
        try {
            currentLevel++;
            LOGGER.trace("going one level down: {}", currentLevel);
            expression.accept(this);
        } finally {
            currentLevel--;
            LOGGER.trace("going one level up: {}", currentLevel);
        }
    }

    @Override
    public Object visitDateExpr(Expr.Date expr) {
        LOGGER.trace("visiting Date expression; value: '{}', format: '{}'", expr.value, expr.format);
        return null;
    }

    @Override
    public Object visitElementExpr(Expr.Element expr) {
        var matcher = expr.matcher != null ? expr.matcher : "0";
        matcher = matcher instanceof Expr.Literal literal ? literal.value : matcher;
        LOGGER.trace("visiting Element expression; name: '{}', matcher: '{}'", expr.name.lexeme(), matcher);
        assert pathsStack.peek() != null;
        if (expr.name.type() == TokenType.ELEMENT && currentLevel != 0) {
            LOGGER.trace("resolving Element expression without appending to path.");
            resolveLocalElement(expr);
            return null;
        }
        pathsStack.peek().append(expr.name.lexeme()).append(matcher).append(".");
        LOGGER.trace("appended to path: '{}'", pathsStack.peek());
        resolveLocalElement(expr);
        return null;

    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        var index = expr.index != null ? ((Expr.Literal) expr.index).value : 0;
        LOGGER.trace("visiting Get expression: name: '{}', index: '{}'", expr.name.lexeme(), index);
        resolve(expr.object);
        assert pathsStack.peek() != null;
        pathsStack.peek().append(expr.name.lexeme()).append(index).append(".");
        LOGGER.trace("appended to path: '{}'", pathsStack.peek());
        resolveLocalElement(expr);
        return null;
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        LOGGER.trace("visiting Literal expression; value: '{}'", expr.value);
        resolveLocalElement(expr);
        return null;
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        var index = expr.index != null ? ((Expr.Literal) expr.index).value : 0;
        LOGGER.trace("visiting Set expression; name: '{}', value: '{}'", expr.name.lexeme(), index);
        resolve(expr.object);
        assert pathsStack.peek() != null;
        pathsStack.peek().append(expr.name.lexeme()).append(index).append(".");
        LOGGER.trace("appended to path: '{}'", pathsStack.peek());
        resolveDownLevel(expr.value);
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        LOGGER.trace("visiting Expression statement.");
        beginExpressionScope();
        resolve(stmt.expression);
        endExpressionScope();
        return null;
    }


    private void beginExpressionScope() {
        LOGGER.trace("entering new scope.");
        pathsStack.push(new StringBuilder());
    }

    private void endExpressionScope() {
        LOGGER.trace("leaving scope.");
        pathsStack.pop();
    }

    private void resolveLocalElement(Expr expr) {
        assert pathsStack.peek() != null;
        var path = pathsStack.peek().toString();
        LOGGER.debug("resolved '{}'", path);
        interpreter.resolveElement(expr, path);
    }
}
