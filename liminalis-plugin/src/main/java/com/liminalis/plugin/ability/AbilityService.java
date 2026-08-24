package com.liminalis.plugin.ability;

import com.liminalis.core.ability.AbilityProgression;
import com.liminalis.core.ability.TierRequirement;
import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.Debug;
import com.liminalis.plugin.config.ConfigService;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.ModifierService;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.rescue.RescueService;
import com.liminalis.plugin.singularity.SingularityResidue;
import com.liminalis.plugin.text.Messages;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;

/**
 * Drives abilities: their progress, their tiers, and the residue that hurries them along.
 *
 * <p>Tier-ups are checked whenever progress moves rather than on a timer, so the moment
 * somebody crosses a threshold they are told. An ability that quietly became stronger and
 * only revealed it the next time the player happened to use it would waste the best moment
 * it has.
 */
public final class AbilityService implements Listener {

    private final JavaPlugin plugin;
    private final ConfigService config;
    private final ProfileManager profiles;
    private final ModifierRegistry registry;
    private final ModifierService modifiers;
    private final RescueService rescue;
    private final Messages messages;
    private final Debug debug;

    public AbilityService(JavaPlugin plugin,
                          ConfigService config,
                          ProfileManager profiles,
                          ModifierRegistry registry,
                          ModifierService modifiers,
                          RescueService rescue,
                          Messages messages,
                          Debug debug) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
        this.rescue = Objects.requireNonNull(rescue, "rescue");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.debug = Objects.requireNonNull(debug, "debug");
    }

    // ---------------------------------------------------------------------------- using

    /**
     * Right-clicking another player: the Priest's whole interface.
     *
     * <p>Sneaking treats a mortal wound, plain right-click heals. Runs at {@code HIGH} and
     * stands aside while the player is mid-rescue, so taking somebody's hand in the grey is
     * never mistaken for laying hands on them.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractPlayer(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || rescue.isCrossed(event.getPlayer().getUniqueId())
                || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player user = event.getPlayer();
        Optional<PriestAbility> priest = abilityOf(user, PriestAbility.class);
        if (priest.isEmpty()) {
            return;
        }
        // Bare-handed only, so a priest can still hand somebody an item.
        if (!user.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }

        int tier = tierOf(user);
        boolean handled = user.isSneaking() && tier >= 3
                ? priest.get().treat(user, target)
                : priest.get().layHands(user, target);

        if (handled) {
            event.setCancelled(true);
            checkForTierUp(user);
        }
    }

    /** Holy weight behind a blow. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        for (Modifier modifier : modifiers.attachedTo(attacker)) {
            if (!(modifier instanceof com.liminalis.plugin.modifier.capability.DamageDealer dealer)) {
                continue;
            }
            double before = event.getDamage();
            double after = dealer.adjustOutgoing(attacker, event.getEntity(), before);
            if (after != before) {
                event.setDamage(after);
                debug.log(() -> attacker.getName() + " dealt " + before + " -> " + after
                        + " via " + modifier.id());
            }
        }
    }

    /** Credits a kill toward whatever the killer's ability counts. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        Entity victim = event.getEntity();
        abilityOf(killer, PriestAbility.class)
                .ifPresent(priest -> priest.recordFelled(killer, victim));
        checkForTierUp(killer);
    }

    // ------------------------------------------------------------------------ accelerant

    /**
     * Spending residue to hurry an ability along.
     *
     * <p>This is why residue exists as a currency at all. An ability gated behind something
     * its owner rarely does - a priest who never fights undead, say - would otherwise leave
     * them staring at a tier they can read about and never reach.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpendResidue(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                    && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (!SingularityResidue.is(plugin, held) || !player.isSneaking()) {
            return;
        }
        event.setCancelled(true);

        PlayerProfile profile = profiles.of(player);
        Optional<Ability> ability = abilityOf(player);
        if (ability.isEmpty()) {
            messages.send(player, "ability.none-to-feed");
            return;
        }

        Optional<TierRequirement> next = AbilityProgression.nextRequirement(
                ability.get().tiers(), profile.abilityProgress());
        if (next.isEmpty()) {
            messages.send(player, "ability.already-complete");
            return;
        }

        int perShard = (int) config.get().abilities().progressPerResidue();
        int gained = AbilityProgression.progressFromResidue(1, perShard);
        held.subtract();
        profile.addAbilityProgress(next.get().counterKey(), gained);
        profiles.saveNow(profile);

        messages.send(player, "ability.fed",
                Messages.placeholder("amount", gained));
        checkForTierUp(player);
    }

    // ---------------------------------------------------------------------------- tiers

    /**
     * Recomputes a player's tier and announces any gain.
     *
     * <p>Cheap enough to call after anything that could have moved a counter. Reads the tier
     * from the counters rather than trusting the stored value, so a stored tier that drifted
     * - through an admin edit, or a rebalanced threshold in config - corrects itself.
     */
    public void checkForTierUp(Player player) {
        PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
        Optional<Ability> ability = abilityOf(player);
        if (profile == null || ability.isEmpty()) {
            return;
        }

        int earned = AbilityProgression.unlockedTier(
                ability.get().tiers(), profile.abilityProgress());
        if (earned == profile.abilityTier()) {
            return;
        }

        int before = profile.abilityTier();
        profile.setAbilityTier(earned);
        profiles.saveNow(profile);
        modifiers.applyFromProfile(player);

        if (earned > before) {
            messages.send(player, "ability.tier-gained",
                    Messages.placeholder("tier", earned),
                    Messages.placeholder("granted",
                            (net.kyori.adventure.text.Component)
                                    messages.get(ability.get().tierKey(earned))));
        }
        debug.log(() -> player.getName() + " ability tier " + before + " -> " + earned);
    }

    // -------------------------------------------------------------------------- lookup

    public Optional<Ability> abilityOf(Player player) {
        return profiles.resident(player.getUniqueId())
                .map(PlayerProfile::abilityId)
                .flatMap(registry::find)
                .filter(Ability.class::isInstance)
                .map(Ability.class::cast);
    }

    private <T extends Ability> Optional<T> abilityOf(Player player, Class<T> type) {
        return abilityOf(player).filter(type::isInstance).map(type::cast);
    }

    private int tierOf(Player player) {
        return profiles.resident(player.getUniqueId())
                .map(PlayerProfile::abilityTier)
                .orElse(1);
    }
}
