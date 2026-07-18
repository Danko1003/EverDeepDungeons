package com.varcore.game.TilesAndGrids;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.varcore.engine.render.RenderTransform;
import com.varcore.engine.render.Renderer;
import com.varcore.game.CamGame;
import com.varcore.game.TileDecor.BurntEmberField;
import com.varcore.game.TileDecor.BurnTransitionParticleField;
import com.varcore.game.TileDecor.GrassBladeField;
import com.varcore.game.TileDecor.RockPebbleField;

public class GridRenderer
{
    /** Below this zoom, draw tile average colors as rectangles instead of sprites. */
    private static final float AVERAGE_COLOR_ZOOM = 0.60f;
    /** Below this zoom, draw plain tiles instead of cached projection images. */
    private static final float PROJECTION_MIN_ZOOM = AVERAGE_COLOR_ZOOM;

    private final TileRegistry tileRegistry;
    private final TileTextureRegistry tileTextureRegistry;
    private final StructureRegistry structureRegistry;
    private final TileMaterialRegistry materialRegistry;
    private final CellVisualCache visualCache;
    private final Map<Integer, Color> averageColors = new HashMap<>();
    private float animTime;

    public GridRenderer(TileRegistry tileRegistry, TileTextureRegistry tileTextureRegistry)
    {
        this(tileRegistry, tileTextureRegistry, new StructureRegistry(), null, null);
    }

    public GridRenderer(
            TileRegistry tileRegistry,
            TileTextureRegistry tileTextureRegistry,
            StructureRegistry structureRegistry)
    {
        this(tileRegistry, tileTextureRegistry, structureRegistry, null, null);
    }

    public GridRenderer(
            TileRegistry tileRegistry,
            TileTextureRegistry tileTextureRegistry,
            StructureRegistry structureRegistry,
            TileMaterialRegistry materialRegistry,
            CellVisualCache visualCache)
    {
        this.tileRegistry = tileRegistry;
        this.tileTextureRegistry = tileTextureRegistry;
        this.structureRegistry = structureRegistry;
        this.materialRegistry = materialRegistry;
        this.visualCache = visualCache;
    }

    public void setAnimTime(float animTime)
    {
        this.animTime = animTime;
    }

    public void render(Renderer renderer, GridManager grid, CamGame cam, int layer)
    {
        VisibleBounds visible = getVisibleBounds(grid, cam);
        float zoom = cam.getZoom();
        // Far zoom skips drawing projections but keeps the cache so zooming back in is instant.
        boolean useProjection = materialRegistry != null && visualCache != null && zoom >= PROJECTION_MIN_ZOOM;
        int rebuildBudget = !useProjection ? 0 : (zoom < 0.75f ? 16 : 24);

        // 1) Floor / ground (projected materials use cached visuals)
        for (int y = visible.minY; y <= visible.maxY; y++)
        {
            for (int x = visible.minX; x <= visible.maxX; x++)
            {
                int tileId = grid.getTileId(x, y);
                if (useProjection && materialRegistry.hasMaterial(tileId))
                {
                    BufferedImage projected = visualCache.getCached(grid, x, y);
                    if (visualCache.needsBuild(grid, x, y) && rebuildBudget > 0)
                    {
                        projected = visualCache.getOrBuild(grid, x, y);
                        rebuildBudget--;
                    }
                    if (projected != null)
                    {
                        drawImageAtCell(renderer, grid, cam, x, y, 0, projected, layer);
                        continue;
                    }
                }
                drawAtCell(renderer, grid, cam, x, y, 0, tileId, layer);
            }
        }

        // 2) Floor decor (after floor, under walls)
        if (materialRegistry != null && zoom >= PROJECTION_MIN_ZOOM)
        {
            RockPebbleField.draw(
                    renderer,
                    grid,
                    cam,
                    materialRegistry,
                    this::averageColor,
                    tileId -> tileRegistry.getTile(tileId).getTextureId(),
                    visible.minX,
                    visible.minY,
                    visible.maxX,
                    visible.maxY,
                    layer + 1);
            GrassBladeField.draw(
                    renderer,
                    grid,
                    cam,
                    materialRegistry,
                    this::averageColor,
                    tileId -> tileRegistry.getTile(tileId).getTextureId(),
                    visible.minX,
                    visible.minY,
                    visible.maxX,
                    visible.maxY,
                    layer + 1,
                    animTime);
            BurntEmberField.draw(
                    renderer,
                    grid,
                    cam,
                    visible.minX,
                    visible.minY,
                    visible.maxX,
                    visible.maxY,
                    layer + 2,
                    animTime);
            BurnTransitionParticleField.draw(
                    renderer,
                    grid,
                    cam,
                    materialRegistry,
                    visible.minX,
                    visible.minY,
                    visible.maxX,
                    visible.maxY,
                    layer + 3,
                    animTime);
        }

        // 3) Wall / structure overlays (front walls draw later via higher Y)
        // Include feet just below the viewport because their stacks extend upward.
        int overlayMaxY = Math.min(
                grid.getHeight() - 1,
                visible.maxY + TileRegistry.WALL_STACK_HEIGHT);
        for (int y = visible.minY; y <= overlayMaxY; y++)
        {
            for (int x = visible.minX; x <= visible.maxX; x++)
            {
                int overlayId = grid.getOverlayTileId(x, y);
                if (overlayId == 0)
                {
                    continue;
                }

                if (structureRegistry.isColumnWallVisualOnlyPiece(overlayId))
                {
                    continue;
                }

                List<StructureRegistry.VisualPart> parts = structureRegistry.getVisualParts(overlayId);
                for (StructureRegistry.VisualPart part : parts)
                {
                    drawAtCell(
                            renderer, grid, cam,
                            x, y + part.dy, part.pixelOffsetY, part.tileId, layer + 2);
                }
            }
        }
    }

    private VisibleBounds getVisibleBounds(GridManager grid, CamGame cam)
    {
        float zoom = Math.max(0.01f, cam.getZoom());
        float viewLeft = cam.getCamX();
        float viewTop = cam.getCamY();
        float viewRight = viewLeft + cam.getViewportWidth() / zoom;
        float viewBottom = viewTop + cam.getViewportHeight() / zoom;
        int tileSize = grid.getTilesize();

        // One-cell padding prevents pop-in at rounded sprite edges and shadows.
        int minX = (int) Math.floor((viewLeft - grid.getWorldX()) / tileSize) - 1;
        int minY = (int) Math.floor((viewTop - grid.getWorldY()) / tileSize) - 1;
        int maxX = (int) Math.floor((viewRight - grid.getWorldX()) / tileSize) + 1;
        int maxY = (int) Math.floor((viewBottom - grid.getWorldY()) / tileSize) + 1;

        return new VisibleBounds(
                Math.max(0, minX),
                Math.max(0, minY),
                Math.min(grid.getWidth() - 1, maxX),
                Math.min(grid.getHeight() - 1, maxY));
    }

    private static final class VisibleBounds
    {
        final int minX;
        final int minY;
        final int maxX;
        final int maxY;

        VisibleBounds(int minX, int minY, int maxX, int maxY)
        {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    private void drawAtCell(Renderer renderer, GridManager grid, CamGame cam,
                            int cellX, int cellY, int pixelOffsetY, int tileId, int layer)
    {
        Tile tile = tileRegistry.getTile(tileId);
        if (cam.getZoom() < AVERAGE_COLOR_ZOOM)
        {
            drawAverageColorAtCell(
                    renderer, grid, cam, cellX, cellY, pixelOffsetY, tile.getTextureId(), layer);
            return;
        }
        BufferedImage img = tileTextureRegistry.getTexture(tile.getTextureId());
        drawImageAtCell(renderer, grid, cam, cellX, cellY, pixelOffsetY, img, layer);
    }

    private void drawAverageColorAtCell(
            Renderer renderer,
            GridManager grid,
            CamGame cam,
            int cellX,
            int cellY,
            int pixelOffsetY,
            int textureId,
            int layer)
    {
        int tileSize = grid.getTilesize();
        float worldLeft = grid.getWorldX() + cellX * tileSize;
        float worldTop = grid.getWorldY() + cellY * tileSize + pixelOffsetY;
        int[] rect = projectCellRect(worldLeft, worldTop, tileSize, cam);
        renderer.drawRect(rect[0], rect[1], rect[2], rect[3],
                averageColor(textureId), true, layer);
    }

    private Color averageColor(int textureId)
    {
        Color cached = averageColors.get(textureId);
        if (cached != null)
        {
            return cached;
        }

        BufferedImage img = tileTextureRegistry.getTexture(textureId);
        long r = 0;
        long g = 0;
        long b = 0;
        long a = 0;
        long count = 0;
        for (int y = 0; y < img.getHeight(); y++)
        {
            for (int x = 0; x < img.getWidth(); x++)
            {
                int argb = img.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0)
                {
                    continue;
                }
                a += alpha;
                r += (argb >>> 16) & 0xFF;
                g += (argb >>> 8) & 0xFF;
                b += argb & 0xFF;
                count++;
            }
        }

        Color avg;
        if (count == 0)
        {
            avg = new Color(0, 0, 0, 0);
        }
        else
        {
            avg = new Color(
                    (int) (r / count),
                    (int) (g / count),
                    (int) (b / count),
                    (int) (a / count));
        }
        averageColors.put(textureId, avg);
        return avg;
    }

    private void drawImageAtCell(
            Renderer renderer,
            GridManager grid,
            CamGame cam,
            int cellX,
            int cellY,
            int pixelOffsetY,
            BufferedImage img,
            int layer)
    {
        int tileSize = grid.getTilesize();
        float worldLeft = grid.getWorldX() + cellX * tileSize;
        float worldTop = grid.getWorldY() + cellY * tileSize + pixelOffsetY;

        int[] rect = projectCellRect(worldLeft, worldTop, tileSize, cam);
        renderer.drawSprite(img, rect[0], rect[1], rect[2], rect[3], layer,
                RenderTransform.rotation(0f));
    }

    /**
     * Maps a world cell to screen pixels without black seams.
     * Floor/ceil + 1px overlap when zoomed out so adjacent tiles always cover the gap
     * that Math.round alone leaves at low zoom / fractional camera positions.
     *
     * @return int[]{drawX, drawY, drawW, drawH}
     */
    private static int[] projectCellRect(float worldLeft, float worldTop, int tileSize, CamGame cam)
    {
        float zoom = cam.getZoom();
        float camX = cam.getCamX();
        float camY = cam.getCamY();

        int drawX = (int) Math.floor((worldLeft - camX) * zoom);
        int drawY = (int) Math.floor((worldTop - camY) * zoom);
        int drawRight = (int) Math.ceil((worldLeft + tileSize - camX) * zoom);
        int drawBottom = (int) Math.ceil((worldTop + tileSize - camY) * zoom);

        int drawW = Math.max(1, drawRight - drawX);
        int drawH = Math.max(1, drawBottom - drawY);

        // Extra overlap when tiles are small on screen — kills residual 1px seams.
        if (zoom < 1f)
        {
            drawW += 1;
            drawH += 1;
        }

        return new int[] { drawX, drawY, drawW, drawH };
    }
}
