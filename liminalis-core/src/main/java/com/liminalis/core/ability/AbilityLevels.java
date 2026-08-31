package com.liminalis.core.ability;

import java.util.List;
import java.util.Objects;

/**
 * One ladder, shared by every ability there will ever be.
 *
 * <p>This replaces a system where each ability defined its own counters and its own thresholds
 * - the Priest earned tiers by healing and by felling undead, and the next ability would have
 * invented two more counters of its own. It was tailored, and tailoring is exactly what made
 * it complicated: five moving parts per ability, a progress bar that had to explain which of
 * two different things you were being measured on, and no way to answer "how far along are
 * you" without knowing which ability somebody had.
 *
 * <p>Now there is one number. <strong>You level by using your ability</strong>, every ability
 * counts the same thing, and level N means powers one through N are yours. A player can be
 * told the whole rule in one sentence, which is worth more than any amount of thematic
 * tailoring.
 *
 * <p>Uses are only counted when a power actually did something. A power that refuses - nothing
 * to smite, nobody hurt to heal - is not progress, or the fastest way to level would be to
 * stand in a field firing into nothing.
 */
public final class AbilityLevels {

    /** Everyone starts here. An ability you cannot use at all reads as a rejection. */
    public static final int FIRST_LEVEL = 1;

    /**
     * The one counter, shared by every ability.
     *
     * <p>Not namespaced per ability, unlike the counters this replaced. That is deliberate and
     * it is the whole simplification: there is nothing to namespace, because there is only
     * ever one of them.
     */
    public static final String USES = "ability.uses";

    private AbilityLevels() {
    }

    /**
     * The level a use count earns.
     *
     * @param thresholds uses needed for level 2, 3, 4... in order. An empty list means every
     *                   ability sits at level one forever, which is a valid, if dull, config
     */
    public static int levelFor(int uses, List<Integer> thresholds) {
        Objects.requireNonNull(thresholds, "thresholds");

        int level = FIRST_LEVEL;
        for (int threshold : thresholds) {
            if (uses < threshold) {
                break;
            }
            level++;
        }
        return level;
    }

    /** The highest level these thresholds can reach. */
    public static int maxLevel(List<Integer> thresholds) {
        return FIRST_LEVEL + Objects.requireNonNull(thresholds, "thresholds").size();
    }

    /**
     * Uses still needed for the next level, or 0 at the top.
     *
     * <p>This is the number a player is actually shown, because "37 more" is a thing somebody
     * can act on and "level 3 of 5" is not.
     */
    public static int usesToNext(int uses, List<Integer> thresholds) {
        int level = levelFor(uses, thresholds);
        if (level >= maxLevel(thresholds)) {
            return 0;
        }
        return Math.max(0, thresholds.get(level - FIRST_LEVEL) - uses);
    }

    /**
     * How far into the current level a player is, from 0 to 1.
     *
     * <p>Measured from the previous threshold rather than from zero, so a bar fills across
     * each level instead of crawling for the first one and leaping for the last. Returns 1 at
     * the top: a bar that can never fill is worse than no bar.
     */
    public static double progressToNext(int uses, List<Integer> thresholds) {
        int level = levelFor(uses, thresholds);
        if (level >= maxLevel(thresholds)) {
            return 1.0;
        }
        int floor = level == FIRST_LEVEL ? 0 : thresholds.get(level - FIRST_LEVEL - 1);
        int ceiling = thresholds.get(level - FIRST_LEVEL);
        if (ceiling <= floor) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (uses - floor) / (double) (ceiling - floor)));
    }

    /**
     * The use count that sits exactly at the start of a level.
     *
     * <p>Needed because an admin setting somebody's level has to leave the counter agreeing
     * with it. Setting the number alone would leave a player who is level four by decree and
     * level one by arithmetic, and the next use would drop them back - which is precisely the
     * kind of silent disagreement the old two-source system kept producing.
     */
    public static int usesForLevel(int level, List<Integer> thresholds) {
        int clamped = Math.max(FIRST_LEVEL, Math.min(level, maxLevel(thresholds)));
        return clamped == FIRST_LEVEL ? 0 : thresholds.get(clamped - FIRST_LEVEL - 1);
    }
}
