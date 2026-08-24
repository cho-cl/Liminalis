package com.liminalis.plugin.injury;

import com.liminalis.core.injury.ActiveInjury;
import com.liminalis.core.injury.InjurySeverity;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.profile.ProfileManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Draws a player's wounds on screen, and nothing else.
 *
 * <p>The design called for a quiet HUD: no permanent overlay of lives or traits, because a
 * server about not knowing quite what is happening to you should not have a status panel.
 * Injuries are the exception, and they earn it - a player needs to know at a glance that they
 * are still bleeding, and needs to be unable to forget that their arm is gone.
 *
 * <p>Icons come from the resource pack font {@code liminalis:hud}. When a player has not
 * accepted the pack, the private-use codepoints render as blank boxes, so the HUD falls back
 * to writing the wound names instead. That check is worth having: a rescue party is exactly
 * the sort of group to include one person who declined the download.
 */
public final class InjuryHud {

    /** Font declared by the resource pack. */
    private static final Key HUD_FONT = Key.key("liminalis", "hud");

    /**
     * Codepoints, matching pack/assets/liminalis/font/hud.json.
     *
     * <p>If these two ever disagree, players see empty boxes. They are asserted against the
     * registered injuries on startup so a mismatch is a log line rather than a mystery.
     */
    private static final Map<String, String> GLYPHS = Map.of(
            "bleeding", "\uE001",
            "sprained_ankle", "\uE002",
            "burns", "\uE003",
            "concussion", "\uE004",
            "punctured_lung", "\uE005",
            "lost_arm", "\uE006",
            "broken_legs", "\uE007",
            "charred", "\uE008");

    /** Twice a second. Fast enough to feel live, slow enough to be free. */
    private static final long REFRESH_TICKS = 10L;

    private final JavaPlugin plugin;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;

    private BukkitTask task;

    public InjuryHud(JavaPlugin plugin, ProfileManager profiles, ModifierRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.registry = Objects.requireNonNull(registry, "registry");
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
    }

    /**
     * Checks every registered injury has an icon.
     *
     * <p>Adding a wound and forgetting its glyph would show players an empty box with no
     * indication of what went wrong. This turns that into a startup warning naming the
     * injury and the file that needs drawing.
     */
    private void warnAboutMissingGlyphs() {
        registry.ofType(com.liminalis.plugin.modifier.ModifierType.INJURY).stream()
                .map(com.liminalis.plugin.modifier.Modifier::id)
                .filter(id -> !GLYPHS.containsKey(id))
                .forEach(id -> plugin.getLogger().warning(
                        "Injury '" + id + "' has no HUD icon. Add a glyph to"
                                + " pack/assets/liminalis/font/hud.json and a texture at"
                                + " textures/font/injury/" + id + ".png, then map it in"
                                + " InjuryHud.GLYPHS."));
    }

    private void draw() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
            if (profile == null || profile.injuries().isEmpty()) {
                continue;
            }
            player.sendActionBar(render(profile));
        }
    }

    /**
     * Builds the action bar.
     *
     * <p>Mortal wounds are listed first and tinted, so the thing that will not heal on its
     * own is never buried behind three scratches.
     */
    private Component render(PlayerProfile profile) {
        TextComponent.Builder line = Component.text();
        boolean first = true;

        for (ActiveInjury injury : ordered(profile)) {
            if (!first) {
                line.append(Component.space());
            }
            first = false;
            line.append(icon(injury));
        }
        return line.build();
    }

    /** Mortal wounds first, then the rest in the order they were taken. */
    private java.util.List<ActiveInjury> ordered(PlayerProfile profile) {
        return profile.injuries().stream()
                .sorted(java.util.Comparator.comparing(injury -> !isMortal(injury.id())))
                .toList();
    }

    private Component icon(ActiveInjury injury) {
        String glyph = GLYPHS.get(injury.id());
        boolean mortal = isMortal(injury.id());

        if (glyph == null) {
            // No icon, or the player declined the pack. Say it in words rather than showing
            // a box - an unreadable HUD is worse than a plain one.
            return Component.text(readableName(injury.id()),
                    mortal ? NamedTextColor.DARK_RED : NamedTextColor.GRAY);
        }
        return Component.text(glyph).font(HUD_FONT);
    }

    private boolean isMortal(String injuryId) {
        return registry.find(injuryId)
                .filter(Injury.class::isInstance)
                .map(Injury.class::cast)
                .map(injury -> injury.severity() == InjurySeverity.MORTAL_WOUND)
                .orElse(false);
    }

    private static String readableName(String injuryId) {
        return injuryId.replace('_', ' ');
    }

    /** Exposed for the admin command, so a mismatch can be checked without a client. */
    public Optional<String> glyphFor(String injuryId) {
        return Optional.ofNullable(GLYPHS.get(injuryId));
    }
}
