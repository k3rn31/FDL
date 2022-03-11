package com.davidepetilli.fdl.internal.interpreter.fhir;

import com.davidepetilli.fdl.internal.error.RuntimeError;
import com.davidepetilli.fdl.internal.interpreter.TypeService;
import com.davidepetilli.fdl.internal.lexer.Token;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static com.davidepetilli.fdl.internal.lexer.TokenType.ELEMENT;
import static com.davidepetilli.fdl.internal.lexer.TokenType.IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirReflectiveEngineTest {

    private FhirReflectiveEngine engine;

    @Mock
    private TypeService typeService;

    @BeforeEach
    void setUp() {
        engine = new FhirReflectiveEngine(typeService);
    }

    @Test
    void instantiateElementCanInstantiateResource() {
        var resource = engine.instantiateElement(new Token(ELEMENT, "Patient", "Patient", 1));

        assertThat(resource).isInstanceOf(Patient.class);
    }

    @Test
    void instantiateElementThrowsWhenResourceIsNotExistent() {
        assertThatThrownBy(() ->
                engine.instantiateElement(new Token(ELEMENT, "FakeRes", "FakeRes", 1)))
                .isInstanceOf(RuntimeError.class);
    }

    @Test
    void getPropertyFromElementCanGetPropertyValue() {
        var resource = new Patient();
        resource.setActive(true);

        var value = engine.getPropertyFromElement(new Token(IDENTIFIER, "active", "active", 1), resource, null);

        assertThat(value).isInstanceOf(Boolean.class);
        assertThat((Boolean) value).isTrue();
    }

    @Test
    void getPropertyFromElementInstantiatesBackboneElementIfNotPresent() {
        var resource = new Patient();

        var value = engine.getPropertyFromElement(new Token(IDENTIFIER, "contact", "contact", 1), resource, 0);

        assertThat(value).isInstanceOf(BackboneElement.class);
    }

    @Test
    void getPropertyFromElementInstantiatesCanGetCorrectBackboneElement() {
        var resource = new Patient();
        resource.addContact().addTelecom().setValue("123456");

        var value = engine.getPropertyFromElement(new Token(IDENTIFIER, "contact", "contact", 1), resource, 0);

        assertThat(value).isInstanceOf(Patient.ContactComponent.class);
        var contactComponent = (Patient.ContactComponent) value;
        assertThat(contactComponent.getTelecom().get(0)).isInstanceOf(ContactPoint.class);
        var contactPoint = (ContactPoint) contactComponent.getTelecom().get(0);
        assertThat(contactPoint.getValue()).isEqualTo("123456");
    }

    @Test
    void getPropertyFromElementThrowsWhenPropertyDoNotExists() {
        var resource = new Patient();
        assertThatThrownBy(() ->
                engine.getPropertyFromElement(new Token(IDENTIFIER, "fakeProp", "fakeProp", 1), resource, null))
                .isInstanceOf(RuntimeError.class);
    }

    @Test
    void setPropertyOnElementCanAddToAddableElement() {
        var humanName = new HumanName();
        var fieldToken = new Token(IDENTIFIER, "given", "given", 1);

        var object = engine.trySetPropertyOnElement(humanName, "testName", fieldToken);
        var result = object.orElseThrow();

        assertThat(result).isSameAs(humanName);
        assertThat(((HumanName) result).getGiven().get(0).toString()).isEqualTo("testName");
    }

    @Test
    void setPropertyOnElementCanAddRequiredValue() {
        var immunization = new Immunization();
        var fieldToken = new Token(IDENTIFIER, "status", "status", 1);

        var object = engine.trySetPropertyOnElement(immunization, "completed", fieldToken);
        var result = object.orElseThrow();

        assertThat(result).isSameAs(immunization);
        assertThat(((Immunization) result).getStatus()).isEqualTo(Immunization.ImmunizationStatus.COMPLETED);
    }

    @Test
    void setPropertyOnElementThrowsWhenValueIsNotValid() {
        var immunization = new Immunization();
        var fieldToken = new Token(IDENTIFIER, "status", "status", 1);

        assertThatThrownBy(() ->
                engine.trySetPropertyOnElement(immunization, "invalidValue", fieldToken))
                .isInstanceOf(RuntimeError.class);
    }

    @Test
    void setPropertyOnElementCanAddAnElementToAnEmptyExistingCollection() {
        var patient = new Patient();
        var humanName = new HumanName();
        var fieldToken = new Token(IDENTIFIER, "name", "name", 1);

        var object = engine.trySetPropertyOnElement(patient, humanName, fieldToken, 0);
        var result = object.orElseThrow();

        assertThat(((Patient) result).getName().get(0)).isSameAs(humanName);
    }

    @Test
    void setPropertyOnElementCanOverwriteElementInExistingCollection() {
        var patient = new Patient();
        var humanName1 = new HumanName();
        var humanName2 = new HumanName();
        var fieldToken = new Token(IDENTIFIER, "name", "name", 1);

        engine.trySetPropertyOnElement(patient, humanName1, fieldToken, 0);
        var object = engine.trySetPropertyOnElement(patient, humanName2, fieldToken, 0);
        var result = (Patient) object.orElseThrow();

        assertThat(result.getName().size()).isEqualTo(1);
        assertThat(result.getName().get(0)).isSameAs(humanName2);
    }

    @Test
    void setPropertyOnElementCanAppendElementInExistingCollection() {
        var patient = new Patient();
        var humanName1 = new HumanName();
        var humanName2 = new HumanName();
        var fieldToken = new Token(IDENTIFIER, "name", "name", 1);

        engine.trySetPropertyOnElement(patient, humanName1, fieldToken, 0);
        var object = engine.trySetPropertyOnElement(patient, humanName2, fieldToken, 1);
        var result = (Patient) object.orElseThrow();

        assertThat(result.getName().size()).isEqualTo(2);
        assertThat(result.getName().get(0)).isSameAs(humanName1);
        assertThat(result.getName().get(1)).isSameAs(humanName2);
    }

    @Test
    void setPropertyOnElementThrowsIfIndexIsOutOfSequence() {
        var patient = new Patient();
        var humanName = new HumanName();
        var fieldToken = new Token(IDENTIFIER, "name", "name", 1);

        assertThatThrownBy(() -> engine.trySetPropertyOnElement(patient, humanName, fieldToken, 1))
                .isInstanceOf(RuntimeError.class);
    }

    @Test
    void setPropertyOnElementCanSetValueOnElement() {
        var humanName = new HumanName();
        var fieldToken = new Token(IDENTIFIER, "family", "family", 1);

        var result = engine.trySetPropertyOnElement(humanName, "Jacobs", fieldToken);
        var name = (HumanName) result.orElseThrow();

        assertThat(name.getFamily()).isEqualTo("Jacobs");
    }

    @Test
    void setPropertyOnElementCanSetBooleanValueWithVariousSyntax() {
        var patientTrue = new Patient();
        var patientYes = new Patient();
        var patientY = new Patient();
        var patientFalse = new Patient();
        var patientNo = new Patient();
        var patientN = new Patient();
        var fieldToken = new Token(IDENTIFIER, "active", "active", 1);
        when(typeService.getAsBoolean(
                argThat(s -> List.of("true", "yes", "y").contains(s.toLowerCase())),
                eq(fieldToken))
        ).thenReturn(true);

        var result = engine.trySetPropertyOnElement(patientTrue, "tRue", fieldToken);
        var activeTrue = (Patient) result.orElseThrow();
        result = engine.trySetPropertyOnElement(patientYes, "YeS", fieldToken);
        var activeYes = (Patient) result.orElseThrow();
        result = engine.trySetPropertyOnElement(patientY, "y", fieldToken);
        var activeY = (Patient) result.orElseThrow();
        result = engine.trySetPropertyOnElement(patientFalse, "fAlse", fieldToken);
        var activeFalse = (Patient) result.orElseThrow();
        result = engine.trySetPropertyOnElement(patientNo, "nO", fieldToken);
        var activeNo = (Patient) result.orElseThrow();
        result = engine.trySetPropertyOnElement(patientN, "N", fieldToken);
        var activeN = (Patient) result.orElseThrow();

        assertThat(activeTrue.getActive()).isTrue();
        assertThat(activeYes.getActive()).isTrue();
        assertThat(activeY.getActive()).isTrue();
        assertThat(activeFalse.getActive()).isFalse();
        assertThat(activeNo.getActive()).isFalse();
        assertThat(activeN.getActive()).isFalse();
    }

    @Test
    void setPropertyOnElementThrowsIfNotValidBoolean() {
        var patient = new Patient();
        var fieldToken = new Token(IDENTIFIER, "active", "active", 1);
        when(typeService.getAsBoolean("notBool", fieldToken))
                .thenThrow(new RuntimeError(fieldToken, ""));

        assertThatThrownBy(() -> engine.trySetPropertyOnElement(patient, "notBool", fieldToken))
                .isInstanceOf(RuntimeError.class);
    }

    @Test
    void setPropertyOnElementThrowsWhenDateFormatIsNotRecognized() {
        var patient = new Patient();
        var fieldToken = new Token(IDENTIFIER, "birthDate", "birthDate", 1);
        when(typeService.getAsLocalDate(fieldToken, "not a date", null))
                .thenThrow(new RuntimeError(fieldToken, ""));

        assertThatThrownBy(() -> engine.trySetPropertyOnElement(patient, "not a date", fieldToken))
                .isInstanceOf(RuntimeError.class);
    }

    @Test
    void setDateCanSetADateWithFormat() {
        var patient = new Patient();
        var fieldToken = new Token(IDENTIFIER, "birthDate", "birthDate", 1);

        var date = LocalDate.now();
        var result = engine.setDateOnElement(patient, fieldToken, date);
        var object = (Patient) result.orElseThrow();

        assertThat(object.getBirthDateElement().getDay()).isEqualTo(date.getDayOfMonth());
        assertThat(object.getBirthDateElement().getMonth() + 1).isEqualTo(date.getMonthValue());
        assertThat(object.getBirthDateElement().getYear()).isEqualTo(date.getYear());
    }

    @Test
    void setCanSetABoolean() {
        var patient = new Patient();
        var fieldToken = new Token(IDENTIFIER, "active", "active", 1);

        var result = engine.setBooleanOnElement(patient, fieldToken, true);
        var object = (Patient) result.orElseThrow();

        assertThat(object.getActive()).isTrue();
    }

    @Test
    void setDecimalCanSetADecimal() {
        var quantity = new Quantity();
        var fieldToken = new Token(IDENTIFIER, "value", "value", 1);

        var result = engine.setDecimalOnElement(quantity, fieldToken, 11.2);
        var object = (Quantity) result.orElseThrow();

        assertThat(object.getValue().doubleValue()).isEqualTo(11.2);
    }

    @Test
    void setIntegerCanSetADInteger() {
        var target = new Goal.GoalTargetComponent();
        var fieldToken = new Token(IDENTIFIER, "detail", "detail", 1);

        var result = engine.setIntegerOnElement(target, fieldToken, 12);
        var object = (Goal.GoalTargetComponent) result.orElseThrow();

        var integer = (IntegerType) object.getDetail();
        assertThat(integer.getValue()).isEqualTo(12);
    }
}