package com.liminalis.plugin.limbo;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

/**
 * Every block of Limbo, in every direction, forever, is pale garden.
 *
 * <p>The pale garden is the greyest thing vanilla has: pale oak, pale moss, colourless
 * leaves, and a dark desaturated fog that the client renders without any help from us. Using
 * the real biome rather than approximating it with grey blocks means the fog, the water
 * tint, the grass colour and the ambient sound all come for free and all agree with each
 * other.
 */
public final class PaleGardenBiomeProvider extends BiomeProvider {

    private static final List<Biome> ONLY_BIOME = List.of(Biome.PALE_GARDEN);

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return Biome.PALE_GARDEN;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return ONLY_BIOME;
    }
}
