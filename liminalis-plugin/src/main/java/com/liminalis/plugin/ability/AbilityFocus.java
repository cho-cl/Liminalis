package com.liminalis.plugin.ability;

import com.liminalis.plugin.text.Messages;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * The object an ability is channelled through, and which has to be in hand to use it.
 *
 * <p>Requiring a focus does two things worth having. It makes an ability visible - you can
 * tell across a field that someone is a priest, because of what they are carrying - and it
 * makes using one a decision, since the hand holding it is not holding a sword.
 *
 * <p>Tagged in persistent data rather than recognised by name, so a player can rename theirs
 * in an anvil without breaking it, and cannot forge one by renaming a stick.
 */
public final class AbilityFocus {

    private static final String KEY = "ability_focus";
    private static final String ABILITY_KEY = "ability_focus_for";

    private AbilityFocus() {
    }

    public static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    public static NamespacedKey abilityKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, ABILITY_KEY);
    }

    /**
     * Builds the focus for an ability.
     *
     * @param material what it looks like; a stick for the Priest's staff
     */
    public static ItemStack create(JavaPlugin plugin, Messages messages,
                                   String abilityId, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(messages.get("ability." + abilityId + ".focus.name")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                messages.get("ability." + abilityId + ".focus.lore")
                        .decoration(TextDecoration.ITALIC, false),
                messages.get("ability.focus.hold-it")
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.setUnbreakable(true);

        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(
                abilityKey(plugin), PersistentDataType.STRING, abilityId);

        item.setItemMeta(meta);
        return item;
    }

    /** Whether this stack is a genuine focus for the given ability. */
    public static boolean isFocusFor(JavaPlugin plugin, ItemStack item, String abilityId) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        var data = item.getItemMeta().getPersistentDataContainer();
        return data.has(key(plugin), PersistentDataType.BYTE)
                && abilityId.equals(data.get(abilityKey(plugin), PersistentDataType.STRING));
    }

    /**
     * Whether the player is holding their focus in either hand.
     *
     * <p>Either hand rather than the main one specifically, so a priest can keep a shield or
     * a torch up and still be able to help somebody.
     */
    public static boolean held(JavaPlugin plugin, Player player, String abilityId) {
        return isFocusFor(plugin, player.getInventory().getItemInMainHand(), abilityId)
                || isFocusFor(plugin, player.getInventory().getItemInOffHand(), abilityId);
    }
}
