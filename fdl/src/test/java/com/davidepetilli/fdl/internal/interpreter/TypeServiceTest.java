package com.davidepetilli.fdl.internal.interpreter;

import com.davidepetilli.fdl.internal.error.RuntimeError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeServiceTest {

    private TypeService typeService;

    @BeforeEach
    void setUp() {
        typeService = new TypeService();
    }

    @Test
    void canInterpretBoolean() {
        var trueString = "tRuE";
        var yesString = "yEs";
        var yString = "y";
        var falseString = "fAlsE";
        var noString = "nO";
        var nString = "n";

        var trueResult = typeService.getAsBoolean(trueString, null);
        var yesResult = typeService.getAsBoolean(yesString, null);
        var yResult = typeService.getAsBoolean(yString, null);
        var falseResult = typeService.getAsBoolean(falseString, null);
        var noResult = typeService.getAsBoolean(noString, null);
        var nResult = typeService.getAsBoolean(nString, null);

        assertThat(trueResult).isTrue();
        assertThat(yesResult).isTrue();
        assertThat(yResult).isTrue();
        assertThat(falseResult).isFalse();
        assertThat(noResult).isFalse();
        assertThat(nResult).isFalse();
    }

    @Test
    void throwsIfBooleanStringIsNotInterpretable() {
        assertThatThrownBy(() -> typeService.getAsBoolean("not a boolean", null))
                .isInstanceOf(RuntimeError.class);
    }


    @ParameterizedTest
    @ValueSource(strings = {
            "8/27/1967",
            "8-27-1967",
            "27-8-1967",
            "Aug 27, 1967",
            "Aug 27 1967",
            "August 27, 1967"})
    void canGuessADateFormat(String date) {
        var result = typeService.getAsLocalDate(null, date, null);

        assertThat(result.getDayOfMonth()).isEqualTo(27);
        assertThat(result.getMonthValue()).isEqualTo(8);
        assertThat(result.getYear()).isEqualTo(1967);
    }

    @Test
    void canParseDateWithFormat() {
        var result = typeService.getAsLocalDate(null, "8/27/1967", "M/dd/yyyy");

        assertThat(result.getDayOfMonth()).isEqualTo(27);
        assertThat(result.getMonthValue()).isEqualTo(8);
        assertThat(result.getYear()).isEqualTo(1967);
    }

    @Test
    void throwsIfDateIsUnrecognized() {
        assertThatThrownBy(() -> typeService.getAsLocalDate(null, "not a date", null))
                .isInstanceOf(RuntimeError.class);
    }
}