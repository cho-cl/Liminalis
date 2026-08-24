package com.liminalis.plugin.boon;

import com.liminalis.core.lives.BoonLives;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierType;

import java.util.Objects;

/**
 * The one place a blessing or a curse is put on somebody, or taken off.
 *
 * <p>It exists because of Thrice-Born. Every other boon is a live effect: attached on join,
 * detached on quit, recomputed from the profile whenever anything changes, and therefore
 * impossible to apply twice. A fourth life is not that - it is an edit to a saved counter,
 * and it has to happen exactly once, at the moment the boon lands.
 *
 * <p>There were two places that assigned boons: the first-join roll and the admin command.
 * Two places meant two chances to grant the life in one and forget it in the other, or to
 * clear the boon without taking the life back. Both now go through here, so granting and
 * revoking are a matched pair by construction rather than by everybody remembering.
 */
public final class BoonAssignment {

    private final ModifierRegistry registry;

    public BoonAssignment(ModifierRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Puts a boon on a player, taking whatever they had before back off.
     *
     * <p>Blessings and curses are mutually exclusive - a player carrying both is a state the
     * roll can never produce, so it must not be a state anything else can produce either.
     *
     * @param boonId the boon to assign; null clears both
     * @param type   which slot it belongs in
     */
    public void assign(PlayerProfile profile, String boonId, ModifierType type) {
        Objects.requireNonNull(profile, "profile");

        revokeLives(profile, profile.blessingId());
        revokeLives(profile, profile.curseId());
        profile.setBlessingId(null);
        profile.setCurseId(null);

        if (boonId == null) {
            return;
        }
        if (type == ModifierType.CURSE) {
            profile.setCurseId(boonId);
        } else {
            profile.setBlessingId(boonId);
        }
        grantLives(profile, boonId);
    }

    /** Takes a boon off, leaving the other slot alone. */
    public void clear(PlayerProfile profile, ModifierType type) {
        Objects.requireNonNull(profile, "profile");

        boolean curse = type == ModifierType.CURSE;
        String current = curse ? profile.curseId() : profile.blessingId();
        if (current == null) {
            return;
        }
        revokeLives(profile, current);
        if (curse) {
            profile.setCurseId(null);
        } else {
            profile.setBlessingId(null);
        }
    }

    private void grantLives(PlayerProfile profile, String boonId) {
        lifeGranting(boonId).ifPresent(boon -> BoonLives.grant(profile, boon.extraLives()));
    }

    private void revokeLives(PlayerProfile profile, String boonId) {
        lifeGranting(boonId).ifPresent(boon -> BoonLives.revoke(profile, boon.extraLives()));
    }

    private java.util.Optional<LifeGranting> lifeGranting(String boonId) {
        if (boonId == null) {
            return java.util.Optional.empty();
        }
        return registry.find(boonId)
                .filter(LifeGranting.class::isInstance)
                .map(LifeGranting.class::cast);
    }
}
