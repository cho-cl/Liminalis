package com.liminalis.plugin.combat;

import com.liminalis.core.combat.CombatRules;
import com.liminalis.core.combat.HealingKind;
import com.liminalis.core.combat.PlayerDamageSource;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Objects;
import java.util.UUID;

/**
 * The three world rules: player-versus-player damage is halved, food heals half as much, and
 * regeneration is worth slightly more.
 *
 * <p>All this class does is work out <em>who really caused</em> a piece of damage and what
 * kind of healing is happening, then hand those to {@link CombatRules}. The decisions live in
 * core where they are tested; the tracing of an arrow back to the player who fired it lives
 * here, because only the server knows that.
 *
 * <p>Runs at {@code HIGH} so ordinary plugins have already had their say, but before
 * {@code MONITOR} listeners that only observe the final number.
 */
public final class CombatListener implements Listener {

    private final ConfigService config;
    private final Debug debug;

    public CombatListener(ConfigService config, Debug debug) {
        this.config = Objects.requireNonNull(config, "config");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    // --------------------------------------------------------------------------- damage

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        PlayerDamageSource source = classify(event.getDamager(), victim);
        if (source == PlayerDamageSource.NONE) {
            return;
        }

        double before = event.getDamage();
        double after = CombatRules.adjustPlayerDamage(before, source, config.get().combat());
        if (after == before) {
            return;
        }

        // setDamage sets the BASE damage, so armour and enchantment reductions still apply
        // on top of the reduced figure. That is what makes this read as "the hit was softer"
        // rather than "armour stopped working".
        event.setDamage(after);
        debug.log(() -> String.format("pvp damage %s -> %s (%s, victim=%s)",
                trim(before), trim(after), source, victim.getName()));
    }

    /**
     * Traces damage back to a player, through whatever they used to deliver it.
     *
     * <p>Self-inflicted damage is deliberately not counted - shooting yourself with your own
     * arrow is not two players fighting, and halving it would just be a strange gift.
     */
    private PlayerDamageSource classify(Entity damager, Player victim) {
        if (damager instanceof Player attacker) {
            return isSomeoneElse(attacker.getUniqueId(), victim)
                    ? PlayerDamageSource.DIRECT : PlayerDamageSource.NONE;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player && isSomeoneElse(player.getUniqueId(), victim)
                    ? PlayerDamageSource.PROJECTILE : PlayerDamageSource.NONE;
        }
        if (damager instanceof Tameable pet && pet.isTamed()) {
            AnimalTamer owner = pet.getOwner();
            return owner != null && isSomeoneElse(owner.getUniqueId(), victim)
                    ? PlayerDamageSource.PET : PlayerDamageSource.NONE;
        }
        if (damager instanceof TNTPrimed tnt) {
            Entity primer = tnt.getSource();
            return primer instanceof Player player && isSomeoneElse(player.getUniqueId(), victim)
                    ? PlayerDamageSource.EXPLOSIVE : PlayerDamageSource.NONE;
        }
        // Not covered yet, because neither can be attributed without tracking who placed
        // them: end crystals and creepers a player deliberately ignited.
        return PlayerDamageSource.NONE;
    }

    private static boolean isSomeoneElse(UUID attacker, Player victim) {
        return !victim.getUniqueId().equals(attacker);
    }

    // -------------------------------------------------------------------------- healing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        HealingKind kind = classify(event.getRegainReason());
        if (kind == HealingKind.OTHER) {
            return;
        }

        double before = event.getAmount();
        double after = CombatRules.adjustHealing(before, kind, config.get().combat());
        if (after == before) {
            return;
        }

        event.setAmount(after);
        debug.log(() -> String.format("%s healing %s -> %s (%s)",
                kind, trim(before), trim(after), player.getName()));
    }

    private static HealingKind classify(EntityRegainHealthEvent.RegainReason reason) {
        return switch (reason) {
            // Regenerating because the hunger bar is full, and health straight from eating.
            case SATIATED, EATING -> HealingKind.FOOD;
            // The Regeneration effect, whether from a potion, a beacon or an ability.
            case MAGIC_REGEN -> HealingKind.REGENERATION;
            // Instant Health, golden apples, peaceful-mode regen and the rest keep their
            // vanilla values.
            default -> HealingKind.OTHER;
        };
    }

    private static String trim(double value) {
        return String.format("%.2f", value);
    }
}
