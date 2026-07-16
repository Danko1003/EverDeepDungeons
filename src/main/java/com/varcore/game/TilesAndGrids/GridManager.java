package com.varcore.game.TilesAndGrids;

public class GridManager
{
    private int width;
    private int height;
    private final int tilesize;
    private CellData[][] grid;
    private int worldX;
    private int worldY;
    private final int defaultTileId;
    private CellVisualCache visualCache;

    public GridManager(int width, int height, int tilesize, int worldX, int worldY)
    {
        this(width, height, tilesize, worldX, worldY, TileRegistry.EMPTY_TILE_ID);
    }

    public GridManager(int width, int height, int tilesize, int worldX, int worldY, int defaultTileId)
    {
        this.tilesize = tilesize;
        this.worldX = worldX;
        this.worldY = worldY;
        this.defaultTileId = defaultTileId;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.grid = createCells(this.width, this.height, defaultTileId);
    }

    public void setVisualCache(CellVisualCache visualCache)
    {
        this.visualCache = visualCache;
        if (visualCache != null)
        {
            visualCache.resize(width, height);
        }
    }

    public CellVisualCache getVisualCache()
    {
        return visualCache;
    }

    public void resize(int newWidth, int newHeight)
    {
        newWidth = Math.max(1, newWidth);
        newHeight = Math.max(1, newHeight);
        if (newWidth == width && newHeight == height)
        {
            return;
        }

        CellData[][] next = createCells(newWidth, newHeight, defaultTileId);
        int copyW = Math.min(width, newWidth);
        int copyH = Math.min(height, newHeight);
        for (int y = 0; y < copyH; y++)
        {
            for (int x = 0; x < copyW; x++)
            {
                next[y][x] = grid[y][x];
            }
        }

        grid = next;
        width = newWidth;
        height = newHeight;
        if (visualCache != null)
        {
            // Keep already-built projections for the overlapping region.
            visualCache.resize(width, height, true);
        }
    }

    /**
     * Slides this fixed-size grid while preserving cells that still overlap the
     * new world window. Positive shifts move existing cells right/down.
     */
    public void shiftWindow(int shiftX, int shiftY, int newWorldX, int newWorldY)
    {
        if (shiftX == 0 && shiftY == 0)
        {
            worldX = newWorldX;
            worldY = newWorldY;
            return;
        }

        CellData[][] next = createCells(width, height, defaultTileId);
        if (Math.abs(shiftX) < width && Math.abs(shiftY) < height)
        {
            for (int y = 0; y < height; y++)
            {
                int ny = y + shiftY;
                if (ny < 0 || ny >= height)
                {
                    continue;
                }
                for (int x = 0; x < width; x++)
                {
                    int nx = x + shiftX;
                    if (nx >= 0 && nx < width)
                    {
                        next[ny][nx] = grid[y][x];
                    }
                }
            }
        }

        grid = next;
        worldX = newWorldX;
        worldY = newWorldY;
        if (visualCache != null)
        {
            visualCache.shiftBy(shiftX, shiftY);
        }
    }

    /**
     * Inserts empty rows at the top of the grid and shifts existing content down.
     * Adjusts {@code worldY} so previously placed tiles keep their world position.
     *
     * @return how many rows were inserted (0 if none)
     */
    public int growUpward(int rows)
    {
        if (rows <= 0)
        {
            return 0;
        }

        int newHeight = height + rows;
        CellData[][] next = createCells(width, newHeight, defaultTileId);
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                next[y + rows][x] = grid[y][x];
            }
        }

        grid = next;
        height = newHeight;
        worldY -= rows * tilesize;
        if (visualCache != null)
        {
            visualCache.resize(width, height, true);
        }
        return rows;
    }

    public void fill(int tileId)
    {
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                grid[y][x].setTileID(tileId);
            }
        }
        if (visualCache != null)
        {
            visualCache.markAllDirty();
        }
    }

    public void clearOverlays()
    {
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                grid[y][x].setOverlayTileID(0);
            }
        }
    }

    public void clearEntrances()
    {
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                grid[y][x].setEntrance(false);
            }
        }
    }

    public int getTileId(int gridX, int gridY)
    {
        return grid[gridY][gridX].getTileID();
    }

    public int getOverlayTileId(int gridX, int gridY)
    {
        return grid[gridY][gridX].getOverlayTileID();
    }

    public boolean hasOverlay(int gridX, int gridY)
    {
        return grid[gridY][gridX].hasOverlay();
    }

    public float getRotation(int gridX, int gridY)
    {
        return grid[gridY][gridX].getRotation();
    }

    public void setTileId(int gridX, int gridY, int newID)
    {
        grid[gridY][gridX].setTileID(newID);
        if (visualCache != null)
        {
            visualCache.markDirty3x3(gridX, gridY);
        }
    }

    /** Sets floor tile without marking the visual cache dirty (bulk world stamps). */
    public void setTileIdSilent(int gridX, int gridY, int newID)
    {
        grid[gridY][gridX].setTileID(newID);
    }

    public void setOverlayTileId(int gridX, int gridY, int overlayId)
    {
        grid[gridY][gridX].setOverlayTileID(overlayId);
    }

    /**
     * Clears base tile to empty. Structure overlays are handled by {@link StructureRegistry}.
     */
    public void clearBaseTile(int gridX, int gridY)
    {
        if (inBounds(gridX, gridY))
        {
            setTileId(gridX, gridY, TileRegistry.EMPTY_TILE_ID);
        }
    }

    public boolean inBounds(int gridX, int gridY)
    {
        return gridX >= 0 && gridY >= 0 && gridX < width && gridY < height;
    }

    public boolean isEntrance(int gridX, int gridY)
    {
        return grid[gridY][gridX].isEntrance();
    }

    public void setEntrance(int gridX, int gridY, boolean entrance)
    {
        grid[gridY][gridX].setEntrance(entrance);
    }

    public int getTilesize() {
        return tilesize;
    }

    public int getWorldX() {
        return worldX;
    }

    public void setWorldX(int worldX) {
        this.worldX = worldX;
    }

    public int getWorldY() {
        return worldY;
    }

    public void setWorldY(int worldY) {
        this.worldY = worldY;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public CellData[][] getGridCopy()
    {
        CellData[][] copy = new CellData[height][width];

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                copy[y][x] = grid[y][x];
            }
        }

        return copy;
    }

    private static CellData[][] createCells(int width, int height, int defaultTileId)
    {
        CellData[][] cells = new CellData[height][width];
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                cells[y][x] = new CellData(defaultTileId, 0);
            }
        }
        return cells;
    }
}
