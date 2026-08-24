package com.liminalis.plugin.hud;

import com.liminalis.core.injury.ActiveInjury;
import com.liminalis.core.injury.InjurySeverity;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.injury.Injury;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.singularity.SingularityService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one thing that writes to a player's action bar.
 *
 * <p>That is the entire reason this class exists as a single service rather than as two. The
 * injury HUD and the Singularity proximity warning both want the same strip of screen, and
 * two systems each calling {@code sendActionBar} on a timer do not politely take turns - they
 * flicker against each other at whatever rate they happen to tick, and the result looks
 * broken in a way that is genuinely hard to diagnose. Everything that wants the action bar
 * gets composed here instead.
 *
 * <p>The design brief called the HUD quiet: no permanent overlay of lives or traits, because
 * a server about not quite knowing what is happening to you should not have a status panel.
 * So this draws nothing at all most of the time. It speaks when you are hurt, and when
 * something is close.
 */
public final class PlayerHud {

    private static final Key HUD_FONT = Key.key("liminalis", "hud");

    /**
     * Glyph codepoints, matching tools/build_icons.py CODEPOINTS.
     *
     * <p>Checked against the registry on startup, so adding an injury without an icon is a
     * named warning rather than an empty box nobody can explain.
     */
    private static final Map<String, String> INJURY_GLYPHS = Map.of(
            "bleeding", "\uE001",
            "sprained_ankle", "\uE002",
            "burns", "\uE003",
            "concussion", "\uE004",
            "punctured_lung", "\uE005",
            "lost_arm", "\uE006",
            "broken_legs", "\uE007",
            "charred", "\uE008");

    private static final String PRESENCE_GLYPH = "\uE009";

    /** Twice a second. Fast enough to feel live, slow enough to be free. */
    private static final long REFRESH_TICKS = 10L;

    /** Colours for how close the nearest thing is, far to near. */
    private static final TextColor DISTANT = TextColor.color(0x6E6A82);
    private static final TextColor NEAR = TextColor.color(0x9A7FBF);
    private static final TextColor CLOSE = TextColor.color(0xC85A5A);

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final SingularityService singularity;

    /** Who already knows something is near, so the sound cue fires once per approach. */
    private final Map<UUID, Boolean> aware = new ConcurrentHashMap<>();

    private BukkitTask task;

    public PlayerHud(JavaPlugin plugin,
                     ConfigService config,
                     ProfileManager profiles,
                     ModifierRegistry registry,
                     SingularityService singularity) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.singularity = Objects.requireNonNull(singularity, "singularity");
    }

    public void start() {
        warnAboutMissingGlyphs();
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::draw, REFRESH_TICKS, REFRESH_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        aware.clear();
    }

    private void warnAboutMissingGlyphs() {
        registry.ofType(ModifierType.INJURY).stream()
                .map(Modifier::id)
                .filter(id -> !INJURY_GLYPHS.containsKey(id))
                .forEach(id -> plugin.getLogger().warning(
                        "Injury '" + id + "' has no HUD icon. Add it to CODEPOINTS in"
                                + " tools/build_icons.py, draw a grid for it, re-run the"
                                + " script, then map it in PlayerHud.INJURY_GLYPHS."));
    }

    // ------------------------------------------------------------------------- drawing

    private void draw() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
            if (profile == null) {
                continue;
            }

            double nearest = nearestSingularity(player);
            boolean sensed = nearest >= 0;
            announceApproach(player, sensed);

            if (profile.injuries().isEmpty() && !sensed) {
                // Nothing to say. The quiet state is the common one, and staying silent is
                // what makes the other states mean anything.
                continue;
            }
            player.sendActionBar(render(profile, nearest));
        }
    }

    private Component render(PlayerProfile profile, double nearest) {
        TextComponent.Builder line = Component.text();
        boolean wrote = false;

        for (ActiveInjury injury : ordered(profile)) {
            if (wrote) {
                line.append(Component.space());
            }
            wrote = true;
            line.append(injuryIcon(injury));
        }

        if (nearest >= 0) {
            if (wrote) {
                line.append(Component.text("   "));
            }
            line.append(presence(nearest));
        }
        return line.build();
    }

    /** Mortal wounds first, so a lost arm is never buried behind three scratches. */
    private List<ActiveInjury> ordered(PlayerProfile profile) {
        return profile.injuries().stream()
                .sorted(java.util.Comparator.comparing(injury -> !isMortal(injury.id())))
                .toList();
    }

    private Component injuryIcon(ActiveInjury injury) {
        String glyph = INJURY_GLYPHS.get(injury.id());
        boolean mortal = isMortal(injury.id());

        if (glyph == null) {
            // No icon, or the player declined the pack. Words beat an empty box.
            return Component.text(injury.id().replace('_', ' '),
                    mortal ? NamedTextColor.DARK_RED : NamedTextColor.GRAY);
        }
        return Component.text(glyph).font(HUD_FONT);
    }

    /**
     * The warning.
     *
     * <p>Deliberately says how close, not which way. Knowing something is thirty blocks off
     * and not knowing from where is the version of this that makes people stop and look
     * around; an arrow pointing at it would just be a compass.
     */
    private Component presence(double distance) {
        var settings = config.get().singularity();
        double range = settings.senseRange();
        double closeness = range <= 0 ? 0 : 1.0 - Math.min(1.0, distance / range);

        TextColor colour = closeness > 0.66 ? CLOSE : closeness > 0.33 ? NEAR : DISTANT;
        return Component.text(PRESENCE_GLYPH).font(HUD_FONT).color(colour)
                .append(Component.text("  " + Math.round(distance) + "m", colour));
    }

    // ------------------------------------------------------------------------ proximity

    /**
     * Distance to the nearest Singularity creature, or -1 if none is within sensing range.
     *
     * <p>Uses the player's own nearby-entity list rather than scanning the world, so the cost
     * is bounded by whatever is already loaded around them.
     */
    private double nearestSingularity(Player player) {
        double range = config.get().singularity().senseRange();
        if (range <= 0) {
            return -1;
        }

        double best = Double.MAX_VALUE;
        for (Entity nearby : player.getNearbyEntities(range, range, range)) {
            if (singularity.typeIdOf(nearby) == null) {
                continue;
            }
            best = Math.min(best, player.getLocation().distance(nearby.getLocation()));
        }
        return best == Double.MAX_VALUE ? -1 : best;
    }

    /**
     * A single sound the moment something comes into range, and nothing after.
     *
     * <p>Repeating it while the creature stays nearby would turn dread into an alarm clock.
     * The flag resets once they are clear, so wandering back into range says it again.
     */
    private void announceApproach(Player player, boolean sensed) {
        Boolean was = aware.put(player.getUniqueId(), sensed);
        if (sensed && !Boolean.TRUE.equals(was)) {
            player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD,
                    0.6f, 0.55f);
        }
    }

    private boolean isMortal(String injuryId) {
        return registry.find(injuryId)
                .filter(Injury.class::isInstance)
                .map(Injury.class::cast)
                .map(injury -> injury.severity() == InjurySeverity.MORTAL_WOUND)
                .orElse(false);
    }
}
