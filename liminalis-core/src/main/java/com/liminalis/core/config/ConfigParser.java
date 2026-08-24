package com.liminalis.core.config;

import com.liminalis.core.combat.CombatSettings;
import com.liminalis.core.limbo.GhostVisitSettings;
import com.liminalis.core.limbo.LimboSettings;
import com.liminalis.core.lives.LifeSettings;

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

    /** Bukkit turns a world name into a directory, so keep it to characters every OS likes. */
    private static final String WORLD_NAME_PATTERN = "[a-zA-Z0-9_-]+";

    private ConfigParser() {
    }

    public static ConfigResult parse(Map<String, Object> values) {
        Reader reader = new Reader(values);

        LifeSettings lives = readLives(reader);
        LimboSettings limbo = readLimbo(reader);
        CombatSettings combat = readCombat(reader);

        boolean backupOnStart = reader.flag("storage.backup-on-start",
                LiminalisConfig.DEFAULTS.backupOnStart());
        // At least 1: "back up on start, keep zero of them" is never what anyone meant.
        // Turning backups off is what storage.backup-on-start is for.
        int keepBackups = reader.wholeNumber("storage.keep-backups",
                LiminalisConfig.DEFAULTS.keepBackups(), 1, 1000);
        boolean debug = reader.flag("debug", LiminalisConfig.DEFAULTS.debug());

        if (!reader.errors.isEmpty()) {
            return ConfigResult.failed(reader.errors);
        }
        return ConfigResult.ok(new LiminalisConfig(
                lives, limbo, combat, backupOnStart, keepBackups, debug));
    }

    private static LifeSettings readLives(Reader reader) {
        LifeSettings defaults = LifeSettings.DEFAULTS;
        return new LifeSettings(
                reader.wholeNumber("lives.starting", defaults.startingLives(), 1, 100),
                reader.flag("lives.pvp-deaths-count", defaults.pvpDeathsCount()));
    }

    private static LimboSettings readLimbo(Reader reader) {
        LimboSettings defaults = LimboSettings.DEFAULTS;
        GhostVisitSettings ghostDefaults = defaults.ghostVisit();

        long visitSeconds = reader.wholeNumber("limbo.ghost-visit-seconds",
                (int) (ghostDefaults.visitMillis() / 1000L), 5, 3600);
        long cooldownSeconds = reader.wholeNumber("limbo.ghost-cooldown-seconds",
                (int) (ghostDefaults.cooldownMillis() / 1000L), 0, 86_400);

        return new LimboSettings(
                reader.worldName("limbo.world-name", defaults.worldName()),
                reader.wholeNumber("limbo.border-radius", defaults.borderRadius(), 100, 1_000_000),
                reader.wholeNumber("limbo.revival-lives", defaults.revivalLives(), 1, 100),
                reader.fraction("limbo.whisper-chance", defaults.whisperChance()),
                new GhostVisitSettings(visitSeconds * 1000L, cooldownSeconds * 1000L));
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
            return decimal(path, fallback, MULTIPLIER_MIN, MULTIPLIER_MAX);
        }

        /** A probability. */
        private double fraction(String path, double fallback) {
            return decimal(path, fallback, 0.0, 1.0);
        }

        private double decimal(String path, double fallback, double min, double max) {
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
            if (value < min || value > max) {
                errors.add(path + ": must be between " + min + " and " + max
                        + " but was " + value);
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

        private String worldName(String path, String fallback) {
            Object raw = values.get(path);
            if (raw == null) {
                return fallback;
            }
            if (!(raw instanceof String text) || text.isBlank()) {
                errors.add(path + ": expected a world name but found " + describe(raw));
                return fallback;
            }
            if (!text.matches(WORLD_NAME_PATTERN)) {
                errors.add(path + ": may only contain letters, digits, underscores and"
                        + " hyphens but was '" + text + "'");
                return fallback;
            }
            return text;
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
