package com.liminalis.plugin.rescue;

import com.liminalis.plugin.singularity.SingularityResidue;
import com.liminalis.plugin.text.Messages;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * The thing that opens a way into the grey.
 *
 * <p>Made from what the Singularity leaves behind, which is the join that makes the whole
 * loop close: the creatures drop residue and the books that explain what it is for, and the
 * residue is what buys somebody's way in to fetch a friend. Without that, the books would be
 * flavour and the residue would only ever be an ability accelerant.
 *
 * <p>Single use, consumed on crossing. A reusable one would make rescue free after the first,
 * and the cost is most of what gives it weight.
 */
public final class ThresholdStone {

    private static final String KEY = "threshold_stone";
    private static final Material MATERIAL = Material.HEART_OF_THE_SEA;

    private ThresholdStone() {
    }

    public static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }

    public static ItemStack create(JavaPlugin plugin, Messages messages) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(messages.get("rescue.stone.name")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                messages.get("rescue.stone.lore-1").decoration(TextDecoration.ITALIC, false),
                messages.get("rescue.stone.lore-2").decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /** Whether a stack is a genuine stone rather than something renamed to look like one. */
    public static boolean is(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(key(plugin), PersistentDataType.BYTE);
    }

    /**
     * Registers the recipe: eight residue around an ender pearl.
     *
     * <p>Uses {@link RecipeChoice.ExactChoice} for the residue rather than matching on
     * material, so ordinary echo shards will not do. The residue has to have come from
     * something that crossed over, which is the point of it.
     */
    public static void registerRecipe(JavaPlugin plugin, Messages messages) {
        NamespacedKey recipeKey = new NamespacedKey(plugin, "threshold_stone_recipe");
        // Remove first so a reload does not throw on an already-registered key.
        plugin.getServer().removeRecipe(recipeKey);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, create(plugin, messages));
        recipe.shape("RRR", "RPR", "RRR");
        recipe.setIngredient('R', new RecipeChoice.ExactChoice(
                SingularityResidue.create(plugin, messages, 1)));
        recipe.setIngredient('P', Material.ENDER_PEARL);

        plugin.getServer().addRecipe(recipe);
    }
}
