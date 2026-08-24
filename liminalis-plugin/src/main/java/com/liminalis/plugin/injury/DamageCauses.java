package com.liminalis.plugin.injury;

import com.liminalis.core.injury.DamageCategory;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * Which of Minecraft's damage causes is which kind of harm.
 *
 * <p>A pure function on its own, deliberately. It was a private method inside
 * {@link InjuryService} taking a whole event, which meant the single most checkable claim the
 * injury system makes - <em>the wound matches the thing that caused it</em> - could not be
 * looked at without a running server and a player willing to be set on fire. Now the whole
 * table can be printed and read.
 *
 * <p><strong>The switch has no {@code default} arm and must never grow one.</strong> There was
 * one, sweeping everything unnamed into {@link DamageCategory#OTHER}, and it read as tidy
 * while being the opposite: a Warden's shout, a witch's potion, a cactus, powder snow and a
 * wither's decay all landed in the same bucket, and that bucket had nothing in it. Listing
 * every cause by name costs a few lines and turns the next Minecraft version's new damage
 * cause into a compile error - a conversation, rather than a silent hole somebody notices
 * three months in.
 */
public final class DamageCauses {

    private DamageCauses() {
    }

    /**
     * The kind of harm a cause does, for everything except a melee swing.
     *
     * <p>Melee is the one case this cannot answer alone: whether being hit by a player cuts
     * or crushes depends on what was in their hand, so {@link InjuryService} resolves it from
     * the event and this returns {@code null} to say so.
     *
     * @return the category, or null when the answer depends on the weapon
     */
    public static DamageCategory categoryOf(DamageCause cause) {
        return switch (cause) {
            // Depends on what they were holding; InjuryService decides.
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> null;

            // Arrows, tridents, shulker bullets - and thorns and cactus, which puncture for
            // exactly the same reason even though nothing was fired at you.
            case PROJECTILE, THORNS, CONTACT -> DamageCategory.PIERCING;

            case FALL -> DamageCategory.FALLING;

            // Lightning belongs here rather than with explosions: it burns you.
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE, MELTING, LIGHTNING ->
                    DamageCategory.BURNING;

            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> DamageCategory.EXPLOSIVE;

            // Weight, walls and pressure. A Warden's shout is a blow you cannot block, which
            // is what crushing means here - and concussion is exactly the right wound for it.
            case FALLING_BLOCK, FLY_INTO_WALL, SUFFOCATION, CRAMMING, SONIC_BOOM ->
                    DamageCategory.CRUSHING;

            case FREEZE -> DamageCategory.FROST;

            // Harm that works from the inside, whatever put it there.
            case POISON, WITHER, MAGIC, DRAGON_BREATH -> DamageCategory.WITHERING;

            // No shape of its own: drowning, starving, the void, the border. Also the two
            // administrative causes, which never reach the roster anyway because a blow that
            // kills is never allowed to wound.
            case DROWNING, STARVATION, VOID, WORLD_BORDER, DRYOUT, SUICIDE, KILL, CUSTOM ->
                    DamageCategory.OTHER;
        };
    }
}
