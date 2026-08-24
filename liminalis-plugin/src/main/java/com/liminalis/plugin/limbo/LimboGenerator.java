package com.liminalis.plugin.limbo;

import org.bukkit.Material;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Generates Limbo: an endless pale garden with nothing in it.
 *
 * <p>Leans on vanilla for the one part vanilla is genuinely good at - the shape of the land -
 * and takes over or switches off everything else. Noise generation stays on, so Limbo has
 * hills and hollows rather than being a flat plane. What goes is everything that would make
 * it feel like somewhere you could live:
 *
 * <ul>
 *   <li><strong>Caves</strong> - Limbo is a surface you wander, not a place with anywhere to
 *       hide or anything to dig up.</li>
 *   <li><strong>Structures</strong> - there are no villages here and nothing to loot. Nobody
 *       built anything, because nobody has ever lived here.</li>
 *   <li><strong>Mobs</strong> - nothing is alive in Limbo except the people trapped in it.</li>
 *   <li><strong>Decorations</strong> - nothing grows. This is what removes the pale oak.</li>
 * </ul>
 *
 * <p>Surface generation is taken over rather than switched off, because vanilla's pale garden
 * gets its grey from decoration features rather than from its surface rules - so removing the
 * trees would otherwise have removed the grey with them. See {@link #shouldGenerateSurface()}.
 *
 * <p>Switching mob generation off here handles the initial spawn during worldgen. Ongoing
 * spawning is stopped separately by gamerule, by Peaceful difficulty, and by cancelling spawn
 * events - pale gardens grow creaking hearts, and those spawn a Creaking regardless of the
 * usual rules.
 */
public final class LimboGenerator extends ChunkGenerator {

    /** How deep the grey goes, so digging in does not immediately expose ordinary stone. */
    private static final int SURFACE_DEPTH = 4;

    private final BiomeProvider biomeProvider = new PaleGardenBiomeProvider();

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return biomeProvider;
    }

    /** Real terrain shape, so Limbo has hills and hollows rather than being a flat plane. */
    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    /**
     * Surface is laid by hand in {@link #generateSurface}, not by vanilla.
     *
     * <p>This was not the original plan and is worth explaining. Vanilla's pale garden puts
     * plain grass and dirt on the ground and gets its grey from <em>decoration</em> features -
     * the pale moss carpets and the trees. Switching decorations off to remove the trees
     * therefore also removed the grey, leaving an ordinary green-tinted meadow. Measured on
     * a generated chunk: 80 grass blocks, 219 dirt, and not one block of pale moss.
     *
     * <p>Laying the surface here gets both things at once - no trees, and ground that is
     * actually grey.
     */
    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    /**
     * Caps the terrain with pale moss, a few blocks deep.
     *
     * <p>Runs after the noise pass, so the shape of the land already exists and this only
     * replaces what is on top of it. Water is stepped over rather than capped, so lakes keep
     * their surface and get a moss bed underneath instead of a lid.
     */
    @Override
    public void generateSurface(WorldInfo worldInfo, Random random,
                                int chunkX, int chunkZ, ChunkData chunk) {
        int min = chunk.getMinHeight();
        int max = chunk.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = max - 1; y >= min; y--) {
                    Material here = chunk.getType(x, y, z);
                    if (here.isAir() || here == Material.WATER) {
                        continue;
                    }
                    // The top block, plus a little depth so digging in does not immediately
                    // expose ordinary stone and break the illusion.
                    for (int depth = 0; depth < SURFACE_DEPTH && y - depth >= min; depth++) {
                        if (!chunk.getType(x, y - depth, z).isAir()) {
                            chunk.setBlock(x, y - depth, z, Material.PALE_MOSS_BLOCK);
                        }
                    }
                    break;
                }
            }
        }
    }

    /** No caves: there is nowhere to go but across. */
    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    /**
     * Nothing grows in Limbo.
     *
     * <p>This is what removes the pale oak. Trees would make it a forest, and a forest is a
     * place - somewhere with cover, landmarks, and wood to build with. Bare grey ground
     * rolling away in every direction with nothing on it is the point.
     *
     * <p>Note this only governs chunks at the moment they are generated. Turning it back on
     * would grow trees in new chunks while leaving old ones bare; making Limbo consistent
     * again means deleting its world folder and letting it regenerate.
     */
    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    /** Nothing is alive here. */
    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    /** Nobody ever built anything in Limbo. */
    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
