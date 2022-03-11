package com.davidepetilli.fdl.internal.interpreter.fhir;

import ca.uhn.fhir.model.api.IElement;
import com.davidepetilli.fdl.Expr;
import com.davidepetilli.fdl.Stmt;
import com.davidepetilli.fdl.internal.error.ErrorService;
import com.davidepetilli.fdl.internal.error.RuntimeError;
import com.davidepetilli.fdl.internal.interpreter.Interpreter;
import com.davidepetilli.fdl.internal.interpreter.TypeService;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * Implements {@link Interpreter} and specializes in producing a FHIR Bundle.
 * Since interpreter extends the two Visitor pattern defined in the {@link Expr} and {@link Stmt} abstract classes,
 * it has to implement all the 'visit' methods.
 * <p>
 * When a statement is executed, the 'accept' method of that statement is invoked with the instance of this
 * class (which is a {@link it.davidepetilli.experiments.fdl.Expr.Visitor}
 * and {@link it.davidepetilli.experiments.fdl.Stmt.Visitor}. This accept method triggers the visit method of the
 * statement so that the AST is visited recursively.
 * <p>
 * The {@code execute} and {@code evaluate} methods are analogous, the first executes the {@code accept} on statements,
 * and the former executes the {@code accept} on expressions.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class FhirInterpreter implements Interpreter<Bundle> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FhirInterpreter.class);

    private static final String SCOPE_RESOLUTION_ERROR =
            "unexpected error in scope resolution; this should not happen.";
    private static final String ONLY_ELEMENTS_HAVE_PROPERTIES_ERROR =
            "tried to access a property on an invalid FHIR element.";
    private static final String PROPERTY_DOES_NOT_EXIST_OR_WRONG_TYPE_ERROR =
            "the field doesn't exist or the element type doesn't match.";

    private final List<Stmt> statements;
    private final ErrorService error;
    private final FhirReflectiveEngine fhirEngine;
    private final TypeService typeService;

    // TODO: This list should be substituted by a proper context handling.
    private final Map<String, Resource> generatedResources = new TreeMap<>();
    private final Map<Expr, String> objectIdentityResolveTable = new HashMap<>();
    private final Map<String, Object> environment = new HashMap<>();
    private int currentLevel = 0;

    public FhirInterpreter(List<Stmt> statements,
                           FhirReflectiveEngine fhirEngine,
                           TypeService typeService,
                           ErrorService errorService) {
        this.statements = statements;
        this.fhirEngine = fhirEngine;
        this.error = errorService;
        this.typeService = typeService;
    }

    /**
     * Starts the interpretation process, visiting the AST on each statement, then it instantiates a {@link Bundle}.
     * If there have been errors during the processing, the {@link Bundle} is returned empty, otherwise, all the
     * generated resources are added to it.
     *
     * @return {@link Bundle} The FHIR Bundle containing all the resources interpreted from the FDL statements.
     */
    @Override
    public Bundle interpret() {
        LOGGER.debug("starting FHIR interpretation.");
        for (Stmt statement : statements) {
            try {
                execute(statement);
            } catch (RuntimeError e) {
                error.runtimeError(e.getToken(), e.getMessage());
            }
        }

        var bundle = new Bundle();
        if (error.hasError()) {
            LOGGER.debug("returning empty Bundle due to errors.");
            return bundle;
        }

        var entryComponents = new ArrayList<Bundle.BundleEntryComponent>();

        for (var resource : generatedResources.entrySet()) {
            var entryComponent = new Bundle.BundleEntryComponent();
            entryComponent.setResource(resource.getValue());
            entryComponents.add(entryComponent);
        }

        LOGGER.debug("adding {} entries to Bundle.", entryComponents.size());
        bundle.setEntry(entryComponents);
        bundle.setType(Bundle.BundleType.BATCH);

        LOGGER.debug("FHIR interpretation complete.");
        return bundle;
    }

    @Override
    public void resolveElement(Expr expr, String path) {
        objectIdentityResolveTable.put(expr, path);
    }

    private void execute(Stmt statement) {
        statement.accept(this);
    }

    private Object evaluate(Expr expression) {
        return expression.accept(this);
    }

    private Object evaluateDownLevel(Expr expr) {
        try {
            currentLevel++;
            LOGGER.trace("going one level down: {}", currentLevel);
            return evaluate(expr);
        } finally {
            currentLevel--;
            LOGGER.trace("going one level up: {}", currentLevel);
        }
    }

    @Override
    public Object visitDateExpr(Expr.Date expr) {
        LOGGER.trace("visiting Date expression; value: '{}', format: '{}'", expr.value, expr.format);
        return typeService.getAsLocalDate(null, expr.value, expr.format);
    }

    @Override
    public Object visitElementExpr(Expr.Element expr) {
        var name = expr.name;
        var matcher = expr.matcher != null ? ((Expr.Literal) expr.matcher).value : "0";
        LOGGER.trace("visiting Element expression; name: '{}', matcher: '{}'", name.lexeme(), matcher);

        var path = objectIdentityResolveTable.get(expr);
        if (path == null || path.isBlank()) {
            throw new RuntimeError(expr.name, SCOPE_RESOLUTION_ERROR);
        }

        var result = environment.get(path);
        if (result != null) {
            LOGGER.trace("element already instantiated, returning it: '{}'", path);
            return result;
        }

        LOGGER.trace("instantiating new element.");
        result = fhirEngine.instantiateElement(name);
        environment.put(path, result);

        // We add here for now, but we still need a proper context management.
        if (currentLevel == 0 && result instanceof Resource resource) {
            LOGGER.trace("element is a top level resource, adding to the generated resources.");
            generatedResources.put(path, resource);
        }

        return result;
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        var index = expr.index != null ? (Integer) evaluate(expr.index) : Integer.valueOf(0);
        LOGGER.trace("visiting Get expression: name: '{}', index: '{}'", expr.name.lexeme(), index);

        var path = objectIdentityResolveTable.get(expr);
        var result = environment.get(path);
        if (result != null) {
            LOGGER.trace("element is cached, returning it: '{}'", path);
            return result;
        }
        if (!(object instanceof IElement)) {
            throw new RuntimeError(expr.name, ONLY_ELEMENTS_HAVE_PROPERTIES_ERROR);
        }

        result = fhirEngine.getPropertyFromElement(expr.name, object, index);
        environment.put(path, result);
        LOGGER.trace("got new property and added it to the cache.");
        return result;
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        LOGGER.trace("visiting Literal expression; value: '{}'", expr.value);
        if (expr.type != null) {
            LOGGER.trace("Literal has known type, getting it.");
            return switch ((TypeService.Types) expr.type) {
                case BOOLEAN -> typeService.getAsBoolean((String) expr.value, null);
                case DECIMAL -> typeService.getAsDecimal((String) expr.value, null);
                case INTEGER -> typeService.getAsInteger((String) expr.value, null);
            };
        }

        return expr.value;
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        var element = evaluate(expr.object);
        var value = evaluateDownLevel(expr.value);
        var index = expr.index != null ? (Integer) evaluate(expr.index) : Integer.valueOf(0);
        LOGGER.trace("visiting Set expression; name: '{}', value: '{}'", expr.name.lexeme(), index);

        if (!(element instanceof IElement)) {
            throw new RuntimeError(expr.name, ONLY_ELEMENTS_HAVE_PROPERTIES_ERROR);
        }

        // If the type is declared, no need to do guess-work, we can go straight to the type specific assignment.
        if (value instanceof LocalDate date) {
            LOGGER.trace("setting LocalDate on '{}'.", expr.name.lexeme());
            var result = fhirEngine.setDateOnElement(element, expr.name, date);
            if (result.isPresent()) return result.get();
        } else if (value instanceof Boolean bool) {
            LOGGER.trace("setting Boolean on '{}'.", expr.name.lexeme());
            var result = fhirEngine.setBooleanOnElement(element, expr.name, bool);
            if (result.isPresent()) return result.get();
        } else if (value instanceof Double decimal) {
            LOGGER.trace("setting Double on '{}'.", expr.name.lexeme());
            var result = fhirEngine.setDecimalOnElement(element, expr.name, decimal);
            if (result.isPresent()) return result.get();
        } else if (value instanceof Integer integer) {
            LOGGER.trace("setting Integer on '{}'.", expr.name.lexeme());
            var result = fhirEngine.setIntegerOnElement(element, expr.name, integer);
            if (result.isPresent()) return result.get();
        }

        // If the type is not defined, lets try to guess it.
        return fhirEngine.trySetPropertyOnElement(element, value, expr.name, index)
                .orElseThrow(() -> new RuntimeError(
                        expr.name, PROPERTY_DOES_NOT_EXIST_OR_WRONG_TYPE_ERROR));
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        LOGGER.trace("visiting Expression statement.");
        evaluate(stmt.expression);
        return null;
    }
}
