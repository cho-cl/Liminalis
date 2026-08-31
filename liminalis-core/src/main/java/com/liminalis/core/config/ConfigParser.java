package com.liminalis.core.config;

import com.liminalis.core.ability.AbilitySettings;
import com.liminalis.core.combat.CombatSettings;
import com.liminalis.core.injury.InjurySettings;
import com.liminalis.core.limbo.GhostVisitSettings;
import com.liminalis.core.limbo.LimboSettings;
import com.liminalis.core.lives.LifeSettings;
import com.liminalis.core.rescue.RescueSettings;
import com.liminalis.core.roll.BoonRollSettings;
import com.liminalis.core.roll.TraitRollSettings;
import com.liminalis.core.singularity.SingularitySettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        TraitSettings traits = readTraits(reader);
        BoonRollSettings boons = readBoons(reader);
        InjurySettings injuries = readInjuries(reader);
        SingularitySettings singularity = readSingularity(reader);
        AbilitySettings abilities = readAbilities(reader);
        RescueSettings rescue = new RescueSettings(reader.wholeNumber(
                "rescue.crossing-seconds",
                (int) RescueSettings.DEFAULTS.crossingSeconds(), 30, 3600));

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
                lives, limbo, combat, traits, boons, injuries, singularity, rescue,
                abilities, backupOnStart, keepBackups, debug));
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

    private static TraitSettings readTraits(Reader reader) {
        TraitRollSettings defaults = TraitRollSettings.DEFAULTS;
        return new TraitSettings(
                new TraitRollSettings(
                        reader.fraction("traits.second-trait-chance",
                                defaults.secondTraitChance()),
                        reader.fraction("traits.singularity-chance",
                                defaults.singularityChance())),
                reader.numbersUnder("traits.tuning."));
    }

    /**
     * Blessing and curse chances.
     *
     * <p>The two are checked together as well as individually: they are mutually exclusive
     * slices of one roll, so a pair that sums above 1.0 is not a config anyone meant to
     * write, and silently clamping it would make the curse rate a lie.
     */
    private static BoonRollSettings readBoons(Reader reader) {
        BoonRollSettings defaults = BoonRollSettings.DEFAULTS;
        double blessing = reader.fraction("boons.blessing-chance", defaults.blessingChance());
        double curse = reader.fraction("boons.curse-chance", defaults.curseChance());

        if (blessing + curse > 1.0) {
            reader.errors.add("boons.blessing-chance + boons.curse-chance: must not exceed"
                    + " 1.0 together, but they add up to " + (blessing + curse));
        }
        return new BoonRollSettings(blessing, curse);
    }

    /**
     * When a blow wounds, and how likely it is to.
     *
     * <p>The mortal threshold is checked against the injury one: a mortal threshold below
     * the injury threshold would mean the worst wounds became reachable by lighter blows
     * than ordinary ones, which is not a config anyone would write on purpose.
     */
    private static InjurySettings readInjuries(Reader reader) {
        InjurySettings defaults = InjurySettings.DEFAULTS;
        double injuryThreshold = reader.fraction("injuries.injury-threshold",
                defaults.injuryThreshold());
        double mortalThreshold = reader.fraction("injuries.mortal-threshold",
                defaults.mortalThreshold());

        if (mortalThreshold < injuryThreshold) {
            reader.errors.add("injuries.mortal-threshold: must be at least"
                    + " injuries.injury-threshold (" + injuryThreshold + ") but was "
                    + mortalThreshold);
        }

        return new InjurySettings(
                injuryThreshold,
                reader.fraction("injuries.injury-chance", defaults.injuryChance()),
                mortalThreshold,
                reader.fraction("injuries.mortal-chance", defaults.mortalChance()),
                reader.wholeNumber("injuries.instant-health-cures",
                        defaults.instantHealthCures(), 0, 16),
                reader.decimalRange("injuries.regeneration-cure-seconds",
                        defaults.regenerationCureSeconds(), 0.0, 3600.0));
    }

    /**
     * The one ladder every ability climbs.
     *
     * <p>The four numbers are checked against each other. A ladder that does not ascend would
     * open a later level before an earlier one, which breaks the only promise the system
     * makes - that level N means powers one through N.
     */
    private static AbilitySettings readAbilities(Reader reader) {
        List<Integer> defaults = AbilitySettings.DEFAULTS.usesPerLevel();
        List<Integer> ladder = new ArrayList<>();

        for (int level = 2; level <= defaults.size() + 1; level++) {
            String path = "abilities.uses-for-level-" + level;
            int uses = reader.wholeNumber(path, defaults.get(level - 2), 1, 1_000_000);
            if (!ladder.isEmpty() && uses <= ladder.get(ladder.size() - 1)) {
                reader.errors.add(path + ": must be more than the level before it ("
                        + ladder.get(ladder.size() - 1) + ") but was " + uses);
            }
            ladder.add(uses);
        }

        return new AbilitySettings(ladder,
                reader.wholeNumber("abilities.uses-per-residue",
                        AbilitySettings.DEFAULTS.usesPerResidue(), 1, 10_000),
                reader.wholeNumber("abilities.uses-per-book",
                        AbilitySettings.DEFAULTS.usesPerBook(), 1, 10_000));
    }

    /**
     * Wave frequency, placement and drops.
     *
     * <p>The two distance bounds are checked against each other. Reversed, every attempt to
     * find a spot would fail silently and the Singularity would simply never appear - which
     * would look like a broken feature rather than a bad number.
     */
    private static SingularitySettings readSingularity(Reader reader) {
        SingularitySettings defaults = SingularitySettings.DEFAULTS;

        int minDistance = reader.wholeNumber("singularity.min-distance",
                defaults.minDistance(), 8, 128);
        int maxDistance = reader.wholeNumber("singularity.max-distance",
                defaults.maxDistance(), 8, 256);
        if (maxDistance < minDistance) {
            reader.errors.add("singularity.max-distance: must be at least"
                    + " singularity.min-distance (" + minDistance + ") but was "
                    + maxDistance);
        }

        int minResidue = reader.wholeNumber("singularity.min-residue",
                defaults.minResidue(), 0, 64);
        int maxResidue = reader.wholeNumber("singularity.max-residue",
                defaults.maxResidue(), 0, 64);
        if (maxResidue < minResidue) {
            reader.errors.add("singularity.max-residue: must be at least"
                    + " singularity.min-residue (" + minResidue + ") but was "
                    + maxResidue);
        }

        return new SingularitySettings(
                reader.fraction("singularity.chance-per-player", defaults.chancePerPlayer()),
                reader.wholeNumber("singularity.interval-seconds",
                        (int) defaults.intervalSeconds(), 30, 86_400),
                reader.fraction("singularity.book-drop-chance", defaults.bookDropChance()),
                minDistance, maxDistance, minResidue, maxResidue,
                reader.decimalRange("singularity.sense-range",
                        defaults.senseRange(), 0.0, 256.0));
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

        /** Public-facing name for a bounded decimal, used where the bound is not 0..1. */
        private double decimalRange(String path, double fallback, double min, double max) {
            return decimal(path, fallback, min, max);
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

        /**
         * Every numeric value beneath a prefix, keyed with the prefix stripped.
         *
         * <p>Open-ended on purpose so trait numbers can be added and rebalanced without this
         * parser needing to know they exist. Anything non-numeric under the prefix is
         * reported, since a string where a number belongs is a typo worth surfacing. The
         * caller is responsible for not passing intermediate YAML nodes in.
         */
        private Map<String, Double> numbersUnder(String prefix) {
            Map<String, Double> found = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String path = entry.getKey();
                if (!path.startsWith(prefix) || path.length() == prefix.length()) {
                    continue;
                }
                Object raw = entry.getValue();
                if (raw instanceof Number number) {
                    found.put(path.substring(prefix.length()), number.doubleValue());
                } else {
                    errors.add(path + ": expected a number but found " + describe(raw));
                }
            }
            return found;
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
