package com.davidepetilli.fdl.internal.interpreter.fhir;

import com.davidepetilli.fdl.Expr;
import com.davidepetilli.fdl.Stmt;
import com.davidepetilli.fdl.internal.error.ErrorService;
import com.davidepetilli.fdl.internal.error.RuntimeError;
import com.davidepetilli.fdl.internal.interpreter.TypeService;
import com.davidepetilli.fdl.internal.lexer.Token;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.davidepetilli.fdl.internal.lexer.TokenType.ELEMENT;
import static com.davidepetilli.fdl.internal.lexer.TokenType.IDENTIFIER;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FhirInterpreterTest {

    @Mock
    private ErrorService errorService;

    @Mock
    private FhirReflectiveEngine fhirEngine;

    @Mock
    private TypeService typeService;

    @Test
    void canInstantiateElement() {
        // Patient;
        final Token patientToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final Expr patientResource = new Expr.Element(patientToken, null);
        List<Stmt> statements = List.of(new Stmt.Expression(patientResource));
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(patientResource, "Patient0.");

        interpreter.interpret();

        verify(fhirEngine, times(1)).instantiateElement(patientToken);
    }

    @Test
    void canReturnBundle() {
        // Patient;
        final Token patientToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final Expr patientResource = new Expr.Element(patientToken, null);
        List<Stmt> statements = List.of(new Stmt.Expression(patientResource));
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(patientResource, "Patient0.");
        when(fhirEngine.instantiateElement(any(Token.class))).thenReturn(new Patient());

        var bundle = interpreter.interpret();

        assertThat(bundle).isInstanceOf(Bundle.class);
        assertThat(bundle.getEntry().size()).isEqualTo(1);
        assertThat(bundle.getEntry().get(0).getResource()).isInstanceOf(Patient.class);
    }

    @Test
    void onlyFirstLevelResourcesGetIntoBundle() {
        // Patient.fake=Patient;
        final Token patientToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final Expr patient1Resource = new Expr.Element(patientToken, null);
        final Expr patient2Resource = new Expr.Element(patientToken, null);
        List<Stmt> statements = List.of(new Stmt.Expression(
                new Expr.Set(
                        patient1Resource,
                        new Token(IDENTIFIER, "fake", "fake", 1),
                        patient2Resource,
                        new Expr.Literal(0, null))
        ));
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(patient1Resource, "Patient0.");
        interpreter.resolveElement(patient2Resource, "Patient0.fake0.");
        when(fhirEngine.instantiateElement(any(Token.class))).thenReturn(new Patient());

        var bundle = interpreter.interpret();

        assertThat(bundle).isInstanceOf(Bundle.class);
        assertThat(bundle.getEntry().size()).isEqualTo(1);
    }

    @Test
    void canReferenceSameElement() {
        // Patient[0];
        // Patient[0];
        when(fhirEngine.instantiateElement(any(Token.class))).thenReturn(new Patient());
        final Token patient1Token = new Token(ELEMENT, "Patient", "Patient", 1);
        final Token patient2Token = new Token(ELEMENT, "Patient", "Patient", 2);
        final Expr patient1Resource = new Expr.Element(patient1Token, new Expr.Literal(0, null));
        final Expr patient2Resource = new Expr.Element(patient2Token, new Expr.Literal(0, null));
        List<Stmt> statements = List.of(new Stmt.Expression(patient1Resource), new Stmt.Expression(patient2Resource));
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(patient1Resource, "Patient0.");
        interpreter.resolveElement(patient2Resource, "Patient0.");

        interpreter.interpret();

        verify(fhirEngine, times(1)).instantiateElement(any(Token.class));
    }

    @Test
    void canReferenceMultipleResource() {
        // Patient["patient0"];
        // Patient["patient0"];
        // Patient[1];
        final Token patient1Token = new Token(ELEMENT, "Patient", "Patient", 1);
        final Token patient2Token = new Token(ELEMENT, "Patient", "Patient", 2);
        final Token patient3Token = new Token(ELEMENT, "Patient", "Patient", 3);
        when(fhirEngine.instantiateElement(patient1Token)).thenReturn(new Patient());
        when(fhirEngine.instantiateElement(patient3Token)).thenReturn(new Patient());
        final Expr patient1Resource = new Expr.Element(patient1Token, new Expr.Literal("patient0", null));
        final Expr patient2Resource = new Expr.Element(patient2Token, new Expr.Literal("patient0", null));
        final Expr patient3Resource = new Expr.Element(patient3Token, new Expr.Literal(1, null));
        List<Stmt> statements = List.of(
                new Stmt.Expression(patient1Resource),
                new Stmt.Expression(patient2Resource),
                new Stmt.Expression(patient3Resource)
        );
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(patient1Resource, "Patientpatient0.");
        interpreter.resolveElement(patient2Resource, "Patientpatient0.");
        interpreter.resolveElement(patient3Resource, "Patient1.");

        interpreter.interpret();

        verify(fhirEngine, times(1)).instantiateElement(patient1Token);
        verify(fhirEngine, never()).instantiateElement(patient2Token);
        verify(fhirEngine, times(1)).instantiateElement(patient3Token);
    }

    @Test
    void handlesPatientWithoutMatcherAsPatientZero() {
        // Patient;
        // Patient[0];
        final Token patientToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final Token patient0Token = new Token(ELEMENT, "Patient", "Patient", 2);
        final Expr patientResource = new Expr.Element(patientToken, null);
        final Expr patient0Resource = new Expr.Element(patient0Token, new Expr.Literal(0, null));
        List<Stmt> statements = List.of(new Stmt.Expression(patientResource), new Stmt.Expression(patient0Resource));
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(patientResource, "Patient0.");
        interpreter.resolveElement(patient0Resource, "Patient0.");
        when(fhirEngine.instantiateElement(any(Token.class))).thenReturn(new Patient());

        interpreter.interpret();

        verify(fhirEngine, times(1)).instantiateElement(patientToken);
        verify(fhirEngine, never()).instantiateElement(patient0Token);
    }

    @Test
    void reportsRuntimeErrorIfResourceIsInvalid() {
        // FakeInvalidResource;
        final Token resourceToken = new Token(ELEMENT, "FakeInvalidResource", "FakeInvalidResource", 1);
        final Expr fakeResource = new Expr.Element(resourceToken, null);
        List<Stmt> statements = List.of(new Stmt.Expression(fakeResource));
        when(errorService.hasError()).thenReturn(true);
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(fakeResource, "FakeInvalidResource0.");
        when(fhirEngine.instantiateElement(resourceToken))
                .thenThrow(new RuntimeError(resourceToken, "error message"));

        final Bundle bundle = interpreter.interpret();

        final var entry = bundle.getEntry();
        assertThat(entry.size()).isEqualTo(0);

        verify(errorService, times(1))
                .runtimeError(resourceToken, "error message");
    }

    @Test
    void canInterpretAssignment() {
        // Patient.active="true";
        final Token patientToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final Expr patientResource = new Expr.Element(patientToken, null);
        final Expr activeLiteral = new Expr.Literal("true", null);
        final Token fieldToken = new Token(IDENTIFIER, "active", null, 1);
        List<Stmt> statements = List.of(
                new Stmt.Expression(
                        new Expr.Set(
                                patientResource,
                                fieldToken,
                                activeLiteral, null)
                )
        );
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(patientResource, "Patient0.");
        interpreter.resolveElement(activeLiteral, "Patient0.active0.");
        final Patient patient = new Patient();
        when(fhirEngine.instantiateElement(patientToken)).thenReturn(patient);
        when(fhirEngine.trySetPropertyOnElement(patient, "true", fieldToken, 0))
                .thenReturn(Optional.of(new Object()));

        interpreter.interpret();

        verify(fhirEngine, times(1))
                .trySetPropertyOnElement(patient, "true", fieldToken, 0);
    }

    @Test
    void canInterpretBooleanTypeAssignment() {
        // Patient.active=("yes" as boolean);
        final Token patientToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final Expr patientResource = new Expr.Element(patientToken, null);
        final Expr booleanLiteral = new Expr.Literal("yes", TypeService.Types.BOOLEAN);
        final Token fieldToken = new Token(IDENTIFIER, "active", null, 1);
        List<Stmt> statements = List.of(
                new Stmt.Expression(
                        new Expr.Set(
                                patientResource,
                                fieldToken,
                                booleanLiteral, null)
                )
        );
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(patientResource, "Patient0.");
        interpreter.resolveElement(booleanLiteral, "Patient0.active0.");
        final Patient patient = new Patient();
        when(fhirEngine.instantiateElement(patientToken)).thenReturn(patient);
        when(fhirEngine.setBooleanOnElement(patient, fieldToken, true))
                .thenReturn(Optional.of(new Object()));
        when(typeService.getAsBoolean("yes", null)).thenReturn(true);

        interpreter.interpret();

        verify(fhirEngine, times(1)).setBooleanOnElement(patient, fieldToken, true);
    }

    @Test
    void canInterpretDateTypeAssignment() {
        // Patient.birthDate=("27/8/2020" as date);
        final Token patientToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final Expr patientResource = new Expr.Element(patientToken, null);
        final Expr birthDate = new Expr.Date("27/8/2020", null);
        final Token fieldToken = new Token(IDENTIFIER, "birthDate", null, 1);
        List<Stmt> statements = List.of(
                new Stmt.Expression(new Expr.Set(patientResource, fieldToken, birthDate, null))
        );
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(patientResource, "Patient0.");
        interpreter.resolveElement(birthDate, "Patient0.birthDate0.");
        final Patient patient = new Patient();
        when(fhirEngine.instantiateElement(patientToken)).thenReturn(patient);
        when(fhirEngine.setDateOnElement(eq(patient), eq(fieldToken), any(LocalDate.class)))
                .thenReturn(Optional.of(new Object()));
        when(typeService.getAsLocalDate(null, "27/8/2020", null))
                .thenReturn(LocalDate.of(2020, 8, 27));

        interpreter.interpret();

        verify(fhirEngine, times(1)).setDateOnElement(eq(patient), eq(fieldToken), any());
    }

    @Test
    void canInterpretDecimalTypeAssignment() {
        // Quantity.value=("11.2" as decimal);
        final Token quantityToken = new Token(ELEMENT, "Quantity", "Quantity", 1);
        final Expr quantityResource = new Expr.Element(quantityToken, null);
        final Expr decimalLiteral = new Expr.Literal("11.2", TypeService.Types.DECIMAL);
        final Token fieldToken = new Token(IDENTIFIER, "value", null, 1);
        List<Stmt> statements = List.of(
                new Stmt.Expression(
                        new Expr.Set(
                                quantityResource,
                                fieldToken,
                                decimalLiteral, null)
                )
        );
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(quantityResource, "Quantity0.");
        interpreter.resolveElement(decimalLiteral, "Quantity0.value0.");
        final Quantity quantity = new Quantity();
        when(fhirEngine.instantiateElement(quantityToken)).thenReturn(quantity);
        when(fhirEngine.setDecimalOnElement(quantity, fieldToken, 11.2))
                .thenReturn(Optional.of(new Object()));
        when(typeService.getAsDecimal("11.2", null)).thenReturn(11.2);

        interpreter.interpret();

        verify(fhirEngine, times(1)).setDecimalOnElement(quantity, fieldToken, 11.2);
    }

    @Test
    void canInterpretIntegerTypeAssignment() {
        // Goal.detail=("10" as integer);
        final Token goalToken = new Token(ELEMENT, "Goal", "Goal", 1);
        final Expr goalResource = new Expr.Element(goalToken, null);
        final Expr integerLiteral = new Expr.Literal("10", TypeService.Types.INTEGER);
        final Token fieldToken = new Token(IDENTIFIER, "value", null, 1);
        List<Stmt> statements = List.of(
                new Stmt.Expression(
                        new Expr.Set(
                                goalResource,
                                fieldToken,
                                integerLiteral, null)
                )
        );
        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(goalResource, "Goal0.");
        interpreter.resolveElement(integerLiteral, "Goal0.detail0.");
        final Goal goal = new Goal();
        when(fhirEngine.instantiateElement(goalToken)).thenReturn(goal);
        when(fhirEngine.setIntegerOnElement(goal, fieldToken, 10))
                .thenReturn(Optional.of(new Object()));
        when(typeService.getAsInteger("10", null)).thenReturn(10);

        interpreter.interpret();

        verify(fhirEngine, times(1)).setIntegerOnElement(goal, fieldToken, 10);
    }

    @Test
    void canInterpretResourceAssignment() {
        // Patient.name[0]=HumanName.family="Smith";
        final Token patientToken = new Token(ELEMENT, "Patient", "Patient", 1);
        final Token humanNameToken = new Token(ELEMENT, "HumanName", "HumanName", 1);
        final Expr patientResource = new Expr.Element(patientToken, null);
        final Expr humanNameResource = new Expr.Element(humanNameToken, null);
        final Expr familyIdentifier = new Expr.Literal("Smith", null);
        final Token nameField = new Token(IDENTIFIER, "name", null, 1);
        final Token familyField = new Token(IDENTIFIER, "family", null, 1);
        List<Stmt> statements = List.of(
                new Stmt.Expression(
                        new Expr.Set(
                                patientResource,
                                nameField,
                                new Expr.Set(
                                        humanNameResource,
                                        familyField,
                                        familyIdentifier,
                                        null
                                ),
                                new Expr.Literal(0, null))));

        final var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        // Mock static analysis
        interpreter.resolveElement(patientResource, "Patient0.");
        interpreter.resolveElement(humanNameResource, "Patient0.name0.");
        interpreter.resolveElement(familyIdentifier, "Patient0.name0.family0.");
        final Patient patient = new Patient();
        final HumanName humanName = new HumanName();
        when(fhirEngine.instantiateElement(patientToken)).thenReturn(patient);
        when(fhirEngine.instantiateElement(humanNameToken)).thenReturn(humanName);
        when(fhirEngine.trySetPropertyOnElement(patient, humanName, nameField, 0)).thenReturn(Optional.of(patient));
        when(fhirEngine.trySetPropertyOnElement(humanName, "Smith", familyField, 0)).thenReturn(Optional.of(humanName));

        interpreter.interpret();

        verify(fhirEngine, times(1)).trySetPropertyOnElement(patient, humanName, nameField, 0);
        verify(fhirEngine, times(1)).trySetPropertyOnElement(humanName, "Smith", familyField, 0);
    }
}