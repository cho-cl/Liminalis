package com.liminalis.core.ability;

import java.util.Set;

/**
 * What counts as undead, by name.
 *
 * <p>This used to ask Bukkit: {@code mob.getCategory() == EntityCategory.UNDEAD}. That reads
 * beautifully and it is why Holy Smite found nothing to smite, ever - the category is derived
 * from data the server does not always populate the way the old API assumed, and a check that
 * quietly answers "no" for every zombie in the world produces a power that refuses every time
 * it is used and explains itself perfectly while doing it.
 *
 * <p>So the list is written out. It is longer and duller and it is right, and being a plain
 * set of names means it can be tested without a server and corrected by anybody reading it.
 */
public final class Undead {

    /**
     * Every undead entity type in 1.21, by its Bukkit name.
     *
     * <p>Names rather than the enum so this can live in core, which never imports Bukkit.
     * The plugin looks up {@code entity.getType().name()} and asks here.
     */
    public static final Set<String> TYPES = Set.of(
            "ZOMBIE",
            "ZOMBIE_VILLAGER",
            "HUSK",
            "DROWNED",
            "ZOMBIFIED_PIGLIN",
            "ZOGLIN",
            "SKELETON",
            "STRAY",
            "BOGGED",
            "WITHER_SKELETON",
            "WITHER",
            "PHANTOM",
            "ZOMBIE_HORSE",
            "SKELETON_HORSE",
            "GIANT");

    private Undead() {
    }

    public static boolean is(String entityTypeName) {
        return entityTypeName != null && TYPES.contains(entityTypeName);
    }
}
