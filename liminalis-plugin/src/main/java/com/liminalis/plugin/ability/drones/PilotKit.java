package com.liminalis.plugin.ability.drones;

import com.liminalis.core.profile.PlayerProfile;
import com.liminalis.plugin.profile.ProfileManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Puts a pilot's own belongings aside, and gives them back.
 *
 * <p>Flying a drone means flying a drone - not flying a drone while wearing netherite and
 * holding a pickaxe. Emptying somebody's inventory is also the single most dangerous thing
 * this plugin does, so all of the care is here.
 *
 * <p><strong>Written to disk before anything is taken.</strong> The order is deliberate and
 * it is the whole safety argument: encode, save the profile, and only then clear the pack. A
 * crash at any point after that leaves the items sitting in a file, and
 * {@link #restoreIfInterrupted} hands them back at the next login. A crash before it leaves
 * the player holding everything they already had. There is no window in which the items exist
 * nowhere.
 *
 * <p>Base64 over Bukkit's own object stream rather than anything hand-rolled, because it
 * round-trips enchantments, custom names, persistent data and everything else a modded item
 * carries - and a restore that quietly dropped an item's enchantments would be its own
 * disaster.
 */
public final class PilotKit {

    private final ProfileManager profiles;
    private final Logger logger;

    public PilotKit(ProfileManager profiles, Logger logger) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Stores everything the player is carrying, then empties them out.
     *
     * @return false if it could not be stored, in which case nothing was taken
     */
    public boolean putAside(Player player) {
        PlayerProfile profile = profiles.of(player);
        if (profile.storedInventory() != null) {
            // Already holding something of theirs. Taking a second inventory would overwrite
            // the first and lose it, so refuse and let the restore path run instead.
            logger.warning("Refusing to take " + player.getName() + "'s inventory: something"
                    + " of theirs is already stored. Restoring that first.");
            giveBack(player);
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        String encoded = encode(inventory.getContents());
        if (encoded == null) {
            return false;
        }

        profile.setStoredInventory(encoded);
        // Written before the clear, never after. This line is the safety.
        profiles.saveNow(profile);

        inventory.clear();
        player.updateInventory();
        return true;
    }

    /** Gives it all back and forgets it. Safe to call on somebody who has nothing stored. */
    public void giveBack(Player player) {
        PlayerProfile profile = profiles.of(player);
        String encoded = profile.storedInventory();
        if (encoded == null) {
            return;
        }
        ItemStack[] contents = decode(encoded, player.getName());

        if (contents != null) {
            player.getInventory().setContents(contents);
            player.updateInventory();
        }
        // Cleared only once the items are back in hand. If decoding threw, the string stays
        // put so a later build or a hand repair can still recover it.
        if (contents != null) {
            profile.setStoredInventory(null);
            profiles.saveNow(profile);
        }
    }

    /**
     * Hands back anything a crash interrupted.
     *
     * <p>Called on join. A player whose server died mid-flight logs in with their own things,
     * one login later than they expected and none the poorer.
     */
    public void restoreIfInterrupted(Player player) {
        PlayerProfile profile = profiles.resident(player.getUniqueId()).orElse(null);
        if (profile == null || profile.storedInventory() == null) {
            return;
        }
        logger.info("Returning " + player.getName() + "'s inventory, put aside for a drone"
                + " flight that did not end tidily.");
        giveBack(player);
    }

    // -------------------------------------------------------------------------- coding

    private String encode(ItemStack[] contents) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
            out.flush();
            return Base64Coder.encodeLines(bytes.toByteArray());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not store an inventory; leaving it alone", e);
            return null;
        }
    }

    private ItemStack[] decode(String encoded, String who) {
        try (ByteArrayInputStream bytes =
                     new ByteArrayInputStream(Base64Coder.decodeLines(encoded));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
            ItemStack[] contents = new ItemStack[in.readInt()];
            for (int i = 0; i < contents.length; i++) {
                contents[i] = (ItemStack) in.readObject();
            }
            return contents;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not read back " + who + "'s stored inventory."
                    + " It is still in their profile under storedInventory and has not been"
                    + " thrown away.", e);
            return null;
        }
    }
}
