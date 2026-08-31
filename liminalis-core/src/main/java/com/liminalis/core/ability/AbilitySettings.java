package com.liminalis.core.ability;

import java.util.List;

/**
 * The one ladder every ability climbs.
 *
 * <p>Shared rather than per-ability, which is the whole of the simplification: there is one
 * number to tune, it means the same thing for every ability ever written, and a player can be
 * told the rule in a sentence.
 *
 * @param usesPerLevel uses needed for level 2, 3, 4 and 5, in order. Must ascend, or a later
 *                     level would open before an earlier one
 * @param usesPerResidue uses one shard of Singularity residue is worth. The alternative route
 *                       for somebody whose ability rarely finds an occasion to be used
 */
public record AbilitySettings(List<Integer> usesPerLevel, int usesPerResidue) {

    public AbilitySettings {
        usesPerLevel = List.copyOf(usesPerLevel);
    }

    public static final AbilitySettings DEFAULTS =
            new AbilitySettings(List.of(25, 75, 150, 300), 25);
}
