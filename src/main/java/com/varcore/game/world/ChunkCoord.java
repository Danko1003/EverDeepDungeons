package com.varcore.game.world;

/** Integer chunk coordinate (chunk-space, not tile-space). */
public final class ChunkCoord
{
    public final int cx;
    public final int cy;

    public ChunkCoord(int cx, int cy)
    {
        this.cx = cx;
        this.cy = cy;
    }

    public static ChunkCoord fromWorldTile(int worldTileX, int worldTileY, int chunkSize)
    {
        int size = Math.max(1, chunkSize);
        int cx = worldTileX >= 0 ? worldTileX / size : (worldTileX - size + 1) / size;
        int cy = worldTileY >= 0 ? worldTileY / size : (worldTileY - size + 1) / size;
        return new ChunkCoord(cx, cy);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof ChunkCoord other))
        {
            return false;
        }
        return cx == other.cx && cy == other.cy;
    }

    @Override
    public int hashCode()
    {
        return cx * 73856093 ^ cy * 19349663;
    }

    @Override
    public String toString()
    {
        return "(" + cx + "," + cy + ")";
    }
}
