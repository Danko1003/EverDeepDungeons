package com.varcore.game.world;

import com.varcore.game.TilesAndGrids.TileRegistry;

/** One generated chunk of floor / overlay / entrance data. */
public final class WorldChunk
{
    private final ChunkCoord coord;
    private final int size;
    private final int[] floors;
    private final int[] overlays;
    private final boolean[] entrances;

    public WorldChunk(ChunkCoord coord, int size)
    {
        this.coord = coord;
        this.size = Math.max(1, size);
        int n = this.size * this.size;
        this.floors = new int[n];
        this.overlays = new int[n];
        this.entrances = new boolean[n];
        for (int i = 0; i < n; i++)
        {
            floors[i] = TileRegistry.EMPTY_TILE_ID;
        }
    }

    public ChunkCoord getCoord()
    {
        return coord;
    }

    public int getSize()
    {
        return size;
    }

    public int worldOriginX()
    {
        return coord.cx * size;
    }

    public int worldOriginY()
    {
        return coord.cy * size;
    }

    public int getFloor(int localX, int localY)
    {
        return floors[index(localX, localY)];
    }

    public void setFloor(int localX, int localY, int tileId)
    {
        floors[index(localX, localY)] = tileId;
    }

    public int getOverlay(int localX, int localY)
    {
        return overlays[index(localX, localY)];
    }

    public void setOverlay(int localX, int localY, int overlayId)
    {
        overlays[index(localX, localY)] = overlayId;
    }

    public boolean isEntrance(int localX, int localY)
    {
        return entrances[index(localX, localY)];
    }

    public void setEntrance(int localX, int localY, boolean entrance)
    {
        entrances[index(localX, localY)] = entrance;
    }

    private int index(int localX, int localY)
    {
        if (localX < 0 || localY < 0 || localX >= size || localY >= size)
        {
            throw new IndexOutOfBoundsException(localX + "," + localY);
        }
        return localY * size + localX;
    }
}
