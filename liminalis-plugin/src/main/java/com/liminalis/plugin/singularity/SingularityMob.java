package com.liminalis.plugin.singularity;

import org.bukkit.Particle;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * One kind of thing the Singularity drops into the world.
 *
 * <p>Built on a vanilla base rather than as a fully custom entity. That is a deliberate
 * trade: a reskinned zombie with rewritten attributes, a name, an aura and its own drops
 * fights like nothing in vanilla and costs almost nothing at runtime, while a display-entity
 * rig with hand-written pathing would look better and break in more ways. With the resource
 * pack the textures can be swapped later without touching any of this.
 *
 * @param id           stable id, stored on the entity so its drops can be identified on death
 * @param base         the vanilla entity underneath
 * @param maxHealth    how much punishment it takes
 * @param attackDamage how hard it hits, before the victim's armour
 * @param speedScalar  movement speed as a multiple of its base type's
 * @param followRange  how far it will notice and pursue someone, in blocks
 * @param aura         particle trailing from it, so it reads as wrong before it reaches you
 */
public record SingularityMob(String id,
                             EntityType base,
                             double maxHealth,
                             double attackDamage,
                             double speedScalar,
                             double followRange,
                             Particle aura) {

    /**
     * The roster.
     *
     * <p>Three shapes rather than three stat blocks: one that will not stop coming, one that
     * ignores walls, and one that will not let you close the distance. Between them there is
     * no single answer that works, which is the point.
     */
    public static List<SingularityMob> all() {
        return List.of(
                // Slow, enormously durable, hits like a collapse. You do not beat this one in
                // a straight fight early on - you leave.
                new SingularityMob("hollow_one", EntityType.ZOMBIE,
                        60.0, 9.0, 0.85, 40.0, Particle.SOUL),

                // Fast, erratic, and passes through walls. Nowhere is safe from it, which is
                // what makes the other two frightening rather than merely difficult.
                new SingularityMob("watcher", EntityType.VEX,
                        26.0, 6.0, 1.15, 48.0, Particle.SOUL_FIRE_FLAME),

                // Keeps its distance and punishes anyone who stands still. Turns a fight
                // against the others into a problem with no good ground.
                new SingularityMob("remnant", EntityType.SKELETON,
                        34.0, 5.0, 1.05, 48.0, Particle.ASH));
    }
}
