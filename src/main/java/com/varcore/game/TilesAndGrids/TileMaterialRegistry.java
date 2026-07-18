package com.varcore.game.TilesAndGrids;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps logical floor tile IDs to expandable materials and expansion profiles.
 */
public final class TileMaterialRegistry
{
    public static final String PROFILE_GRASS_THICK = "grass_thick";
    public static final String PROFILE_DIRT_CRUMBLING = "dirt_crumbling";
    public static final String PROFILE_STONE_SPARSE = "stone_sparse";

    private final Map<Integer, TileMaterial> materials = new HashMap<>();
    private final Map<String, ExpansionProfile> profiles = new HashMap<>();

    public TileMaterialRegistry(TileTextureRegistry textures)
    {
        profiles.put(PROFILE_GRASS_THICK,
                ExpansionMaskGenerator.createProfile(PROFILE_GRASS_THICK, ExpansionMaskGenerator.Style.SOFT));
        profiles.put(PROFILE_DIRT_CRUMBLING,
                ExpansionMaskGenerator.createProfile(PROFILE_DIRT_CRUMBLING, ExpansionMaskGenerator.Style.SOFT));
        profiles.put(PROFILE_STONE_SPARSE,
                ExpansionMaskGenerator.createProfile(PROFILE_STONE_SPARSE, ExpansionMaskGenerator.Style.SPARSE));

        // layer, canExpand, acceptsExpansion
        // Higher layer fringes onto lower. Same-layer peers blend one-sided.
        // Empty: layer 0, never expands, accepts all fringes
        register(TileRegistry.EMPTY_TILE_ID, textures, 0, false, true,
                PROFILE_GRASS_THICK, MaterialFamily.EMPTY);

        // Dirt — shared peer layers so variants blend instead of hard-stopping
        register(15, textures, 4, true, true, PROFILE_DIRT_CRUMBLING,
                MaterialFamily.DIRT, MaterialTrait.BURNT); // Dirt Burnt
        register(11, textures, 5, true, true, PROFILE_DIRT_CRUMBLING, MaterialFamily.DIRT); // Dirt Path One
        register(13, textures, 5, true, true, PROFILE_DIRT_CRUMBLING, MaterialFamily.DIRT); // Dirt Path Three
        register(14, textures, 5, true, true, PROFILE_DIRT_CRUMBLING, MaterialFamily.DIRT); // Dirt Normal

        // Grass — shared peer layer so shades blend at edges
        register(4, textures, 8, true, true, PROFILE_GRASS_THICK,
                MaterialFamily.GRASS, MaterialTrait.BURNABLE);
        register(5, textures, 8, true, true, PROFILE_GRASS_THICK,
                MaterialFamily.GRASS, MaterialTrait.BURNABLE);
        register(6, textures, 8, true, true, PROFILE_GRASS_THICK,
                MaterialFamily.GRASS, MaterialTrait.BURNABLE);
        register(7, textures, 8, true, true, PROFILE_GRASS_THICK,
                MaterialFamily.GRASS, MaterialTrait.BURNABLE);

        // Stone — shared peer layer
        register(2, textures, 2, true, true, PROFILE_STONE_SPARSE, MaterialFamily.STONE);
        register(3, textures, 2, true, true, PROFILE_STONE_SPARSE, MaterialFamily.STONE);
    }

    private void register(
            int tileId,
            TileTextureRegistry textures,
            int dominanceLayer,
            boolean canExpand,
            boolean acceptsExpansion,
            String profileId,
            MaterialFamily family,
            MaterialTrait... traits)
    {
        int textureId = textureIdForTile(tileId);
        BufferedImage tex = textures.getTexture(textureId);
        materials.put(tileId, new TileMaterial(
                tileId,
                tex,
                dominanceLayer,
                canExpand,
                acceptsExpansion,
                profileId,
                family,
                traits));
    }

    private static int textureIdForTile(int tileId)
    {
        // Matches TileRegistry texture ids for projected floors
        return switch (tileId)
        {
            case TileRegistry.EMPTY_TILE_ID -> 1;
            case 11 -> 11;
            case 13 -> 12;
            case 14 -> 13;
            case 15 -> 14;
            default -> tileId; // grass 4–7, stone 2–3 use same texture id
        };
    }

    public boolean hasMaterial(int tileId)
    {
        return materials.containsKey(tileId);
    }

    public TileMaterial getMaterial(int tileId)
    {
        return materials.get(tileId);
    }

    public ExpansionProfile getProfile(String profileId)
    {
        return profiles.get(profileId);
    }

    public ExpansionProfile getProfileFor(TileMaterial material)
    {
        if (material == null)
        {
            return null;
        }
        return profiles.get(material.getExpansionProfileId());
    }
}
