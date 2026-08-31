package com.liminalis.plugin.singularity;

import com.liminalis.plugin.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * What is left of a Singularity creature once it stops.
 *
 * <p>This is the universal accelerant for ability unlocks - the thing that lets someone
 * hurry a power along whose own conditions they cannot easily meet. That matters because
 * abilities unlock through conditions tailored to each one, and a player whose ability is
 * gated behind something they rarely do would otherwise simply be stuck.
 *
 * <p>Tagged in persistent data rather than identified by name, so a player who renames a
 * stack of it in an anvil does not accidentally destroy it, and a player who renames
 * something else to match cannot forge it.
 */
public final class SingularityResidue {

    private static final String KEY = "singularity_residue";

    private SingularityResidue() {
    }

    public static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    public static ItemStack create(JavaPlugin plugin, Messages messages, int amount) {
        ItemStack item = new ItemStack(Material.ECHO_SHARD, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        meta.displayName(messages.get("singularity.residue.name")
                .decoration(TextDecoration.ITALIC, false));
        // The second line is the whole reason anybody finds out this is spendable. Residue
        // used to say only what it was, and a currency nothing tells you how to spend is
        // indistinguishable from a trophy.
        meta.lore(List.of(
                messages.get("singularity.residue.lore")
                        .decoration(TextDecoration.ITALIC, false),
                messages.get("singularity.residue.use")
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /** Whether a stack is genuine residue rather than something renamed to look like it. */
    public static boolean is(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(key(plugin), PersistentDataType.BYTE);
    }

    /** Fallback name, used only if messages.yml has been emptied. */
    static Component fallbackName() {
        return Component.text("Residue", NamedTextColor.DARK_AQUA);
    }
}
