package com.varcore.game.TilesAndGrids;

public final class MaterialRegion
{
    private final TileMaterial material;
    private final PixelMask mask;

    public MaterialRegion(TileMaterial material, PixelMask mask)
    {
        this.material = material;
        this.mask = mask;
    }

    public TileMaterial getMaterial()
    {
        return material;
    }

    public PixelMask getMask()
    {
        return mask;
    }
}
