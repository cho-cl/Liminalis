package com.liminalis.plugin.command;

import com.liminalis.core.injury.ActiveInjury;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.boon.BoonAssignment;
import com.liminalis.plugin.injury.Injury;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierType;

import java.util.Objects;

/**
 * Putting one thing on a player, and taking it off again.
 *
 * <p>Six kinds of modifier used to mean six subtrees of admin commands, each with its own
 * {@code give} and its own {@code remove} and its own name for what removing meant - traits
 * were removed, boons were cleared, injuries were healed. Every one of them did the same job,
 * and the operator had to remember which noun went with which verb before they could type
 * anything.
 *
 * <p>So the difference between the kinds lives here instead, in one switch, and there is one
 * {@code give} and one {@code take} above it. What is genuinely different between the types
 * stays different: an injury carries an expiry, an ability resets its progress, and a boon
 * goes through {@link BoonAssignment} because one of them carries a life with it.
 */
public final class Grants {

    private final ModifierRegistry registry;
    private final BoonAssignment boons;

    public Grants(ModifierRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.boons = new BoonAssignment(registry);
    }

    /**
     * Puts a modifier on a player.
     *
     * @return false if they already had it and nothing changed
     */
    public boolean give(PlayerProfile profile, Modifier modifier) {
        return switch (modifier.type()) {
            case TRAIT -> profile.addTrait(modifier.id());
            case MARK -> profile.addMark(modifier.id());
            case BLESSING, CURSE -> {
                if (modifier.id().equals(profile.blessingId())
                        || modifier.id().equals(profile.curseId())) {
                    yield false;
                }
                boons.assign(profile, modifier.id(), modifier.type());
                yield true;
            }
            case INJURY -> {
                if (profile.hasInjury(modifier.id())) {
                    yield false;
                }
                profile.addInjury(new ActiveInjury(modifier.id(), expiryOf(modifier)));
                yield true;
            }
            case ABILITY -> {
                if (modifier.id().equals(profile.abilityId())) {
                    yield false;
                }
                profile.setAbilityId(modifier.id());
                // A new ability starts over. Carrying the old one's use count across would
                // hand somebody level five of something they have never cast.
                profile.clearAbilityProgress();
                profile.setAbilityTier(1);
                yield true;
            }
        };
    }

    /**
     * Takes a modifier off a player.
     *
     * @return false if they were not carrying it
     */
    public boolean take(PlayerProfile profile, Modifier modifier) {
        return switch (modifier.type()) {
            case TRAIT -> profile.removeTrait(modifier.id());
            case MARK -> profile.removeMark(modifier.id());
            case BLESSING, CURSE -> {
                String current = modifier.type() == ModifierType.CURSE
                        ? profile.curseId() : profile.blessingId();
                if (!modifier.id().equals(current)) {
                    yield false;
                }
                boons.clear(profile, modifier.type());
                yield true;
            }
            case INJURY -> profile.removeInjury(modifier.id());
            case ABILITY -> {
                if (!modifier.id().equals(profile.abilityId())) {
                    yield false;
                }
                profile.setAbilityId(null);
                profile.setAbilityTier(0);
                profile.clearAbilityProgress();
                yield true;
            }
        };
    }

    /** Whether a player is currently carrying this. Drives what {@code take} tab-completes. */
    public boolean has(PlayerProfile profile, Modifier modifier) {
        return switch (modifier.type()) {
            case TRAIT -> profile.traitIds().contains(modifier.id());
            case MARK -> profile.markIds().contains(modifier.id());
            case BLESSING -> modifier.id().equals(profile.blessingId());
            case CURSE -> modifier.id().equals(profile.curseId());
            case INJURY -> profile.hasInjury(modifier.id());
            case ABILITY -> modifier.id().equals(profile.abilityId());
        };
    }

    /**
     * When a granted wound should fade.
     *
     * <p>Mortal wounds never do, and everything else uses its own configured lifetime - so an
     * injury handed out for testing behaves exactly like one earned by being hit, rather than
     * being a special admin-only version that outlives it.
     */
    private static long expiryOf(Modifier modifier) {
        if (!(modifier instanceof Injury injury) || !injury.decays()) {
            return 0L;
        }
        return System.currentTimeMillis() + injury.durationSeconds() * 1000L;
    }

    public ModifierRegistry registry() {
        return registry;
    }
}
