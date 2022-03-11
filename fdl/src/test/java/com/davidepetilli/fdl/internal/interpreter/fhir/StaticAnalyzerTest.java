package com.davidepetilli.fdl.internal.interpreter.fhir;

import com.davidepetilli.fdl.Expr;
import com.davidepetilli.fdl.Stmt;
import com.davidepetilli.fdl.internal.interpreter.Interpreter;
import com.davidepetilli.fdl.internal.lexer.Token;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.davidepetilli.fdl.internal.lexer.TokenType.ELEMENT;
import static com.davidepetilli.fdl.internal.lexer.TokenType.IDENTIFIER;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StaticAnalyzerTest {

    @Mock
    Interpreter<Bundle> interpreter;

    @Test
    void setsCorrectResolutionPaths() {
        // Patient[1].name[0]=HumanName.given[0]="Lamont";
        final var patientResource = new Expr.Element(new Token(ELEMENT, "Patient", "Patient", 1), null);
        final var humanNameType = new Expr.Element(new Token(ELEMENT, "HumanName", "HumanName", 1), null);
        final var givenNameLiteral = new Expr.Literal("Lamont", null);

        List<Stmt> statements = List.of(
                new Stmt.Expression(
                        new Expr.Set(
                                patientResource,
                                new Token(IDENTIFIER, "name", null, 1),
                                new Expr.Set(
                                        humanNameType,
                                        new Token(IDENTIFIER, "given", null, 1),
                                        givenNameLiteral,
                                        new Expr.Literal(0, null)
                                ),
                                new Expr.Literal(0, null))
                )
        );

        var analyzer = new StaticAnalyzer(interpreter);
        analyzer.resolve(statements);

        verify(interpreter, times(1)).resolveElement(patientResource, "Patient0.");
        verify(interpreter, times(1)).resolveElement(humanNameType, "Patient0.name0.");
        verify(interpreter, times(1)).resolveElement(givenNameLiteral, "Patient0.name0.given0.");
    }

    @Test
    void setsCorrectResolutionPathsForBackboneElement() {
        // Patient.contact.telecom;
        final var patientResource = new Expr.Element(new Token(ELEMENT, "Patient", "Patient", 1), null);
        final var contactPoint = new Expr.Element(new Token(ELEMENT, "ContactPoint", "ContactPoint", 1), null);
        final var indexLiteral = new Expr.Literal("0", null);

        List<Stmt> statements = List.of(
                new Stmt.Expression(
                        new Expr.Set(
                                new Expr.Get(
                                        patientResource,
                                        new Token(IDENTIFIER, "contact", "contact", 1),
                                        indexLiteral
                                ),
                                new Token(IDENTIFIER, "telecom", "telecom", 1),
                                new Expr.Set(
                                        contactPoint,
                                        new Token(IDENTIFIER, "use", "use", 1),
                                        new Expr.Literal("home", null),
                                        indexLiteral
                                ),
                                indexLiteral
                        )
                ));

        var analyzer = new StaticAnalyzer(interpreter);
        analyzer.resolve(statements);

        verify(interpreter, times(1)).resolveElement(patientResource, "Patient0.");
        verify(interpreter, times(1)).resolveElement(contactPoint, "Patient0.contact0.telecom0.");
    }

}