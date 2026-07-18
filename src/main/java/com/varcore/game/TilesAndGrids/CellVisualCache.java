package com.varcore.game.TilesAndGrids;

import java.awt.image.BufferedImage;

/**
 * Caches composed projected floor visuals per cell. Rebuilds on dirty flags.
 */
public final class CellVisualCache
{
    private final TransitionResolver resolver;
    private BufferedImage[][] images;
    private boolean[][] dirty;
    private int width;
    private int height;

    public CellVisualCache(TransitionResolver resolver, int width, int height)
    {
        this.resolver = resolver;
        resize(width, height);
    }

    public void resize(int width, int height)
    {
        resize(width, height, false);
    }

    /**
     * @param preserve when true, copies overlapping cached images into the new
     *                 arrays so zoom/screen resizes don't wipe finished projections
     */
    public void resize(int width, int height, boolean preserve)
    {
        int newW = Math.max(1, width);
        int newH = Math.max(1, height);
        if (newW == this.width && newH == this.height && images != null)
        {
            return;
        }

        BufferedImage[][] oldImages = images;
        boolean[][] oldDirty = dirty;
        int oldW = this.width;
        int oldH = this.height;

        this.width = newW;
        this.height = newH;
        images = new BufferedImage[this.height][this.width];
        dirty = new boolean[this.height][this.width];

        if (!preserve || oldImages == null)
        {
            markAllDirty();
            return;
        }

        int copyW = Math.min(oldW, this.width);
        int copyH = Math.min(oldH, this.height);
        for (int y = 0; y < this.height; y++)
        {
            for (int x = 0; x < this.width; x++)
            {
                if (x < copyW && y < copyH)
                {
                    images[y][x] = oldImages[y][x];
                    dirty[y][x] = oldDirty[y][x];
                }
                else
                {
                    dirty[y][x] = true;
                }
            }
        }
    }

    public void markAllDirty()
    {
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                dirty[y][x] = true;
                images[y][x] = null;
            }
        }
    }

    public void markDirty(int x, int y)
    {
        if (x < 0 || y < 0 || x >= width || y >= height)
        {
            return;
        }
        dirty[y][x] = true;
    }

    public void markDirty3x3(int x, int y)
    {
        for (int oy = -1; oy <= 1; oy++)
        {
            for (int ox = -1; ox <= 1; ox++)
            {
                markDirty(x + ox, y + oy);
            }
        }
    }

    /**
     * Moves cached images with a sliding grid window. Cells shifted outside the
     * window are discarded; newly exposed cells remain empty and dirty.
     */
    public void shiftBy(int shiftX, int shiftY)
    {
        if (shiftX == 0 && shiftY == 0)
        {
            return;
        }
        if (Math.abs(shiftX) >= width || Math.abs(shiftY) >= height)
        {
            markAllDirty();
            return;
        }

        int yStart = shiftY > 0 ? height - 1 : 0;
        int yEnd = shiftY > 0 ? -1 : height;
        int yStep = shiftY > 0 ? -1 : 1;
        int xStart = shiftX > 0 ? width - 1 : 0;
        int xEnd = shiftX > 0 ? -1 : width;
        int xStep = shiftX > 0 ? -1 : 1;
        for (int y = yStart; y != yEnd; y += yStep)
        {
            for (int x = xStart; x != xEnd; x += xStep)
            {
                int sourceX = x - shiftX;
                int sourceY = y - shiftY;
                if (sourceX >= 0 && sourceY >= 0 && sourceX < width && sourceY < height)
                {
                    images[y][x] = images[sourceY][sourceX];
                    dirty[y][x] = dirty[sourceY][sourceX];
                }
                else
                {
                    images[y][x] = null;
                    dirty[y][x] = true;
                }
            }
        }
    }

    /**
     * Returns the composed visual for a projected material cell, or null if
     * the cell has no material (caller should fall back to sprite draw).
     */
    public BufferedImage getOrBuild(GridManager grid, int x, int y)
    {
        if (x < 0 || y < 0 || x >= width || y >= height)
        {
            return null;
        }

        ensureSize(grid);

        if (!dirty[y][x] && images[y][x] != null)
        {
            return images[y][x];
        }

        BufferedImage built = resolver.buildImage(grid, x, y);
        images[y][x] = built;
        dirty[y][x] = false;
        return built;
    }

    public BufferedImage getCached(GridManager grid, int x, int y)
    {
        if (x < 0 || y < 0)
        {
            return null;
        }
        ensureSize(grid);
        if (x >= width || y >= height)
        {
            return null;
        }
        return images[y][x];
    }

    public boolean needsBuild(GridManager grid, int x, int y)
    {
        if (x < 0 || y < 0)
        {
            return false;
        }
        ensureSize(grid);
        if (x >= width || y >= height)
        {
            return false;
        }
        return dirty[y][x] || images[y][x] == null;
    }

    /**
     * Builds up to {@code maxBuilds} dirty cells inside the given bounds.
     * Returns how many cells were built this call.
     */
    public int buildDirtyInBounds(
            GridManager grid,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int maxBuilds)
    {
        if (maxBuilds <= 0)
        {
            return 0;
        }
        ensureSize(grid);
        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        maxX = Math.min(width - 1, maxX);
        maxY = Math.min(height - 1, maxY);

        int built = 0;
        for (int y = minY; y <= maxY && built < maxBuilds; y++)
        {
            for (int x = minX; x <= maxX && built < maxBuilds; x++)
            {
                if (!dirty[y][x] && images[y][x] != null)
                {
                    continue;
                }
                BufferedImage img = resolver.buildImage(grid, x, y);
                images[y][x] = img;
                dirty[y][x] = false;
                built++; // count attempts so empty cells can't burn the whole frame
            }
        }
        return built;
    }

    private void ensureSize(GridManager grid)
    {
        if (width != grid.getWidth() || height != grid.getHeight())
        {
            resize(grid.getWidth(), grid.getHeight());
        }
    }
}
