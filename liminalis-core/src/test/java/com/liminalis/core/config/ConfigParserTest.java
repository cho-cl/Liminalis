package com.liminalis.core.config;

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
        assertThat(config.startingLives()).isEqualTo(5);
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
        assertThat(result.config().startingLives()).isEqualTo(3);
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
}
