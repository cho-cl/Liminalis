package com.liminalis.core.roll;

/**
 * How likely a blessing or a curse is on first join.
 *
 * <p>The two are mutually exclusive, and both chances are measured against the whole
 * population rather than against each other - 0.15 and 0.15 means fifteen percent blessed,
 * fifteen percent cursed, seventy percent neither.
 */
public record BoonRollSettings(double blessingChance, double curseChance) {

    public static final BoonRollSettings DEFAULTS = new BoonRollSettings(0.15, 0.15);
}
