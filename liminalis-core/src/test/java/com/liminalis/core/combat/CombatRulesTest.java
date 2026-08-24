package com.liminalis.core.combat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The three world rules that push players toward cooperation: player-versus-player damage is
 * halved, food heals half as much, and regeneration is worth slightly more.
 *
 * <p>The interesting part is not the arithmetic, it is deciding what counts as one player
 * hurting another. An arrow obviously does. A wolf set on someone, or TNT placed under them,
 * is the same act at one remove - and if those are not covered, halving melee damage just
 * teaches people to fight with wolves and TNT instead.
 */
class CombatRulesTest {

    private static final CombatSettings DEFAULTS = CombatSettings.DEFAULTS;

    private static CombatSettings withFlags(boolean projectiles, boolean pets, boolean explosives) {
        return new CombatSettings(0.5, 0.5, 1.25, projectiles, pets, explosives);
    }

    // ------------------------------------------------------------------ player damage

    @Test
    void aDirectHitFromAnotherPlayerIsHalved() {
        assertThat(CombatRules.adjustPlayerDamage(10.0, PlayerDamageSource.DIRECT, DEFAULTS))
                .isCloseTo(5.0, within(1e-9));
    }

    @Test
    void damageNotCausedByAPlayerIsLeftAlone() {
        // A zombie hitting you for 10 must still hit you for 10.
        assertThat(CombatRules.adjustPlayerDamage(10.0, PlayerDamageSource.NONE, DEFAULTS))
                .isCloseTo(10.0, within(1e-9));
    }

    @Test
    void arrowsCountAsPlayerDamageByDefault() {
        assertThat(CombatRules.adjustPlayerDamage(8.0, PlayerDamageSource.PROJECTILE, DEFAULTS))
                .isCloseTo(4.0, within(1e-9));
    }

    @Test
    void projectilesCanBeExcluded() {
        CombatSettings settings = withFlags(false, true, true);

        assertThat(CombatRules.adjustPlayerDamage(8.0, PlayerDamageSource.PROJECTILE, settings))
                .isCloseTo(8.0, within(1e-9));
    }

    @Test
    void aPetAttackingOnItsOwnersBehalfCountsByDefault() {
        assertThat(CombatRules.adjustPlayerDamage(6.0, PlayerDamageSource.PET, DEFAULTS))
                .isCloseTo(3.0, within(1e-9));
    }

    @Test
    void petsCanBeExcluded() {
        CombatSettings settings = withFlags(true, false, true);

        assertThat(CombatRules.adjustPlayerDamage(6.0, PlayerDamageSource.PET, settings))
                .isCloseTo(6.0, within(1e-9));
    }

    @Test
    void explosivesAPlayerSetOffCountByDefault() {
        assertThat(CombatRules.adjustPlayerDamage(20.0, PlayerDamageSource.EXPLOSIVE, DEFAULTS))
                .isCloseTo(10.0, within(1e-9));
    }

    @Test
    void explosivesCanBeExcluded() {
        CombatSettings settings = withFlags(true, true, false);

        assertThat(CombatRules.adjustPlayerDamage(20.0, PlayerDamageSource.EXPLOSIVE, settings))
                .isCloseTo(20.0, within(1e-9));
    }

    @Test
    void aMultiplierOfOneLeavesCombatExactlyAsVanilla() {
        CombatSettings vanilla = new CombatSettings(1.0, 1.0, 1.0, true, true, true);

        assertThat(CombatRules.adjustPlayerDamage(10.0, PlayerDamageSource.DIRECT, vanilla))
                .isCloseTo(10.0, within(1e-9));
    }

    @Test
    void damageIsNeverNegative() {
        // Bukkit will happily heal an entity given negative damage. Never hand it one.
        CombatSettings settings = new CombatSettings(-2.0, 0.5, 1.25, true, true, true);

        assertThat(CombatRules.adjustPlayerDamage(10.0, PlayerDamageSource.DIRECT, settings))
                .isZero();
    }

    // ----------------------------------------------------------------------- healing

    @Test
    void healingFromFoodIsHalved() {
        assertThat(CombatRules.adjustHealing(1.0, HealingKind.FOOD, DEFAULTS))
                .isCloseTo(0.5, within(1e-9));
    }

    @Test
    void regenerationIsWorthSlightlyMore() {
        assertThat(CombatRules.adjustHealing(1.0, HealingKind.REGENERATION, DEFAULTS))
                .isCloseTo(1.25, within(1e-9));
    }

    @Test
    void otherHealingIsLeftAlone() {
        // Instant Health potions, golden apples and the like keep their vanilla value.
        assertThat(CombatRules.adjustHealing(4.0, HealingKind.OTHER, DEFAULTS))
                .isCloseTo(4.0, within(1e-9));
    }

    @Test
    void healingIsNeverNegative() {
        CombatSettings settings = new CombatSettings(0.5, -1.0, 1.25, true, true, true);

        assertThat(CombatRules.adjustHealing(2.0, HealingKind.FOOD, settings)).isZero();
    }
}
