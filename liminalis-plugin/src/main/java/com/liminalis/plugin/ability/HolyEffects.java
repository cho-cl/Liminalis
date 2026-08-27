package com.liminalis.plugin.ability;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * The shapes holy power makes.
 *
 * <p>Every power the Priest had was one call to {@code spawnParticle} and one sound - a puff
 * of particles in a sphere, which is what a plugin does when nobody has decided what its magic
 * looks like. Closing a mortal wound, the rarest and most consequential thing anyone on this
 * server can do, looked exactly like healing someone for three hearts.
 *
 * <p>So this is a small vocabulary rather than a helper: rings, pillars, spirals, beams and
 * domes, out of which each power is composed differently. The shapes are what make one power
 * legible as a different thing from another at a glance, across a field, by somebody who is
 * not the one casting it.
 *
 * <p><strong>Everything here draws in a single frame.</strong> No animation, and no timers -
 * modifiers in this plugin never own a {@code BukkitTask}, and the shared modifier loop at two
 * ticks a second is far too coarse for a smooth one anyway. It matters less than it sounds:
 * particles linger for about a second, so a whole spiral drawn at once still reads as a
 * spiral. Sustained effects, where something must persist, are driven from that shared loop
 * instead.
 */
public final class HolyEffects {

    /** Warm gold. The colour of the whole ability. */
    public static final Particle.DustOptions GOLD =
            new Particle.DustOptions(Color.fromRGB(255, 226, 148), 1.0f);

    /** Pale, near-white gold, for the edges of a shape. */
    public static final Particle.DustOptions PALE =
            new Particle.DustOptions(Color.fromRGB(255, 248, 224), 0.8f);

    /** Deeper amber, used only where something is meant to look heavy. */
    public static final Particle.DustOptions AMBER =
            new Particle.DustOptions(Color.fromRGB(232, 172, 64), 1.3f);

    private HolyEffects() {
    }

    // ------------------------------------------------------------------------- shapes

    /**
     * A flat circle on the ground.
     *
     * <p>The workhorse. A ring is the clearest way to say "this far and no further", which is
     * what both the smite and the consecration are actually about - so both draw one at their
     * real radius, and a player can see the edge of the effect rather than guessing at it.
     */
    public static void ring(Location centre, double radius, int points,
                            Particle particle, Object data) {
        World world = centre.getWorld();
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2 / points) * i;
            Location at = centre.clone().add(
                    Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            spawn(world, particle, at, data);
        }
    }

    /** Several rings inside each other, which reads as a shockwave without animating. */
    public static void shockwave(Location centre, double radius, int rings,
                                 Particle particle, Object data) {
        for (int i = 1; i <= rings; i++) {
            double r = radius * i / rings;
            ring(centre, r, Math.max(8, (int) (r * 8)), particle, data);
        }
    }

    /** A column of light standing where something happened. */
    public static void pillar(Location base, double height, int points,
                              Particle particle, Object data) {
        World world = base.getWorld();
        for (int i = 0; i < points; i++) {
            Location at = base.clone().add(0, height * i / points, 0);
            spawn(world, particle, at, data);
        }
    }

    /**
     * A helix winding upward.
     *
     * <p>Rising rather than falling, everywhere it is used. The Priest mends and protects, and
     * the direction a shape travels is most of what tells a player whether something good is
     * happening to them.
     */
    public static void spiral(Location base, double height, double radius,
                              int points, double turns, Particle particle, Object data) {
        World world = base.getWorld();
        for (int i = 0; i < points; i++) {
            double progress = i / (double) points;
            double angle = Math.PI * 2 * turns * progress;
            Location at = base.clone().add(
                    Math.cos(angle) * radius, height * progress, Math.sin(angle) * radius);
            spawn(world, particle, at, data);
        }
    }

    /** Two helices out of phase, for the one power that should look like more than the rest. */
    public static void doubleSpiral(Location base, double height, double radius,
                                    int points, double turns, Particle particle, Object data) {
        World world = base.getWorld();
        for (int i = 0; i < points; i++) {
            double progress = i / (double) points;
            double angle = Math.PI * 2 * turns * progress;
            for (double offset : new double[] {0, Math.PI}) {
                Location at = base.clone().add(
                        Math.cos(angle + offset) * radius,
                        height * progress,
                        Math.sin(angle + offset) * radius);
                spawn(world, particle, at, data);
            }
        }
    }

    /** A line drawn between two points: a priest reaching somebody. */
    public static void beam(Location from, Location to, int points,
                            Particle particle, Object data) {
        World world = from.getWorld();
        if (!world.equals(to.getWorld())) {
            return;
        }
        var step = to.toVector().subtract(from.toVector()).multiply(1.0 / points);
        Location at = from.clone();
        for (int i = 0; i < points; i++) {
            at.add(step);
            spawn(world, particle, at, data);
        }
    }

    /** A hemisphere over a place, drawn as stacked rings that shrink toward the top. */
    public static void dome(Location centre, double radius, int rings,
                            Particle particle, Object data) {
        for (int i = 0; i < rings; i++) {
            double fraction = i / (double) rings;
            double y = radius * fraction;
            double r = radius * Math.cos(fraction * Math.PI / 2);
            ring(centre.clone().add(0, y, 0), r, Math.max(6, (int) (r * 5)), particle, data);
        }
    }

    /** A small ring above someone's head. The mark of a priest, and of the blessed. */
    public static void halo(Player who, int points, Particle particle, Object data) {
        ring(who.getLocation().add(0, 2.15, 0), 0.45, points, particle, data);
    }

    // ------------------------------------------------------------------------- sound

    /**
     * Several sounds at once, which is the difference between a noise and a chord.
     *
     * <p>One sample is a game event; two or three layered is a moment. The Priest is the
     * ability players are meant to be glad to see arrive, so its powers get chords.
     */
    public static void chord(Location at, float volume, Sound[] sounds, float[] pitches) {
        World world = at.getWorld();
        for (int i = 0; i < sounds.length; i++) {
            world.playSound(at, sounds[i], volume, pitches[i]);
        }
    }

    // ------------------------------------------------------------------------ helpers

    /**
     * Spawns one particle, with data only where the particle actually takes it.
     *
     * <p>Passing a {@code DustOptions} to a particle that does not want one throws, and
     * passing none to {@code DUST} makes it red - so the check lives here once rather than at
     * every call site, where getting it wrong would be silent until somebody saw a crimson
     * halo over a priest.
     */
    private static void spawn(World world, Particle particle, Location at, Object data) {
        if (data != null && particle.getDataType() != Void.class
                && particle.getDataType().isInstance(data)) {
            world.spawnParticle(particle, at, 1, 0, 0, 0, 0, data);
        } else {
            world.spawnParticle(particle, at, 1, 0, 0, 0, 0);
        }
    }
}
