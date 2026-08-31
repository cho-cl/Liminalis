package com.liminalis.plugin.ability;

import com.liminalis.core.ability.Aggression;
import com.liminalis.plugin.ability.drones.DroneFleet;
import com.liminalis.plugin.ability.drones.DroneService;
import com.liminalis.plugin.text.Messages;
import com.liminalis.plugin.trait.TraitTuning;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.Objects;

/**
 * The Drones - five bees that work for you, and the first ability written after the Priest.
 *
 * <p>Worth reading beside the Priest, because the two are almost opposites and both are five
 * powers on the same frame. The Priest spends its powers on other people; the Drones spend
 * theirs on the world. What they share is that no power in either is a number - each one is a
 * different verb, and an ability whose powers are five sizes of the same thing has one power
 * and four settings.
 *
 * <p>One bee per level, so levelling is legible without reading anything: you can count your
 * ability. The sixth power is the exception the framework now allows - a settings toggle,
 * open from the start, because a control deciding whether your drones will sting other
 * players is not a reward for having played a while. It is the thing you need before you own
 * anything dangerous enough to need it.
 */
public final class DronesAbility implements Ability {

    public static final String ID = "drones";

    private final TraitTuning tuning;
    private final DroneService drones;
    private final Messages messages;

    public DronesAbility(TraitTuning tuning, DroneService drones, Messages messages) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.drones = Objects.requireNonNull(drones, "drones");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Power> powers() {
        return List.of(new Summon(), new Attack(), new Bring(), new Work(),
                new Control(), new Temperament());
    }

    // -------------------------------------------------------------------------- 1. summon

    /**
     * Call them out, or send them all away.
     *
     * <p>One command for both because the second is only ever wanted when the first has
     * nothing left to do. Summoning while full is not a mistake worth an error message - it
     * is unambiguously a request to put them away.
     */
    private final class Summon implements Power {

        @Override
        public int slot() {
            return 1;
        }

        @Override
        public String id() {
            return "summon";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("drones.summon-cooldown", 3);
        }

        @Override
        public boolean use(Player player, Player ignored) {
            int allowed = allowedFor(player);
            DroneFleet fleet = drones.fleetOf(player);

            if (fleet.size() >= allowed) {
                int sent = drones.dismissAll(player.getUniqueId());
                messages.send(player, "ability.drones.dismissed",
                        Messages.placeholder("count", sent));
                return true;
            }

            Bee bee = drones.summon(player, allowed);
            if (bee == null) {
                return false;
            }
            messages.send(player, "ability.drones.summoned",
                    Messages.placeholder("count", fleet.size()),
                    Messages.placeholder("total", allowed));
            return true;
        }
    }

    // -------------------------------------------------------------------------- 2. attack

    /** Point at something and set all of them on it. */
    private final class Attack implements Power {

        @Override
        public int slot() {
            return 2;
        }

        @Override
        public String id() {
            return "attack";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("drones.attack-cooldown", 4);
        }

        @Override
        public boolean use(Player player, Player ignored) {
            List<Bee> fleet = drones.dronesOf(player);
            if (fleet.isEmpty()) {
                messages.send(player, "ability.drones.none-out");
                return false;
            }
            LivingEntity target = lookedAtLiving(player);
            if (target == null) {
                messages.send(player, "ability.drones.nothing-there");
                return false;
            }
            Aggression mode = drones.fleetOf(player).aggression();
            if (target instanceof Player && !mode.allowsPlayers()) {
                // Refused rather than silently ignored. A player who has set their drones to
                // leave people alone and then orders an attack on one has contradicted
                // themselves, and should be told which half won.
                messages.send(player, "ability.drones.not-people");
                return false;
            }

            DroneFleet state = drones.fleetOf(player);
            for (Bee bee : fleet) {
                if (!bee.equals(state.controlled())) {
                    state.finish(bee);
                    bee.setTarget(target);
                }
            }
            messages.send(player, "ability.drones.attacking",
                    Messages.placeholder("target", describe(target)));
            return true;
        }
    }

    // --------------------------------------------------------------------------- 3. bring

    /** Point at something and have it hauled over to you. */
    private final class Bring implements Power {

        @Override
        public int slot() {
            return 3;
        }

        @Override
        public String id() {
            return "bring";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("drones.bring-cooldown", 8);
        }

        @Override
        public boolean use(Player player, Player ignored) {
            DroneFleet fleet = drones.fleetOf(player);
            if (fleet.isEmpty()) {
                messages.send(player, "ability.drones.none-out");
                return false;
            }
            Entity cargo = lookedAtEntity(player);
            if (cargo == null) {
                messages.send(player, "ability.drones.nothing-there");
                return false;
            }
            if (cargo instanceof Player && !fleet.aggression().allowsPlayers()) {
                messages.send(player, "ability.drones.not-people");
                return false;
            }
            Bee bee = fleet.idleDrone();
            if (bee == null) {
                messages.send(player, "ability.drones.all-busy");
                return false;
            }

            fleet.assign(bee, new DroneFleet.Job.Carry(cargo.getUniqueId()));
            messages.send(player, "ability.drones.bringing",
                    Messages.placeholder("target", describe(cargo)));
            return true;
        }
    }

    // ---------------------------------------------------------------------- 4. mine/place

    /**
     * Send one out to break the block you are looking at - or to put one there.
     *
     * <p>Which of the two it is depends on whether the drone is carrying anything, and that
     * is deliberately the only thing that decides it. A separate command for placing would be
     * a seventh power for what is really one idea: the drone goes to the block you picked and
     * does the obvious thing with what it has in its hands.
     */
    private final class Work implements Power {

        @Override
        public int slot() {
            return 4;
        }

        @Override
        public String id() {
            return "work";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("drones.work-cooldown", 1);
        }

        @Override
        public boolean use(Player player, Player ignored) {
            DroneFleet fleet = drones.fleetOf(player);
            if (fleet.isEmpty()) {
                messages.send(player, "ability.drones.none-out");
                return false;
            }
            int reach = (int) tuning.get("drones.work-range", 24.0);
            Block block = player.getTargetBlockExact(reach);
            if (block == null || block.getType().isAir()) {
                messages.send(player, "ability.drones.no-block");
                return false;
            }
            Bee bee = fleet.idleDrone();
            if (bee == null) {
                messages.send(player, "ability.drones.all-busy");
                return false;
            }

            var equipment = bee.getEquipment();
            boolean carrying = equipment != null && equipment.getHelmet() != null
                    && !equipment.getHelmet().getType().isAir();

            if (carrying) {
                BlockFace face = faceLookedAt(player, reach);
                fleet.assign(bee, new DroneFleet.Job.Place(block.getLocation(), face));
                messages.send(player, "ability.drones.placing");
            } else {
                fleet.assign(bee, new DroneFleet.Job.Mine(block.getLocation()));
                messages.send(player, "ability.drones.mining",
                        Messages.placeholder("block",
                                block.getType().name().toLowerCase(java.util.Locale.ROOT)));
            }
            return true;
        }
    }

    // ------------------------------------------------------------------------- 5. control

    /**
     * Look through one of them.
     *
     * <p>Using it again walks to the next drone and lets go after the last, so one number
     * does entering, swapping and leaving. With a single drone that is a plain toggle; with
     * five it is a tour.
     */
    private final class Control implements Power {

        @Override
        public int slot() {
            return 5;
        }

        @Override
        public String id() {
            return "control";
        }

        @Override
        public long cooldownSeconds() {
            return (long) tuning.get("drones.control-cooldown", 2);
        }

        @Override
        public boolean use(Player player, Player ignored) {
            DroneFleet fleet = drones.fleetOf(player);
            if (fleet.isEmpty()) {
                messages.send(player, "ability.drones.none-out");
                return false;
            }
            if (!drones.control().isPiloting(player)) {
                return drones.control().take(player, fleet, 0);
            }
            if (fleet.advance()) {
                return drones.control().take(player, fleet, fleet.controllingIndex());
            }
            drones.control().release(player, fleet);
            return true;
        }
    }

    // --------------------------------------------------------------------- 6. temperament

    /**
     * What they are willing to do, cycled one step at a time.
     *
     * <p>Slot six and open from level one. It is the only power here that changes nothing in
     * the world when used, and the only one whose absence could get somebody else killed.
     */
    private final class Temperament implements Power {

        @Override
        public int slot() {
            return 6;
        }

        @Override
        public int unlockedAt() {
            return 1;
        }

        @Override
        public String id() {
            return "temperament";
        }

        @Override
        public long cooldownSeconds() {
            return 0;
        }

        @Override
        public boolean use(Player player, Player ignored) {
            Aggression next = drones.fleetOf(player).aggression().next();
            drones.setAggression(player, next);
            messages.send(player, "ability.drones.temperament",
                    Messages.placeholder("mode",
                            messages.get("ability.drones.mode." + next.id())));
            return true;
        }
    }

    // -------------------------------------------------------------------------- helpers

    /** One drone per level, which is what makes the level count readable at a glance. */
    private int allowedFor(Player player) {
        return Math.max(1, drones.levelOf(player));
    }

    private LivingEntity lookedAtLiving(Player player) {
        Entity found = lookedAtEntity(player);
        return found instanceof LivingEntity living ? living : null;
    }

    /** Whatever the player is aiming at, ignoring their own drones and themselves. */
    private Entity lookedAtEntity(Player player) {
        double range = tuning.get("drones.command-range", 32.0);
        RayTraceResult hit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), player.getEyeLocation().getDirection(), range, 0.6,
                entity -> !entity.equals(player) && !drones.fleetOf(player).owns(entity));
        return hit == null ? null : hit.getHitEntity();
    }

    /**
     * Which side of the block they are aiming at, so a placed block lands where expected.
     *
     * <p>Falls back to the top face. Placing into the ground would be worse than placing
     * somewhere slightly unintended.
     */
    private BlockFace faceLookedAt(Player player, int reach) {
        RayTraceResult hit = player.rayTraceBlocks(reach);
        BlockFace face = hit == null ? null : hit.getHitBlockFace();
        return face == null ? BlockFace.UP : face;
    }

    private static String describe(Entity entity) {
        if (entity.customName() != null) {
            return entity.getName();
        }
        return entity.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
