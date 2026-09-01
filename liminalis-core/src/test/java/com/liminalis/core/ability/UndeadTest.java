package com.liminalis.core.ability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What Holy Smite is allowed to burn.
 *
 * <p>Worth a test purely because the version this replaced was wrong in the quietest possible
 * way: it asked the server for a mob's category, got an answer that excluded every zombie,
 * and produced a power that politely refused every single time it was used.
 */
class UndeadTest {

    @Test
    void theObviousOnesAreUndead() {
        assertThat(Undead.is("ZOMBIE")).isTrue();
        assertThat(Undead.is("SKELETON")).isTrue();
        assertThat(Undead.is("HUSK")).isTrue();
        assertThat(Undead.is("DROWNED")).isTrue();
        assertThat(Undead.is("WITHER_SKELETON")).isTrue();
        assertThat(Undead.is("PHANTOM")).isTrue();
    }

    @Test
    void theOnesAddedSinceTheAbilityWasWrittenAreToo() {
        // Bogged arrived in 1.21 and would have been missed by anybody listing these from
        // memory, which is most of the argument for the list being somewhere testable.
        assertThat(Undead.is("BOGGED")).isTrue();
        assertThat(Undead.is("ZOGLIN")).isTrue();
        assertThat(Undead.is("ZOMBIFIED_PIGLIN")).isTrue();
    }

    @Test
    void theLivingAreNot() {
        assertThat(Undead.is("CREEPER")).isFalse();
        assertThat(Undead.is("SPIDER")).isFalse();
        assertThat(Undead.is("PLAYER")).isFalse();
        assertThat(Undead.is("COW")).isFalse();
        assertThat(Undead.is("ENDERMAN")).isFalse();
        assertThat(Undead.is("BEE")).isFalse();
    }

    @Test
    void aDroneIsNeverUndead() {
        // A priest with drones out should not be able to burn their own swarm.
        assertThat(Undead.is("BEE")).isFalse();
    }

    @Test
    void nothingIsNotUndead() {
        assertThat(Undead.is(null)).isFalse();
        assertThat(Undead.is("")).isFalse();
    }

    @Test
    void theListIsNotEmpty() {
        // The failure this exists to prevent is a check that matches nothing at all.
        assertThat(Undead.TYPES).isNotEmpty();
        assertThat(Undead.TYPES).hasSizeGreaterThan(10);
    }
}
