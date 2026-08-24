package com.liminalis.plugin.trait;

import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.core.roll.BoonKind;
import com.liminalis.core.roll.BoonOutcome;
import com.liminalis.core.roll.BoonRoller;
import com.liminalis.core.roll.RollCandidate;
import com.liminalis.core.roll.TraitRoller;
import com.liminalis.core.roll.TraitTier;
import com.liminalis.core.roll.WeightedEntry;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.boon.BoonAssignment;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.modifier.ModifierType;
import com.liminalis.plugin.boon.Boon;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Decides who a player is, the first time they ever join.
 *
 * <p>Runs exactly once per player, guarded by a flag on the profile that is written before
 * anything is announced. Rolling twice would be the worst bug this class could have: it would
 * silently replace somebody's identity, and the only evidence would be their confusion.
 *
 * <p>The reveal is delayed a moment so it lands after the join message and the server's own
 * noise, rather than scrolling past underneath it.
 */
public final class FirstJoinService implements Listener {

    /** Ticks to wait before telling the player what they are. */
    private static final long REVEAL_DELAY_TICKS = 30L;

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final Messages messages;
    private final Debug debug;
    private final Random random = new Random();
    private final BoonAssignment assignment;

    public FirstJoinService(JavaPlugin plugin,
                            ConfigService config,
                            ProfileManager profiles,
                            ModifierRegistry registry,
                            ModifierService modifiers,
                            Messages messages,
                            Debug debug) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.debug = Objects.requireNonNull(debug, "debug");
        this.assignment = new BoonAssignment(this.registry);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = profiles.of(player);
        if (profile.firstJoinComplete()) {
            return;
        }

        List<String> rolled = new TraitRoller(candidates())
                .roll(config.get().traits().roll(), random);
        rolled.forEach(profile::addTrait);

        BoonOutcome boon = new BoonRoller(entriesOf(ModifierType.BLESSING),
                entriesOf(ModifierType.CURSE)).roll(config.get().boons(), random);
        switch (boon.kind()) {
            // Through BoonAssignment rather than set directly, so a boon that carries a life
            // with it - Thrice-Born - grants it here as well as when an admin hands it over.
            case BLESSING -> assignment.assign(profile, boon.id(), ModifierType.BLESSING);
            case CURSE -> assignment.assign(profile, boon.id(), ModifierType.CURSE);
            case NONE -> {
                // Most people. Nothing to record.
            }
        }

        // Written before anything else happens, so a crash between here and the reveal
        // cannot leave the player unrolled and hand them a second, different identity.
        profile.setFirstJoinComplete(true);
        profiles.saveNow(profile);

        // Re-attach from the profile so the new traits take effect now rather than on the
        // next login. Safe to call regardless of listener ordering - it rebuilds from scratch.
        modifiers.applyFromProfile(player);

        debug.log(() -> "first join roll for " + player.getName() + ": " + rolled
                + " boon=" + boon.kind() + (boon.id() == null ? "" : "/" + boon.id()));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> reveal(player, rolled, boon), REVEAL_DELAY_TICKS);
    }

    private void reveal(Player player, List<String> rolled, BoonOutcome boon) {
        if (!player.isOnline()) {
            return;
        }
        messages.send(player, "traits.reveal-header");

        if (rolled.isEmpty()) {
            // Only reachable if a build registers no traits at all. Say something rather
            // than leaving an empty header hanging.
            messages.send(player, "traits.reveal-none");
            return;
        }

        for (String id : rolled) {
            Modifier modifier = registry.find(id).orElse(null);
            if (modifier == null) {
                continue;
            }
            boolean singular = modifier instanceof Trait trait
                    && trait.tier() == TraitTier.SINGULARITY;
            announce(player, modifier,
                    singular ? "traits.reveal-singularity" : "traits.reveal-entry");
        }

        revealBoon(player, boon);
    }

    /**
     * Tells the player whether they were blessed or cursed.
     *
     * <p>Said plainly, because the design calls for players to be told everything on join.
     * The mystery in this server is meant to live in Limbo and the Singularity, not in
     * whether somebody can work out what their own curse does.
     */
    private void revealBoon(Player player, BoonOutcome boon) {
        if (boon.kind() == BoonKind.NONE) {
            return;
        }
        Modifier modifier = registry.find(boon.id()).orElse(null);
        if (modifier == null) {
            return;
        }
        announce(player, modifier, boon.kind() == BoonKind.BLESSING
                ? "boons.reveal-blessing" : "boons.reveal-curse");
    }

    private void announce(Player player, Modifier modifier, String key) {
        messages.send(player, key,
                Messages.placeholder("name", messages.get(modifier.nameKey())),
                Messages.placeholder("description",
                        (Component) messages.get(modifier.descriptionKey())));
    }

    /** Registered modifiers of a type, as weighted roll entries. */
    private List<WeightedEntry> entriesOf(ModifierType type) {
        return registry.ofType(type).stream()
                .filter(Boon.class::isInstance)
                .map(Boon.class::cast)
                .map(Boon::asEntry)
                .toList();
    }

    /** Every registered trait, as a roll table entry. */
    private List<RollCandidate> candidates() {
        return registry.ofType(ModifierType.TRAIT).stream()
                .filter(Trait.class::isInstance)
                .map(Trait.class::cast)
                .map(Trait::asCandidate)
                .toList();
    }
}
