package com.liminalis.plugin.limbo;

import com.liminalis.core.limbo.Whisper;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * The dead talk to each other, and once in a while the living almost hear them.
 *
 * <p>Limbo chat is a closed room: what is said there reaches the other people trapped there
 * and nobody else. The exception is the whisper - a small chance that a message bleeds
 * through to everyone alive, worn down to its shape. That is the only channel the dead have
 * to the living, and it is deliberately unreliable, because the pressure to rescue someone
 * should come from half-heard fragments rather than from them simply asking.
 */
public final class LimboChatListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final ProfileManager profiles;
    private final Messages messages;
    private final Debug debug;

    /** How much of a whispered message survives the crossing. */
    private static final double WHISPER_CLARITY = 0.35;

    private final Random random = new Random();

    public LimboChatListener(JavaPlugin plugin,
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player speaker = event.getPlayer();
        if (!isInLimbo(speaker.getUniqueId())) {
            return;
        }

        keepOnlyTheDead(event);
        String spoken = PlainTextComponentSerializer.plainText().serialize(event.message());

        event.renderer((source, displayName, message, viewer) ->
                messages.get("limbo.chat",
                        Messages.placeholder("player", speaker.getName()),
                        Messages.placeholder("message", spoken)));

        maybeWhisper(speaker, spoken);
    }

    /**
     * Strips every living viewer from the recipient list.
     *
     * <p>Console is intentionally left in: an operator needs to be able to read what is being
     * said in Limbo, not least to know when somebody has been stuck there too long.
     */
    private void keepOnlyTheDead(AsyncChatEvent event) {
        Iterator<Audience> viewers = event.viewers().iterator();
        while (viewers.hasNext()) {
            Audience viewer = viewers.next();
            if (viewer instanceof Player player && !isInLimbo(player.getUniqueId())) {
                viewers.remove();
            }
        }
    }

    private void maybeWhisper(Player speaker, String spoken) {
        double chance = config.get().limbo().whisperChance();
        if (chance <= 0.0 || random.nextDouble() >= chance) {
            return;
        }

        String heard = Whisper.garble(spoken, WHISPER_CLARITY, random);
        Component whisper = messages.get("limbo.whisper", Messages.placeholder("message", heard));

        for (Player living : plugin.getServer().getOnlinePlayers()) {
            if (!isInLimbo(living.getUniqueId())) {
                living.sendMessage(whisper);
            }
        }
        debug.log(() -> "whisper from " + speaker.getName() + ": " + heard);
    }

    /**
     * Reads limbo state without assuming the player is resident.
     *
     * <p>Chat is async and viewers can include someone mid-disconnect, so this must not be
     * the throwing accessor.
     */
    private boolean isInLimbo(UUID id) {
        Optional<PlayerProfile> profile = profiles.resident(id);
        return profile.isPresent() && profile.get().inLimbo();
    }
}
