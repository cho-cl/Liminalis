package com.liminalis.core.singularity;

/**
 * How often the Singularity reaches through, and how close it puts things.
 *
 * @param chancePerPlayer chance each online player draws a creature, rolled every interval
 * @param intervalSeconds how long between waves
 * @param bookDropChance  chance a dead creature leaves one of the five books
 * @param minDistance     nearest a creature may appear to the player it was sent to
 * @param maxDistance     furthest it may appear; beyond this it would simply never be found
 * @param minResidue      fewest shards of residue a creature leaves
 * @param maxResidue      most it leaves
 * @param senseRange      how far away a player starts to feel one of these. Deliberately
 *                        larger than the distance they spawn at, so the warning lands before
 *                        the creature does rather than at the same moment
 */
public record SingularitySettings(double chancePerPlayer,
                                  long intervalSeconds,
                                  double bookDropChance,
                                  int minDistance,
                                  int maxDistance,
                                  int minResidue,
                                  int maxResidue,
                                  double senseRange) {

    public static final SingularitySettings DEFAULTS =
            new SingularitySettings(0.5, 1800L, 0.75, 24, 48, 1, 3, 40.0);
}
