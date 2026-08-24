package com.liminalis.core.profile;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads every modifier id a profile refers to, across all of its slots.
 *
 * <p>Traits, blessings, curses, marks and abilities are stored separately because they are
 * rolled and granted differently, but once attached they are all just modifiers. This is the
 * single place that flattens the slots, so nothing downstream has to remember the list.
 */
public final class ProfileModifierIds {

    private ProfileModifierIds() {
    }

    /**
     * Every modifier id the profile carries, de-duplicated and in a stable order.
     *
     * <p>De-duplication is not defensive tidiness: attaching the same modifier twice would
     * apply its effect twice, which is how a stat modifier quietly becomes twice as strong.
     */
    public static Set<String> referencedBy(PlayerProfile profile) {
        Set<String> ids = new LinkedHashSet<>(profile.traitIds());
        addIfPresent(ids, profile.blessingId());
        addIfPresent(ids, profile.curseId());
        ids.addAll(profile.markIds());
        addIfPresent(ids, profile.abilityId());

        // Injuries live in their own list because they carry an expiry, but they are still
        // modifiers and still have to be attached. Leaving them out of here is exactly how
        // wounds ended up purely cosmetic - visible on the HUD and in /profile, applying no
        // penalty and never ticking, because nothing ever looked them up.
        profile.injuries().forEach(injury -> addIfPresent(ids, injury.id()));
        return ids;
    }

    private static void addIfPresent(Set<String> ids, String id) {
        if (id != null && !id.isBlank()) {
            ids.add(id);
        }
    }
}
