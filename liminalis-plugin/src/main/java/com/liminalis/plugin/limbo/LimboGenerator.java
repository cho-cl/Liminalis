package com.liminalis.plugin.limbo;

import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Generates Limbo: an endless pale garden with nothing in it.
 *
 * <p>Rather than hand-building terrain, this leans on vanilla for the parts vanilla is good
 * at and switches off the parts that do not belong. Noise and surface generation stay on, so
 * the land has real shape; decorations stay on, so pale oak and pale moss actually grow. What
 * goes is everything that would make Limbo feel like somewhere you could live:
 *
 * <ul>
 *   <li><strong>Caves</strong> - Limbo is a surface you wander, not a place with anywhere to
 *       hide or anything to dig up.</li>
 *   <li><strong>Structures</strong> - there are no villages here and nothing to loot. Nobody
 *       built anything, because nobody has ever lived here.</li>
 *   <li><strong>Mobs</strong> - nothing is alive in Limbo except the people trapped in it.</li>
 * </ul>
 *
 * <p>Switching mob generation off here handles the initial spawn during worldgen. Ongoing
 * spawning is stopped separately by gamerule and by cancelling spawn events, because pale
 * gardens grow creaking hearts and those spawn a Creaking regardless of the usual rules.
 */
public final class LimboGenerator extends ChunkGenerator {

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

    /** Pale moss and the biome's own surface blocks. */
    @Override
    public boolean shouldGenerateSurface() {
        return true;
    }

    /** No caves: there is nowhere to go but across. */
    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    /** Pale oak trees and moss patches - the things that make it read as a pale garden. */
    @Override
    public boolean shouldGenerateDecorations() {
        return true;
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
