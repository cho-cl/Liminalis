package com.liminalis.plugin.injury;

import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.profile.ProfileManager;
import com.liminalis.plugin.text.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

/**
 * Takes the offhand away from anyone who has lost the arm it belonged to.
 *
 * <p>Attribute penalties alone were not enough to make a lost arm feel like a lost arm.
 * Fewer points of attack damage is a number; not being able to hold a shield is a limb. This
 * is the one injury whose cost is a capability rather than a statistic, and it is the reason
 * players will spend a life to be rid of it.
 *
 * <p>Enforced in three places because Minecraft has three ways into that slot: swapping with
 * F, dragging in the inventory, and anything else that ends with something sitting there -
 * which the sweep catches. The item is always returned to the player, never destroyed. A
 * mortal wound should cost them their arm, not their shield.
 */
public final class OffhandBlocker implements Listener {

    /** The wound that costs an arm. */
    public static final String LOST_ARM = "lost_arm";

    /** How often the slot is checked for anything that slipped past the events. */
    private static final long SWEEP_TICKS = 20L;

    private final JavaPlugin plugin;
    private final ProfileManager profiles;
    private final Messages messages;

    private BukkitTask sweepTask;

    public OffhandBlocker(JavaPlugin plugin, ProfileManager profiles, Messages messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    public void start() {
        sweepTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::sweep, SWEEP_TICKS, SWEEP_TICKS);
    }

    public void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    /** The common case: pressing F. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!hasLostArm(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        messages.send(event.getPlayer(), "injury.lost_arm.refused");
    }

    /** Dragging something into the slot from the inventory screen. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !hasLostArm(player)) {
            return;
        }
        boolean intoOffhand = event.getSlotType()
                == org.bukkit.event.inventory.InventoryType.SlotType.QUICKBAR
                && event.getSlot() == 40;
        if (intoOffhand || event.getHotbarButton() == 40) {
            event.setCancelled(true);
            messages.send(player, "injury.lost_arm.refused");
        }
    }

    /**
     * Catches anything the events missed, and empties the slot for someone who was already
     * holding a shield when the arm came off.
     */
    private void sweep() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!hasLostArm(player)) {
                continue;
            }
            PlayerInventory inventory = player.getInventory();
            ItemStack offhand = inventory.getItemInOffHand();
            if (offhand.getType().isAir()) {
                continue;
            }

            inventory.setItemInOffHand(null);
            // Back to the player, never destroyed. The wound costs them an arm, not a shield.
            inventory.addItem(offhand).values().forEach(leftover ->
                    player.getWorld().dropItem(player.getLocation(), leftover));
            messages.send(player, "injury.lost_arm.dropped");
        }
    }

    private boolean hasLostArm(Player player) {
        return profiles.resident(player.getUniqueId())
                .map(profile -> profile.hasInjury(LOST_ARM))
                .orElse(false);
    }

    /** Exposed so other code can ask without duplicating the lookup. */
    public boolean isDisarmed(PlayerProfile profile) {
        return profile != null && profile.hasInjury(LOST_ARM);
    }

    /** Never used for the main hand - losing an arm should not stop you defending yourself. */
    static EquipmentSlot blockedSlot() {
        return EquipmentSlot.OFF_HAND;
    }
}
