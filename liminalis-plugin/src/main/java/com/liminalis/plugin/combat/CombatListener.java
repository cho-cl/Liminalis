package com.liminalis.plugin.combat;

import com.liminalis.core.combat.CombatRules;
import com.liminalis.core.combat.HealingKind;
import com.liminalis.core.combat.PlayerDamageSource;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.Objects;

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
        PlayerDamageSource source = PlayerDamageAttribution.of(event.getDamager(), victim);
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
