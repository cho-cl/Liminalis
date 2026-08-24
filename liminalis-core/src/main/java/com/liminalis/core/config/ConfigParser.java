package com.liminalis.core.config;

import com.liminalis.core.combat.CombatSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a flat {@code path -> value} map into a validated {@link LiminalisConfig}.
 *
 * <p>Takes a plain map rather than anything Bukkit-shaped so the rules live in core and can
 * be tested without a server. The plugin hands it {@code YamlConfiguration.getValues(true)};
 * tests hand it a {@link Map#of}.
 *
 * <p>Every setting is read even after an earlier one fails, so a broken config reports all
 * of its problems in one go instead of one per restart.
 */
public final class ConfigParser {

    private static final double MULTIPLIER_MIN = 0.0;
    private static final double MULTIPLIER_MAX = 10.0;

    private ConfigParser() {
    }

    public static ConfigResult parse(Map<String, Object> values) {
        Reader reader = new Reader(values);

        int startingLives = reader.wholeNumber("lives.starting",
                LiminalisConfig.DEFAULTS.startingLives(), 1, 100);
        boolean backupOnStart = reader.flag("storage.backup-on-start",
                LiminalisConfig.DEFAULTS.backupOnStart());
        // At least 1: "back up on start, keep zero of them" is never what anyone meant.
        // Turning backups off is what storage.backup-on-start is for.
        int keepBackups = reader.wholeNumber("storage.keep-backups",
                LiminalisConfig.DEFAULTS.keepBackups(), 1, 1000);
        boolean debug = reader.flag("debug", LiminalisConfig.DEFAULTS.debug());

        CombatSettings combat = readCombat(reader);

        if (!reader.errors.isEmpty()) {
            return ConfigResult.failed(reader.errors);
        }
        return ConfigResult.ok(
                new LiminalisConfig(startingLives, backupOnStart, keepBackups, debug, combat));
    }

    private static CombatSettings readCombat(Reader reader) {
        CombatSettings defaults = CombatSettings.DEFAULTS;
        return new CombatSettings(
                reader.multiplier("combat.pvp-damage-multiplier",
                        defaults.pvpDamageMultiplier()),
                reader.multiplier("combat.food-healing-multiplier",
                        defaults.foodHealingMultiplier()),
                reader.multiplier("combat.regeneration-multiplier",
                        defaults.regenerationMultiplier()),
                reader.flag("combat.include-projectiles", defaults.includeProjectiles()),
                reader.flag("combat.include-pets", defaults.includePets()),
                reader.flag("combat.include-explosives", defaults.includeExplosives()));
    }

    private static final class Reader {

        private final Map<String, Object> values;
        private final List<String> errors = new ArrayList<>();

        private Reader(Map<String, Object> values) {
            this.values = values;
        }

        private int wholeNumber(String path, int fallback, int min, int max) {
            Object raw = values.get(path);
            if (raw == null) {
                return fallback;
            }
            if (!(raw instanceof Number number)) {
                return error(path, "expected a whole number but found " + describe(raw), fallback);
            }
            double asDouble = number.doubleValue();
            if (asDouble != Math.floor(asDouble) || Double.isInfinite(asDouble)) {
                return error(path, "expected a whole number but found " + asDouble, fallback);
            }
            long asLong = (long) asDouble;
            if (asLong < min || asLong > max) {
                return error(path, "must be between " + min + " and " + max
                        + " but was " + asLong, fallback);
            }
            return (int) asLong;
        }

        /**
         * A non-negative scaling factor.
         *
         * <p>Capped well below anything sane on purpose: a stray zero turning 0.5 into 50
         * would make every hit instantly lethal, and that should be caught by the config
         * check rather than by a player dying to it.
         */
        private double multiplier(String path, double fallback) {
            Object raw = values.get(path);
            if (raw == null) {
                return fallback;
            }
            if (!(raw instanceof Number number)) {
                errors.add(path + ": expected a number but found " + describe(raw));
                return fallback;
            }
            double value = number.doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                errors.add(path + ": is not a usable number");
                return fallback;
            }
            if (value < MULTIPLIER_MIN || value > MULTIPLIER_MAX) {
                errors.add(path + ": must be between " + MULTIPLIER_MIN + " and "
                        + MULTIPLIER_MAX + " but was " + value);
                return fallback;
            }
            return value;
        }

        private boolean flag(String path, boolean fallback) {
            Object raw = values.get(path);
            if (raw == null) {
                return fallback;
            }
            if (!(raw instanceof Boolean bool)) {
                error(path, "expected true or false but found " + describe(raw), 0);
                return fallback;
            }
            return bool;
        }

        private int error(String path, String problem, int fallback) {
            errors.add(path + ": " + problem);
            return fallback;
        }

        private static String describe(Object raw) {
            return "'" + raw + "' (" + raw.getClass().getSimpleName() + ")";
        }
    }
}
