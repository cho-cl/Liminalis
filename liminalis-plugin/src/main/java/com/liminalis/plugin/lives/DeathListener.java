package com.liminalis.plugin.lives;

import com.liminalis.core.combat.PlayerDamageSource;
import com.liminalis.core.lives.DeathCause;
import com.liminalis.core.lives.DeathVerdict;
import com.liminalis.core.lives.LifeRules;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.combat.PlayerDamageAttribution;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Spends a life when a player dies, and sends them to Limbo when there are none left.
 *
 * <p>Deaths are the one state change that must never be lost, so the profile is written
 * synchronously here rather than queued. A crash in the moment after someone spends their
 * last life would otherwise hand it back, and the player would have no way to know.
 */
public final class DeathListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final ProfileManager profiles;
    private final Messages messages;
    private final Debug debug;

    public DeathListener(JavaPlugin plugin,
                         ConfigService config,
                         ProfileManager profiles,
                         Messages messages,
                         Debug debug) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerProfile profile = profiles.of(player);

        DeathCause cause = classify(player);
        DeathVerdict verdict = LifeRules.recordDeath(
                profile, cause, config.get().lives(), System.currentTimeMillis());

        debug.log(() -> player.getName() + " died (" + cause + ") -> " + verdict
                + ", lives now " + profile.livesRemaining());

        if (verdict != DeathVerdict.IGNORED) {
            profiles.saveNow(profile);
        }

        switch (verdict) {
            case LIFE_SPENT -> announceLifeSpent(player, profile);
            case FELL_TO_LIMBO -> announceFall(player);
            case IGNORED -> {
                // Nothing to say. An excused death should feel like nothing happened.
            }
        }
    }

    private void announceLifeSpent(Player player, PlayerProfile profile) {
        int remaining = profile.livesRemaining();
        messages.send(player, remaining == 1 ? "lives.last-one" : "lives.spent",
                Messages.placeholder("lives", remaining));
    }

    /**
     * The player is still on the death screen at this point. Their journey to Limbo happens
     * when they respawn, which {@code LimboService} handles by reading the profile - so
     * nothing here needs to move them.
     */
    private void announceFall(Player player) {
        messages.send(player, "limbo.fell");
        plugin.getServer().sendMessage(
                messages.get("limbo.fell-broadcast", Messages.placeholder("player", player.getName())));
    }

    /**
     * Reduces Minecraft's thirty-odd damage causes to the four distinctions that matter.
     *
     * <p>Uses the same attacker-tracing as the combat rules, so an arrow, a wolf or a stick
     * of TNT that counts as PvP for damage also counts as PvP for deaths. Two different
     * answers to "was that a player kill?" would be indefensible.
     */
    private DeathCause classify(Player player) {
        EntityDamageEvent last = player.getLastDamageCause();
        if (last == null) {
            return DeathCause.UNKNOWN;
        }

        EntityDamageEvent.DamageCause damageCause = last.getCause();
        if (damageCause == EntityDamageEvent.DamageCause.KILL
                || damageCause == EntityDamageEvent.DamageCause.CUSTOM) {
            // /kill and plugin-dealt damage. Never the player's fault, never costs a life.
            return DeathCause.ADMIN;
        }

        Player killer = player.getKiller();
        if (killer != null && !killer.getUniqueId().equals(player.getUniqueId())) {
            return DeathCause.PLAYER;
        }

        if (last instanceof EntityDamageByEntityEvent byEntity) {
            PlayerDamageSource source =
                    PlayerDamageAttribution.of(byEntity.getDamager(), player);
            if (source != PlayerDamageSource.NONE) {
                return DeathCause.PLAYER;
            }
            if (byEntity.getDamager() instanceof LivingEntity) {
                return DeathCause.MOB;
            }
        }

        return DeathCause.ENVIRONMENT;
    }
}
