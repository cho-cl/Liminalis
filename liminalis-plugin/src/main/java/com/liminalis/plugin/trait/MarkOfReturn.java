package com.liminalis.plugin.trait;

import com.liminalis.plugin.limbo.LimboService;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.modifier.capability.Ticking;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mark left on anyone who has been to Limbo and come back.
 *
 * <p>Permanent, and unlike a trait it is earned rather than rolled. What it grants is a
 * <em>sense</em> of the dead, not sight of them: when someone in Limbo is walking nearby as a
 * ghost, the marked player hears something and knows they are not alone. They cannot tell
 * where, or who.
 *
 * <p>That distinction is the whole design. Deathsight - the rare Singularity trait - shows
 * you exactly where a ghost is standing. The mark only tells you one is there, which is
 * enough to be unsettling and not enough to be useful on its own. It is the difference
 * between having been to the other side and being able to look into it.
 */
public final class MarkOfReturn implements Modifier, Ticking {

    public static final String ID = "mark_of_return";

    /** Shared-loop intervals between cues, so this stays eerie rather than constant. */
    private static final int INTERVALS_BETWEEN_CUES = 12;

    private final LimboService limbo;
    private final TraitTuning tuning;
    private final Map<UUID, Integer> sinceLastCue = new ConcurrentHashMap<>();

    public MarkOfReturn(TraitTuning tuning, LimboService limbo) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.limbo = Objects.requireNonNull(limbo, "limbo");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ModifierType type() {
        return ModifierType.MARK;
    }

    @Override
    public void tick(Player player) {
        int waited = sinceLastCue.merge(player.getUniqueId(), 1, Integer::sum);
        if (waited < INTERVALS_BETWEEN_CUES) {
            return;
        }
        sinceLastCue.put(player.getUniqueId(), 0);

        if (!aGhostIsNear(player)) {
            return;
        }
        // Played at the listener's own position, so it carries no direction. They know
        // something is there; they do not know where.
        player.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 0.25f, 0.6f);
    }

    @Override
    public void onDetach(Player player) {
        sinceLastCue.remove(player.getUniqueId());
    }

    private boolean aGhostIsNear(Player player) {
        double range = tuning.get("mark_of_return.sense-range", 24.0);
        double rangeSquared = range * range;

        for (Player other : player.getServer().getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())
                    || !limbo.isGhosting(other.getUniqueId())
                    || !other.getWorld().equals(player.getWorld())) {
                continue;
            }
            if (other.getLocation().distanceSquared(player.getLocation()) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }
}
