package com.davidepetilli.fdl.integration;

import com.davidepetilli.fdl.internal.error.ErrorService;
import com.davidepetilli.fdl.internal.interpreter.TypeService;
import com.davidepetilli.fdl.internal.interpreter.fhir.FhirInterpreter;
import com.davidepetilli.fdl.internal.interpreter.fhir.FhirReflectiveEngine;
import com.davidepetilli.fdl.internal.interpreter.fhir.StaticAnalyzer;
import com.davidepetilli.fdl.internal.lexer.Lexer;
import com.davidepetilli.fdl.internal.parser.Parser;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class FdlIntegrationTest extends BaseIntegrationTest {

    private TypeService typeService;
    private FhirReflectiveEngine fhirEngine;

    @BeforeEach
    void setUp() {
        typeService = new TypeService();
        fhirEngine = new FhirReflectiveEngine(typeService);
    }

    @Test
    void canGenerateABasicResource() {
        var source = "Patient;";

        var errorService = new ErrorService();
        var lexer = new Lexer(source, errorService);
        var tokens = lexer.scanTokens();
        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();
        var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        var staticAnalyzer = new StaticAnalyzer(interpreter);
        staticAnalyzer.resolve(statements);

        var bundle = interpreter.interpret();

        assertThat(errorService.hasError()).isFalse();
        assertThat(bundle).isNotNull();
        var entry = bundle.getEntry();
        assertThat(entry.size()).isEqualTo(1);
        var patient = entry.get(0).getResource();
        assertThat(patient).isInstanceOf(Patient.class);
    }

    @Test
    void canGenerateMultipleResourcesWithMixedMatchers() {
        // new Patient                            +1
        // not new Patient
        // new Patient                            +1
        // new Patient                            +1
        // not new Patient
        // not new Patient (same as Patient[2])
        // new Patient                            +1
        // not new Patient
        //                               TOTAL ->  4
        var source = """
                Patient;
                Patient[0];
                Patient[1];
                Patient[2];
                Patient[2];
                Patient["2"];
                Patient["patient2"];
                Patient["patient2"];
                """;

        var errorService = new ErrorService();
        var lexer = new Lexer(source, errorService);
        var tokens = lexer.scanTokens();
        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();
        var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        var staticAnalyzer = new StaticAnalyzer(interpreter);
        staticAnalyzer.resolve(statements);

        var bundle = interpreter.interpret();

        assertThat(errorService.hasError()).isFalse();
        assertThat(bundle).isNotNull();
        var entry = bundle.getEntry();
        assertThat(entry.size()).isEqualTo(4);
    }

    @Test
    void lexerReportsErrorsCorrectly() {
        var source = "Patient.first本 = \"Unterminated";
        var errorService = new ErrorService();
        var lexer = new Lexer(source, errorService);

        lexer.scanTokens();

        assertThat(errorService.hasError()).isTrue();
        assertThat(errorService.staticErrors().size()).isEqualTo(2);
    }

    /**
     * This test loads a file containing lots of FDL statements and combinations, and verifies that everything is
     * lexed, parsed, analyzed and interpreted correctly. The file does not contain errors.
     */
    @Test
    void canProcessMultipleStatements() {
        // Prepare ------------------------------------------------------------
        String source = getSourceFromFile("test_statements.fdl");
        var errorService = new ErrorService();

        // Execute preparatory ------------------------------------------------
        var lexer = new Lexer(source, errorService);
        var tokens = lexer.scanTokens();
        assertError(errorService);

        var parser = new Parser(tokens, errorService);
        var statements = parser.parse();
        assertError(errorService);

        var interpreter = new FhirInterpreter(statements, fhirEngine, typeService, errorService);
        var staticAnalyzer = new StaticAnalyzer(interpreter);
        staticAnalyzer.resolve(statements);
        assertError(errorService);

        // Execute main interpretation ----------------------------------------
        var bundle = interpreter.interpret();
        assertError(errorService);

        // Verify -------------------------------------------------------------
        assertThat(bundle).isNotNull();
        var entry = bundle.getEntry();
        var resources = entry.stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .collect(toList());

        assertThat(resources.size()).isEqualTo(7);

        assertThat(resources).extracting(Resource::fhirType)
                .containsExactly(
                        "Goal", "Goal", "Immunization", "Observation", "Observation", "Patient", "Patient");

        record PatientWrapper(HumanName name, Date birthDate, boolean active, List<Patient.ContactComponent> contact) {
        }
        record GoalWrapper(String lifecycicleStatus) {
        }

        assertThat(resources)
                .filteredOn(r -> r instanceof Goal)
                .map(r -> (Goal) r)
                .extracting(
                        Goal::getLifecycleStatus,
                        goal -> goal.getDescription().getText(),
                        g -> g.getTarget().get(0).getMeasure().getText(),
                        g -> ((IntegerType) g.getTarget().get(0).getDetail()).getValue())
                .containsExactlyInAnyOrder(
                        tuple(Goal.GoalLifecycleStatus.COMPLETED, "Add additional fingers", "Number of fingers", 7),
                        tuple(Goal.GoalLifecycleStatus.PROPOSED, "Add additional eyes", "Number of eyes", 3)
                );

        assertThat(resources)
                .filteredOn(r -> r instanceof Immunization)
                .map(i -> (Immunization) i)
                .extracting(
                        Immunization::getStatus,
                        i -> i.getVaccineCode().getText(),
                        i -> i.getVaccineCode().getCoding().get(0).getCode())
                .containsExactlyInAnyOrder(tuple(Immunization.ImmunizationStatus.COMPLETED, "AstraZeneca", "55423-8"));

        assertThat(resources)
                .filteredOn(r -> r instanceof Observation)
                .map(o -> (Observation) o)
                .extracting(
                        Observation::getStatus,
                        o -> ((Quantity) o.getValue()).getValue().doubleValue()
                )
                .containsExactlyInAnyOrder(
                        tuple(Observation.ObservationStatus.REGISTERED, 11.2),
                        tuple(Observation.ObservationStatus.PRELIMINARY, 27.6)
                );

        assertThat(resources)
                .filteredOn(r -> r instanceof Patient)
                .map(r -> {
                    var p = (Patient) r;
                    return new PatientWrapper(p.getName().get(0), p.getBirthDate(), p.getActive(), p.getContact());
                })
                .extracting(
                        pw -> pw.name().getFamily(),
                        pw -> {
                            var builder = new StringBuilder();
                            for (var name : pw.name().getGiven()) {
                                builder.append(name.toString());
                            }
                            return builder.toString();
                        },
                        pw -> pw.birthDate() != null ? new SimpleDateFormat("dd-MM-yyyy").format(pw.birthDate()) : null,
                        PatientWrapper::active,
                        pw -> pw.contact().stream()
                                .map(Patient.ContactComponent::getTelecom)
                                .flatMap(cp -> cp.stream().map(cpp -> cpp.getUse() + " " + cpp.getValue()))
                                .findFirst().orElse(""))
                .containsExactlyInAnyOrder(
                        tuple("Jacobs", "LamontMarcel", "26-09-1994", true, ""),
                        tuple("北野", "武", "18-01-1947", false, "HOME 12345678"));
    }
}