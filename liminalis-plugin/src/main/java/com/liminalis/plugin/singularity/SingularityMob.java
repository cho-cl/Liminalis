package com.liminalis.plugin.singularity;

import com.liminalis.core.roll.WeightedEntry;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * One kind of thing the Singularity drops into the world.
 *
 * <p>Built on vanilla bases rather than as fully custom entities. That is a deliberate trade:
 * a reskinned base with rewritten attributes, a size, a name, an aura and its own drops fights
 * like nothing in vanilla and costs almost nothing at runtime, while a display-entity rig with
 * hand-written pathing would look better and break in more ways.
 *
 * <p>Variety comes from three axes rather than from stat blocks: the <em>silhouette</em> of the
 * base entity, the <em>scale</em> applied on top of it, and how often it appears. A player who
 * sees something on the horizon should be able to tell roughly what kind of trouble it is from
 * its outline alone, before it is close enough to read a name.
 *
 * @param id          stable id, stored on the entity so drops can be identified on death
 * @param base        the vanilla entity underneath, chosen for its silhouette
 * @param scale       size multiplier; 1.8 is a thing that blots out the sky, 0.6 is underfoot
 * @param maxHealth   how much punishment it takes
 * @param attackDamage how hard it hits, before the victim's armour
 * @param speedScalar movement speed as a multiple of its base type's
 * @param followRange how far it will notice and pursue someone, in blocks
 * @param weight      how often it is chosen relative to the others
 * @param aura        particle trailing from it, so it reads as wrong before it reaches you
 */
public record SingularityMob(String id,
                             EntityType base,
                             double scale,
                             double maxHealth,
                             double attackDamage,
                             double speedScalar,
                             double followRange,
                             double weight,
                             Particle aura) {

    /**
     * The roster.
     *
     * <p>Eight silhouettes, from something you can step on to something you can see over a
     * hill. No single answer beats all of them: one will not stop coming, one ignores walls,
     * one will not let you close, one is faster than you, and one you should simply run from.
     */
    public static List<SingularityMob> all() {
        return List.of(
                // Slow, durable, relentless. The baseline everything else is read against.
                new SingularityMob("hollow_one", EntityType.ZOMBIE, 1.0,
                        60.0, 9.0, 0.85, 40.0, 1.0, Particle.SOUL),

                // Small, fast, erratic, and passes through walls. Nowhere is safe from it.
                new SingularityMob("watcher", EntityType.VEX, 1.0,
                        26.0, 6.0, 1.15, 48.0, 1.0, Particle.SOUL_FIRE_FLAME),

                // Keeps its distance and punishes anyone who stands still.
                new SingularityMob("remnant", EntityType.SKELETON, 1.0,
                        34.0, 5.0, 1.05, 48.0, 1.0, Particle.ASH),

                // Half again as tall as a person and it teleports. Reads as wrong from a
                // very long way off, which is most of what it is for.
                new SingularityMob("the_tall", EntityType.ENDERMAN, 1.5,
                        50.0, 8.0, 1.0, 56.0, 0.7, Particle.SOUL),

                // Enormous and slow. A husk rather than a zombie because husks do not burn
                // at dawn - this is meant to still be standing there in the morning.
                new SingularityMob("carrion", EntityType.HUSK, 1.8,
                        110.0, 11.0, 0.7, 40.0, 0.5, Particle.ASH),

                // Underfoot, quick, and hard to hit. Arrives in ones but feels like more.
                new SingularityMob("mote", EntityType.ENDERMITE, 0.6,
                        12.0, 3.0, 1.35, 32.0, 1.2, Particle.SOUL_FIRE_FLAME),

                // Climbs walls and gets behind you. Ruins any plan that involved a corner.
                new SingularityMob("crawler", EntityType.CAVE_SPIDER, 1.3,
                        30.0, 6.0, 1.2, 48.0, 0.9, Particle.ASH),

                // Rare, and genuinely dangerous. Kept at base scale because a Warden does
                // not need help being frightening, and heavily cut down from vanilla's 500
                // health - this is meant to be survivable by a prepared group, not a boss.
                // If it proves too much, lower its weight or drop it from this list.
                new SingularityMob("herald", EntityType.WARDEN, 1.0,
                        140.0, 12.0, 0.9, 48.0, 0.12, Particle.SCULK_SOUL));
    }

    public WeightedEntry asEntry() {
        return new WeightedEntry(id, weight);
    }
}
