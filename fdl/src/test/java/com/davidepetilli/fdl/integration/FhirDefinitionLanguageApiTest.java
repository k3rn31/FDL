package com.davidepetilli.fdl.integration;

import com.davidepetilli.fdl.api.FhirDefinitionLanguageAPI;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FhirDefinitionLanguageApiTest extends BaseIntegrationTest {

    @Test
    void testSpitFHIRWrapper() {
        var statements = Map.of(
                "Patient.name=HumanName[0].family=$;", "Smith",
                "Patient.name=HumanName[0].given=$;", "Stan",
                "Immunization.vaccineCode=CodeableConcept.text=$;", "AstraZeneca"
        );
        var api = new FhirDefinitionLanguageAPI();
        var bundleFuture = api.processBundle(statements);
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() ->
                bundleFuture.thenAccept(b -> assertThat(b.getEntry().size()).isEqualTo(2)));
    }

    @Test
    void testWrapper() {
        var source = getSourceFromFile("test_statements.fdl");
        var api = new FhirDefinitionLanguageAPI();

        var bundleFuture = api.processBundle(source);
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() ->
                bundleFuture.thenAccept(b -> assertThat(b.getEntry().size()).isEqualTo(3)));
    }
}