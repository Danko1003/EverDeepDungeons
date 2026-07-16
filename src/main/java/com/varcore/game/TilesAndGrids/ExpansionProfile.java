package com.varcore.game.TilesAndGrids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ExpansionProfile
{
    private final String id;
    private final Map<Direction, List<PixelMask>> edgeMasks;
    private final Map<Corner, List<PixelMask>> cornerMasks;

    public ExpansionProfile(
            String id,
            Map<Direction, List<PixelMask>> edgeMasks,
            Map<Corner, List<PixelMask>> cornerMasks)
    {
        this.id = id;
        this.edgeMasks = copyEdges(edgeMasks);
        this.cornerMasks = copyCorners(cornerMasks);
    }

    public String getId()
    {
        return id;
    }

    public PixelMask getEdgeMask(Direction direction, int variant)
    {
        List<PixelMask> list = edgeMasks.get(direction);
        if (list == null || list.isEmpty())
        {
            return PixelMask.empty(32);
        }
        return list.get(Math.floorMod(variant, list.size()));
    }

    public PixelMask getCornerMask(Corner corner, int variant)
    {
        List<PixelMask> list = cornerMasks.get(corner);
        if (list == null || list.isEmpty())
        {
            return PixelMask.empty(32);
        }
        return list.get(Math.floorMod(variant, list.size()));
    }

    public int getEdgeVariantCount(Direction direction)
    {
        List<PixelMask> list = edgeMasks.get(direction);
        return list == null ? 0 : list.size();
    }

    public int getCornerVariantCount(Corner corner)
    {
        List<PixelMask> list = cornerMasks.get(corner);
        return list == null ? 0 : list.size();
    }

    private static Map<Direction, List<PixelMask>> copyEdges(Map<Direction, List<PixelMask>> src)
    {
        Map<Direction, List<PixelMask>> out = new EnumMap<>(Direction.class);
        if (src == null)
        {
            return out;
        }
        for (Map.Entry<Direction, List<PixelMask>> e : src.entrySet())
        {
            out.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return out;
    }

    private static Map<Corner, List<PixelMask>> copyCorners(Map<Corner, List<PixelMask>> src)
    {
        Map<Corner, List<PixelMask>> out = new EnumMap<>(Corner.class);
        if (src == null)
        {
            return out;
        }
        for (Map.Entry<Corner, List<PixelMask>> e : src.entrySet())
        {
            out.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return out;
    }
}
