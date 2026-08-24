package com.liminalis.core.config;

import com.liminalis.core.combat.CombatSettings;
import com.liminalis.core.limbo.LimboSettings;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The config is where every balance number lives, and it gets edited on a live server at
 * odd hours. These tests pin down the behaviour that matters there: a typo must be reported
 * clearly and must not quietly become a default, because a silently defaulted probability
 * is a bug nobody will ever notice.
 */
class ConfigParserTest {

    private static Map<String, Object> valid() {
        Map<String, Object> values = new HashMap<>();
        values.put("lives.starting", 3);
        values.put("storage.backup-on-start", true);
        values.put("storage.keep-backups", 10);
        values.put("debug", false);
        return values;
    }

    @Test
    void readsAFullySpecifiedConfig() {
        Map<String, Object> values = valid();
        values.put("lives.starting", 5);
        values.put("storage.keep-backups", 3);
        values.put("debug", true);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isTrue();
        LiminalisConfig config = result.config();
        assertThat(config.lives().startingLives()).isEqualTo(5);
        assertThat(config.backupOnStart()).isTrue();
        assertThat(config.keepBackups()).isEqualTo(3);
        assertThat(config.debug()).isTrue();
    }

    @Test
    void absentKeysFallBackToDefaults() {
        // Unlike a profile, a missing config key is safe to default: the file is ours, it
        // ships with the plugin, and a new release adding a key must not break startup.
        ConfigResult result = ConfigParser.parse(Map.of());

        assertThat(result.valid()).isTrue();
        assertThat(result.config()).isEqualTo(LiminalisConfig.DEFAULTS);
    }

    @Test
    void reportsAValueOfTheWrongType() {
        Map<String, Object> values = valid();
        values.put("lives.starting", "three");

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("lives.starting"));
    }

    @Test
    void reportsAValueOutsideItsAllowedRange() {
        Map<String, Object> values = valid();
        values.put("lives.starting", 0);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("lives.starting"));
    }

    @Test
    void reportsEveryProblemAtOnceRatherThanStoppingAtTheFirst() {
        // Fixing a broken config one restart at a time is miserable. Report the lot.
        Map<String, Object> values = valid();
        values.put("lives.starting", -1);
        values.put("storage.keep-backups", -5);
        values.put("debug", "yes please");

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(3);
    }

    @Test
    void producesNoConfigWhenInvalidSoCallersCannotUseItByAccident() {
        Map<String, Object> values = valid();
        values.put("lives.starting", 0);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.config()).isNull();
    }

    @Test
    void acceptsWholeNumbersSuppliedAsOtherNumericTypes() {
        // YAML parsers are inconsistent about int vs long vs double for plain integers.
        Map<String, Object> values = valid();
        values.put("lives.starting", 3L);
        values.put("storage.keep-backups", 10.0);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isTrue();
        assertThat(result.config().lives().startingLives()).isEqualTo(3);
        assertThat(result.config().keepBackups()).isEqualTo(10);
    }

    @Test
    void rejectsAFractionalValueForAWholeNumberSetting() {
        Map<String, Object> values = valid();
        values.put("lives.starting", 3.5);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("lives.starting"));
    }

    // ------------------------------------------------------------------ combat section

    @Test
    void readsTheCombatSection() {
        Map<String, Object> values = valid();
        values.put("combat.pvp-damage-multiplier", 0.25);
        values.put("combat.food-healing-multiplier", 0.75);
        values.put("combat.regeneration-multiplier", 1.5);
        values.put("combat.include-projectiles", false);
        values.put("combat.include-pets", false);
        values.put("combat.include-explosives", false);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isTrue();
        CombatSettings combat = result.config().combat();
        assertThat(combat.pvpDamageMultiplier()).isEqualTo(0.25);
        assertThat(combat.foodHealingMultiplier()).isEqualTo(0.75);
        assertThat(combat.regenerationMultiplier()).isEqualTo(1.5);
        assertThat(combat.includeProjectiles()).isFalse();
        assertThat(combat.includePets()).isFalse();
        assertThat(combat.includeExplosives()).isFalse();
    }

    @Test
    void anAbsentCombatSectionUsesTheDefaults() {
        ConfigResult result = ConfigParser.parse(valid());

        assertThat(result.valid()).isTrue();
        assertThat(result.config().combat()).isEqualTo(CombatSettings.DEFAULTS);
    }

    @Test
    void acceptsAWholeNumberWhereADecimalIsExpected() {
        // YAML gives back an Integer for "1", not a Double. Refusing that would be absurd.
        Map<String, Object> values = valid();
        values.put("combat.pvp-damage-multiplier", 1);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isTrue();
        assertThat(result.config().combat().pvpDamageMultiplier()).isEqualTo(1.0);
    }

    @Test
    void rejectsANegativeMultiplier() {
        // Negative damage heals in Bukkit. This must never reach the server.
        Map<String, Object> values = valid();
        values.put("combat.pvp-damage-multiplier", -0.5);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("combat.pvp-damage-multiplier"));
    }

    @Test
    void rejectsAMultiplierThatIsNotANumber() {
        Map<String, Object> values = valid();
        values.put("combat.regeneration-multiplier", "slightly more");

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("combat.regeneration-multiplier"));
    }

    @Test
    void rejectsAnAbsurdlyLargeMultiplier() {
        // A stray zero turning 0.5 into 50 would make a single hit lethal to anyone.
        Map<String, Object> values = valid();
        values.put("combat.pvp-damage-multiplier", 50.0);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
    }

    // ------------------------------------------------------------------- limbo section

    @Test
    void readsTheLimboSection() {
        Map<String, Object> values = valid();
        values.put("limbo.world-name", "the_grey");
        values.put("limbo.border-radius", 2000);
        values.put("limbo.revival-lives", 1);
        values.put("limbo.whisper-chance", 0.4);
        values.put("limbo.ghost-visit-seconds", 120);
        values.put("limbo.ghost-cooldown-seconds", 600);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isTrue();
        LimboSettings limbo = result.config().limbo();
        assertThat(limbo.worldName()).isEqualTo("the_grey");
        assertThat(limbo.borderRadius()).isEqualTo(2000);
        assertThat(limbo.revivalLives()).isEqualTo(1);
        assertThat(limbo.whisperChance()).isEqualTo(0.4);
        assertThat(limbo.ghostVisit().visitMillis()).isEqualTo(120_000L);
        assertThat(limbo.ghostVisit().cooldownMillis()).isEqualTo(600_000L);
    }

    @Test
    void ghostTimesAreGivenInSecondsAndStoredInMillis() {
        // The file talks in seconds because that is what a human wants to type; everything
        // downstream works in millis. Getting this conversion wrong by 1000 would turn a
        // five minute visit into five seconds.
        ConfigResult result = ConfigParser.parse(valid());

        assertThat(result.config().limbo().ghostVisit().visitMillis()).isEqualTo(300_000L);
        assertThat(result.config().limbo().ghostVisit().cooldownMillis()).isEqualTo(900_000L);
    }

    @Test
    void rejectsAWorldNameThatWouldNotSurviveBeingADirectory() {
        Map<String, Object> values = valid();
        values.put("limbo.world-name", "../../etc/passwd");

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("limbo.world-name"));
    }

    @Test
    void rejectsAWhisperChanceOutsideZeroToOne() {
        Map<String, Object> values = valid();
        values.put("limbo.whisper-chance", 1.5);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("limbo.whisper-chance"));
    }

    @Test
    void rejectsReturningFromLimboWithNoLives() {
        // Coming back with zero lives means the next scratch sends you straight back.
        Map<String, Object> values = valid();
        values.put("limbo.revival-lives", 0);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("limbo.revival-lives"));
    }

    // ------------------------------------------------------------------ traits section

    @Test
    void readsTheTraitRollChances() {
        Map<String, Object> values = valid();
        values.put("traits.second-trait-chance", 0.4);
        values.put("traits.singularity-chance", 0.01);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isTrue();
        assertThat(result.config().traits().roll().secondTraitChance()).isEqualTo(0.4);
        assertThat(result.config().traits().roll().singularityChance()).isEqualTo(0.01);
    }

    @Test
    void collectsTraitTuningNumbersWithoutKnowingWhatTheyAre() {
        // The whole point of the tuning map: a new trait's numbers can be added here without
        // the parser ever being taught that the trait exists.
        Map<String, Object> values = valid();
        values.put("traits.tuning.resilience.max-armor", 12.0);
        values.put("traits.tuning.something.invented.later", 3);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isTrue();
        assertThat(result.config().traits().tuning())
                .containsEntry("resilience.max-armor", 12.0)
                .containsEntry("something.invented.later", 3.0);
    }

    @Test
    void rejectsATraitTuningValueThatIsNotANumber() {
        Map<String, Object> values = valid();
        values.put("traits.tuning.resilience.max-armor", "lots");

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("traits.tuning.resilience.max-armor"));
    }

    @Test
    void anAbsentTraitsSectionUsesTheDefaults() {
        ConfigResult result = ConfigParser.parse(valid());

        assertThat(result.valid()).isTrue();
        assertThat(result.config().traits()).isEqualTo(TraitSettings.DEFAULTS);
    }

    @Test
    void rejectsASingularityChanceAboveOne() {
        Map<String, Object> values = valid();
        values.put("traits.singularity-chance", 2.0);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("traits.singularity-chance"));
    }

    // ------------------------------------------------------------------- boons section

    @Test
    void readsTheBlessingAndCurseChances() {
        Map<String, Object> values = valid();
        values.put("boons.blessing-chance", 0.2);
        values.put("boons.curse-chance", 0.1);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isTrue();
        assertThat(result.config().boons().blessingChance()).isEqualTo(0.2);
        assertThat(result.config().boons().curseChance()).isEqualTo(0.1);
    }

    @Test
    void rejectsBlessingAndCurseChancesThatCannotBothFit() {
        // They are exclusive slices of one roll. Above 1.0 together, one of them is being
        // silently squeezed out, and the configured rate would be a lie.
        Map<String, Object> values = valid();
        values.put("boons.blessing-chance", 0.7);
        values.put("boons.curse-chance", 0.6);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("boons.blessing-chance"));
    }

    @Test
    void acceptsChancesThatExactlyFill() {
        Map<String, Object> values = valid();
        values.put("boons.blessing-chance", 0.5);
        values.put("boons.curse-chance", 0.5);

        assertThat(ConfigParser.parse(values).valid()).isTrue();
    }

    // ---------------------------------------------------------------- injuries section

    @Test
    void readsTheInjurySection() {
        Map<String, Object> values = valid();
        values.put("injuries.injury-threshold", 0.3);
        values.put("injuries.injury-chance", 0.5);
        values.put("injuries.mortal-threshold", 0.7);
        values.put("injuries.mortal-chance", 0.2);
        values.put("injuries.regeneration-speedup", 2.0);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isTrue();
        assertThat(result.config().injuries().injuryThreshold()).isEqualTo(0.3);
        assertThat(result.config().injuries().mortalChance()).isEqualTo(0.2);
        assertThat(result.config().injuries().regenerationSpeedup()).isEqualTo(2.0);
    }

    @Test
    void rejectsAMortalThresholdBelowTheInjuryThreshold() {
        // Otherwise the worst wounds would be reachable by lighter blows than ordinary ones,
        // and every large hit would maim rather than injure.
        Map<String, Object> values = valid();
        values.put("injuries.injury-threshold", 0.6);
        values.put("injuries.mortal-threshold", 0.2);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("injuries.mortal-threshold"));
    }

    @Test
    void acceptsThresholdsThatAreEqual() {
        Map<String, Object> values = valid();
        values.put("injuries.injury-threshold", 0.5);
        values.put("injuries.mortal-threshold", 0.5);

        assertThat(ConfigParser.parse(values).valid()).isTrue();
    }

    @Test
    void pvpDeathsCanBeSwitchedOff() {
        Map<String, Object> values = valid();
        values.put("lives.pvp-deaths-count", false);

        ConfigResult result = ConfigParser.parse(values);

        assertThat(result.valid()).isTrue();
        assertThat(result.config().lives().pvpDeathsCount()).isFalse();
    }
}
