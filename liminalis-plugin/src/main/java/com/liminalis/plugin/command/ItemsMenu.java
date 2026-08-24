package com.liminalis.plugin.command;

import com.liminalis.plugin.ability.AbilityFocus;
import com.liminalis.plugin.rescue.ThresholdStone;
import com.liminalis.plugin.singularity.LoreBooks;
import com.liminalis.plugin.singularity.SingularityResidue;
import com.liminalis.plugin.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A chest of everything the plugin can hand out.
 *
 * <p>Exists because the alternative is remembering that residue comes from
 * {@code singularity residue}, stones are not obtainable by command at all, and each of the
 * five books has an id that is not quite its title. A menu turns all of that into looking at
 * a row of items and clicking one.
 *
 * <p>Clicks take a copy rather than the item itself, so the menu never empties and two
 * operators can have it open at once. Shift-click takes a full stack where that makes sense.
 */
public final class ItemsMenu implements Listener, InventoryHolder {

    private static final int SIZE = 27;

    private final JavaPlugin plugin;
    private final Messages messages;

    /** Parallel to the inventory: what each slot hands out when clicked. */
    private final List<ItemStack> contents = new ArrayList<>();

    private Inventory inventory;

    public ItemsMenu(JavaPlugin plugin, Messages messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Opens a freshly built menu. Rebuilt per open so a reload's new names show up. */
    public void openFor(Player player) {
        build();
        player.openInventory(inventory);
    }

    private void build() {
        contents.clear();
        inventory = plugin.getServer().createInventory(this, SIZE,
                Component.text("Liminalis", NamedTextColor.DARK_GRAY));

        add(SingularityResidue.create(plugin, messages, 16), "Residue",
                "The accelerant. Sneak-right-click to feed an ability.");
        add(ThresholdStone.create(plugin, messages), "Threshold Stone",
                "Opens a way into the grey. Single use.");
        add(AbilityFocus.create(plugin, messages, "priest", Material.STICK), "Healing Staff",
                "A Priest must hold this to use /ability.");

        // Books go on the second row, so the two functional items are not lost among them.
        while (contents.size() < 9) {
            contents.add(null);
        }
        for (LoreBooks.LoreBook book : LoreBooks.all()) {
            add(book.toItem(), book.title(), "Lore. Drops from the Singularity.");
        }

        for (int slot = 0; slot < contents.size() && slot < SIZE; slot++) {
            inventory.setItem(slot, contents.get(slot));
        }
    }

    /** Adds an item, annotating it so it is obvious this is an admin source. */
    private void add(ItemStack item, String label, String note) {
        ItemStack shown = item.clone();
        var meta = shown.getItemMeta();

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) {
            lore.addAll(Objects.requireNonNull(meta.lore()));
        }
        lore.add(Component.empty());
        lore.add(Component.text(note, NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Click to take a copy", NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        shown.setItemMeta(meta);

        inventoryLabel(shown, label);
        contents.add(shown);
    }

    private void inventoryLabel(ItemStack item, String label) {
        var meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            meta.displayName(Component.text(label, NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // -------------------------------------------------------------------------- clicks

    /**
     * Hands over a copy and cancels everything else.
     *
     * <p>Every interaction is cancelled unconditionally before anything else happens. A menu
     * that let an operator pull the display item out would empty itself, and one that let
     * them put something in would eat it on close.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ItemsMenu menu) || menu != this) {
            return;
        }
        event.setCancelled(true);

        HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player player) || event.getClickedInventory() != inventory) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        // The lore added for display would otherwise follow the item into the world.
        ItemStack given = stripMenuLore(clicked.clone());
        if (event.isShiftClick()) {
            given.setAmount(Math.min(given.getMaxStackSize(), given.getAmount() * 4));
        }

        player.getInventory().addItem(given).values().forEach(leftover ->
                player.getWorld().dropItem(player.getLocation(), leftover));
        player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.4f);
    }

    /** Dragging across a menu slot would otherwise bypass the click handler entirely. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ItemsMenu menu && menu == this) {
            event.setCancelled(true);
        }
    }

    /**
     * Removes the two explanatory lines the menu adds.
     *
     * <p>Only the trailing lines are dropped, so an item's own lore - the Threshold Stone
     * saying it is single use, for instance - survives into the player's inventory.
     */
    private ItemStack stripMenuLore(ItemStack item) {
        var meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore != null && lore.size() >= 3) {
            meta.lore(new ArrayList<>(lore.subList(0, lore.size() - 3)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
