package com.varcore.game.TilesAndGrids;

import java.awt.image.BufferedImage;
import java.util.EnumSet;

/**
 * Separates what a material looks like from where it may expand.
 * Logical floor tile IDs map to materials via {@link TileMaterialRegistry}.
 *
 * <p>Expansion is decided by:
 * <ul>
 *   <li>{@code canExpand} — may this material fringe onto neighbors?</li>
 *   <li>{@code acceptsExpansion} — may neighbors fringe onto this cell?</li>
 *   <li>{@code dominanceLayer} — higher layer wins over lower; equal layers
 *       peer-blend one-sided (lower id onto higher) so seams aren't double-fringed</li>
 * </ul>
 */
public final class TileMaterial
{
    private final int id;
    private final BufferedImage texture;
    private final int dominanceLayer;
    private final boolean canExpand;
    private final boolean acceptsExpansion;
    private final String expansionProfileId;
    private final MaterialFamily family;
    private final EnumSet<MaterialTrait> traits;

    public TileMaterial(
            int id,
            BufferedImage texture,
            int dominanceLayer,
            boolean canExpand,
            boolean acceptsExpansion,
            String expansionProfileId,
            MaterialFamily family,
            MaterialTrait... traits)
    {
        this.id = id;
        this.texture = texture;
        this.dominanceLayer = dominanceLayer;
        this.canExpand = canExpand;
        this.acceptsExpansion = acceptsExpansion;
        this.expansionProfileId = expansionProfileId;
        this.family = family;
        this.traits = traits == null || traits.length == 0
                ? EnumSet.noneOf(MaterialTrait.class)
                : EnumSet.of(traits[0], traits);
    }

    public int getId()
    {
        return id;
    }

    public BufferedImage getTexture()
    {
        return texture;
    }

    public int getDominanceLayer()
    {
        return dominanceLayer;
    }

    public boolean canExpand()
    {
        return canExpand;
    }

    public boolean acceptsExpansion()
    {
        return acceptsExpansion;
    }

    public String getExpansionProfileId()
    {
        return expansionProfileId;
    }

    public MaterialFamily getFamily()
    {
        return family;
    }

    public boolean hasTrait(MaterialTrait trait)
    {
        return traits.contains(trait);
    }

    /**
     * Whether {@code source} may fringe onto a cell of {@code target}.
     * Higher layer expands onto lower. Same-layer peers blend on one side of the
     * seam only (lower id → higher id) so the edge isn't double-fringed.
     */
    public static boolean canExpandOnto(TileMaterial source, TileMaterial target)
    {
        if (source == null || target == null)
        {
            return false;
        }
        if (!source.canExpand || !target.acceptsExpansion)
        {
            return false;
        }
        if (source.dominanceLayer > target.dominanceLayer)
        {
            return true;
        }
        // Peer blend: same layer, both expanders, different materials.
        // One-sided (source.id < target.id) so only one cell of the pair
        // receives the fringe — at the collision, not into both interiors.
        return source.dominanceLayer == target.dominanceLayer
                && target.canExpand
                && source.id < target.id;
    }
}
