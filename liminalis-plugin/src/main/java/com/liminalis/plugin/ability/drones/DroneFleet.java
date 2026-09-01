package com.liminalis.plugin.ability.drones;

import com.liminalis.core.ability.Aggression;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything one player's drones are currently doing.
 *
 * <p>Held in memory rather than on the profile, because all of it is about right now: which
 * bees exist, what each one has been sent to do, whether their owner is currently looking
 * through one of them. A restart ends all of that, and should - a job half-finished by an
 * entity that no longer exists is worse than no job.
 *
 * <p>The one thing that outlives a session is the aggression setting, which is a preference
 * rather than a state and would be tedious to set again every login.
 */
public final class DroneFleet {

    /** What a single drone has been told to go and do. */
    public sealed interface Job permits Job.Mine, Job.Place, Job.Carry {

        /** Fly to a block and break it. */
        record Mine(Location block) implements Job {
        }

        /** Fly to a block and put the held one against the given face of it. */
        record Place(Location against, BlockFace face) implements Job {
        }

        /** Take hold of something and haul it back to the owner. */
        record Carry(UUID target) implements Job {
        }
    }

    private final List<Bee> drones = new ArrayList<>();
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();

    private Aggression aggression = Aggression.standard();

    /** Index of the drone being looked through, or -1 for nobody. */
    private int controlling = -1;

    // ------------------------------------------------------------------------- drones

    public List<Bee> drones() {
        drones.removeIf(bee -> bee == null || bee.isDead() || !bee.isValid());
        return drones;
    }

    public void add(Bee bee) {
        drones.add(bee);
    }

    public int size() {
        return drones().size();
    }

    public boolean isEmpty() {
        return drones().isEmpty();
    }

    /** Forgets one drone, and stops looking through it if that is what was happening. */
    public void forget(Bee bee) {
        int index = drones.indexOf(bee);
        drones.remove(bee);
        jobs.remove(bee.getUniqueId());
        if (index >= 0 && index == controlling) {
            controlling = -1;
        }
    }

    public void clear() {
        drones.clear();
        jobs.clear();
        controlling = -1;
    }

    // --------------------------------------------------------------------------- jobs

    public Job jobOf(Bee bee) {
        return jobs.get(bee.getUniqueId());
    }

    public void assign(Bee bee, Job job) {
        jobs.put(bee.getUniqueId(), job);
    }

    public void finish(Bee bee) {
        jobs.remove(bee.getUniqueId());
    }

    public void clearJobs() {
        jobs.clear();
    }

    /**
     * The first drone with nothing to do, or null if they are all busy.
     *
     * <p>Jobs go to idle drones rather than the nearest one so that a player who queues five
     * blocks gets five drones working, which is the entire point of having more than one.
     */
    public Bee idleDrone() {
        for (Bee bee : drones()) {
            if (!jobs.containsKey(bee.getUniqueId())) {
                return bee;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------- aggression

    public Aggression aggression() {
        return aggression;
    }

    public void setAggression(Aggression aggression) {
        this.aggression = aggression;
    }

    // ------------------------------------------------------------------------- control

    public boolean isControlling() {
        return controlling >= 0 && controlling < size();
    }

    public Bee controlled() {
        return isControlling() ? drones().get(controlling) : null;
    }

    public int controllingIndex() {
        return controlling;
    }

    public void control(int index) {
        this.controlling = index;
    }

    public void release() {
        this.controlling = -1;
    }

    /**
     * Moves to the next drone, or reports that there is no next one.
     *
     * <p>Cycling and then letting go is how one command does both jobs. With a single drone
     * it is a plain toggle; with five it walks them in order and releases at the end, so the
     * player never has to remember a second gesture for "stop".
     *
     * @return true if there was another drone to move to
     */
    public boolean advance() {
        if (!isControlling()) {
            return false;
        }
        if (controlling + 1 >= size()) {
            return false;
        }
        controlling++;
        return true;
    }

    /**
     * An idle drone that is carrying something, preferred over one that is not.
     *
     * <p>Which drone takes a job used to be whichever happened to be first in the list, and
     * because a drone carrying a block places while an empty one digs, that meant the same
     * command did opposite things depending on the order the swarm had been summoned in.
     * Building with drones was a coin toss. A loaded drone now wins: a player who has
     * deliberately put a block on one has said what they want the swarm to be doing.
     *
     * @return a loaded idle drone, or any idle drone, or null if they are all busy
     */
    public Bee idleCarrier() {
        Bee fallback = null;
        for (Bee bee : drones()) {
            if (jobs.containsKey(bee.getUniqueId())) {
                continue;
            }
            var equipment = bee.getEquipment();
            boolean loaded = equipment != null && equipment.getHelmet() != null
                    && !equipment.getHelmet().getType().isAir();
            if (loaded) {
                return bee;
            }
            if (fallback == null) {
                fallback = bee;
            }
        }
        return fallback;
    }

    /** Whether this entity is one of the drones in this fleet. */
    public boolean owns(Entity entity) {
        return entity instanceof Bee bee && drones().contains(bee);
    }
}
