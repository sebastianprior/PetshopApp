package com.petshop.app.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ETagsTest {

    @Test
    void theSameDataGivesTheSameQuotedTag() {
        String a = ETags.of(1L, "COMPLETADA", 100.0);
        String b = ETags.of(1L, "COMPLETADA", 100.0);

        assertThat(a).isEqualTo(b).startsWith("\"").endsWith("\"").hasSize(26);
    }

    @Test
    void anyChangeInTheDataChangesTheTag() {
        String base = ETags.of(1L, "COMPLETADA", 100.0);

        assertThat(ETags.of(2L, "COMPLETADA", 100.0)).isNotEqualTo(base);
        assertThat(ETags.of(1L, "ENVIADA", 100.0)).isNotEqualTo(base);
        assertThat(ETags.of(1L, "COMPLETADA", 100.5)).isNotEqualTo(base);
    }

    @Test
    void theOrderAndTheSeparationOfThePartsMatter() {
        assertThat(ETags.of("a", "b")).isNotEqualTo(ETags.of("b", "a"));
        assertThat(ETags.of("ab", "c")).isNotEqualTo(ETags.of("a", "bc"));
        assertThat(ETags.of("a", null)).isNotEqualTo(ETags.of("a", "null"));
    }

    @Test
    void withoutIfMatchOrWithAStarNothingIsStale() {
        String current = ETags.of(1);

        assertThat(ETags.stale(null, current)).isFalse();
        assertThat(ETags.stale("", current)).isFalse();
        assertThat(ETags.stale("   ", current)).isFalse();
        assertThat(ETags.stale("*", current)).isFalse();
        assertThat(ETags.stale(" * ", current)).isFalse();
    }

    @Test
    void aMatchingTagIsNotStaleAndADifferentOneIs() {
        String current = ETags.of(1);

        assertThat(ETags.stale(current, current)).isFalse();
        assertThat(ETags.stale(ETags.of(2), current)).isTrue();
        assertThat(ETags.stale("\"cualquiera\"", current)).isTrue();
    }

    @Test
    void severalValuesAreAcceptedIfAnyMatchesAndTheWeakPrefixIsIgnored() {
        String current = ETags.of(1);

        assertThat(ETags.stale("\"a\", " + current + " , \"b\"", current)).isFalse();
        assertThat(ETags.stale("W/" + current, current)).isFalse();
        assertThat(ETags.stale("\"a\", \"b\"", current)).isTrue();
    }

    @Test
    void notModifiedWhenTheClientAlreadyHasThisVersion() {
        String current = ETags.of(1);

        assertThat(ETags.notModified(null, current)).isFalse();
        assertThat(ETags.notModified("", current)).isFalse();
        assertThat(ETags.notModified(current, current)).isTrue();
        assertThat(ETags.notModified("W/" + current, current)).isTrue();
        assertThat(ETags.notModified("\"viejo\", " + current, current)).isTrue();
        assertThat(ETags.notModified("*", current)).isTrue();
        assertThat(ETags.notModified("\"viejo\"", current)).isFalse();
    }
}
