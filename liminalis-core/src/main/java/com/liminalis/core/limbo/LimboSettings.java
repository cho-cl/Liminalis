package com.liminalis.core.limbo;

/**
 * Tuning for Limbo and for coming back from it.
 *
 * @param worldName     the Bukkit world Limbo lives in
 * @param borderRadius  how far Limbo extends from its centre; large enough to feel endless
 *                      on foot, bounded so a wanderer cannot generate chunks without limit
 * @param revivalLives  how many lives someone returns with
 * @param whisperChance the chance that something said in Limbo is faintly heard by the living
 * @param ghostVisit    how long and how often the dead may walk among the living
 */
public record LimboSettings(
        String worldName,
        int borderRadius,
        int revivalLives,
        double whisperChance,
        GhostVisitSettings ghostVisit) {

    public static final LimboSettings DEFAULTS = new LimboSettings(
            "liminalis_limbo", 5000, 2, 0.15, GhostVisitSettings.DEFAULTS);
}
