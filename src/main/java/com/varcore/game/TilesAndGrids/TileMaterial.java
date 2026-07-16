package com.varcore.game.TilesAndGrids;

import java.awt.image.BufferedImage;

/**
 * Separates what a material looks like from where it may expand.
 * Logical floor tile IDs map to materials via {@link TileMaterialRegistry}.
 *
 * <p>Expansion is decided only by:
 * <ul>
 *   <li>{@code canExpand} — may this material fringe onto neighbors?</li>
 *   <li>{@code acceptsExpansion} — may neighbors fringe onto this cell?</li>
 *   <li>{@code dominanceLayer} — higher layer wins over lower</li>
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

    public TileMaterial(
            int id,
            BufferedImage texture,
            int dominanceLayer,
            boolean canExpand,
            boolean acceptsExpansion,
            String expansionProfileId,
            MaterialFamily family)
    {
        this.id = id;
        this.texture = texture;
        this.dominanceLayer = dominanceLayer;
        this.canExpand = canExpand;
        this.acceptsExpansion = acceptsExpansion;
        this.expansionProfileId = expansionProfileId;
        this.family = family;
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

    /**
     * Whether {@code source} may fringe onto a cell of {@code target}.
     * No family exceptions — only expand flags and dominance layer.
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
        return source.dominanceLayer > target.dominanceLayer;
    }
}
