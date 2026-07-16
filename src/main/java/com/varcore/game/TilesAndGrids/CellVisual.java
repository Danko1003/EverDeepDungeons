package com.varcore.game.TilesAndGrids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CellVisual
{
    private final MaterialRegion base;
    private final List<MaterialRegion> overlays;

    public CellVisual(MaterialRegion base, List<MaterialRegion> overlays)
    {
        this.base = base;
        this.overlays = overlays == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(overlays));
    }

    public MaterialRegion getBase()
    {
        return base;
    }

    public List<MaterialRegion> getOverlays()
    {
        return overlays;
    }
}
