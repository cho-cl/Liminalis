package com.liminalis.plugin.combat;

import com.liminalis.core.combat.PlayerDamageSource;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

/**
 * Traces damage back to the player who really caused it.
 *
 * <p>Lives in one place because two features depend on the same answer and they must never
 * disagree: Phase 1 halves damage between players, and Phase 2 decides whether a death was a
 * player kill. If an arrow counted as PvP for damage but not for deaths, the same fight would
 * be governed by two different rules.
 */
public final class PlayerDamageAttribution {

    private PlayerDamageAttribution() {
    }

    /**
     * Works out how {@code damager} traces back to a player, from {@code victim}'s point of
     * view.
     *
     * <p>Self-inflicted damage returns {@link PlayerDamageSource#NONE}: blowing yourself up
     * with your own TNT is not two players fighting.
     */
    public static PlayerDamageSource of(Entity damager, Player victim) {
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
}
