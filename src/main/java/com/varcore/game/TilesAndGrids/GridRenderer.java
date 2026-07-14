package com.varcore.game.TilesAndGrids;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import com.varcore.engine.render.RenderTransform;
import com.varcore.engine.render.Renderer;
import com.varcore.game.CamGame;

public class GridRenderer
{
    /**
     * How many tile-art pixels the contact shadow spans (chunky, not screen px).
     * Each step is drawn as a solid zoom-scaled band so it matches pixel art.
     */
    private static final int CONTACT_SHADOW_ROWS = 4;
    /** Stepped alphas (dark → light). Length must match CONTACT_SHADOW_ROWS. */
    private static final int[] CONTACT_SHADOW_ALPHAS = {200, 130, 70, 35};

    private final TileRegistry tileRegistry;
    private final TileTextureRegistry tileTextureRegistry;
    private final StructureRegistry structureRegistry;

    public GridRenderer(TileRegistry tileRegistry, TileTextureRegistry tileTextureRegistry)
    {
        this(tileRegistry, tileTextureRegistry, new StructureRegistry());
    }

    public GridRenderer(
            TileRegistry tileRegistry,
            TileTextureRegistry tileTextureRegistry,
            StructureRegistry structureRegistry)
    {
        this.tileRegistry = tileRegistry;
        this.tileTextureRegistry = tileTextureRegistry;
        this.structureRegistry = structureRegistry;
    }

    public void render(Renderer renderer, GridManager grid, CamGame cam, int layer)
    {
        // 1) Floor / ground
        for (int y = 0; y < grid.getHeight(); y++)
        {
            for (int x = 0; x < grid.getWidth(); x++)
            {
                drawAtCell(renderer, grid, cam, x, y, grid.getTileId(x, y), layer);
            }
        }

        // 2) Contact shadows on the grass where wall feet sit / meet the ground
        for (int y = 0; y < grid.getHeight(); y++)
        {
            for (int x = 0; x < grid.getWidth(); x++)
            {
                int overlayId = grid.getOverlayTileId(x, y);
                if (structureRegistry.isColumnWallFoot(overlayId))
                {
                    drawWallContactShadow(renderer, grid, cam, x, y, layer + 1);
                }
            }
        }

        // 3) Wall / structure overlays (front walls draw later via higher Y)
        for (int y = 0; y < grid.getHeight(); y++)
        {
            for (int x = 0; x < grid.getWidth(); x++)
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
                    drawAtCell(renderer, grid, cam, x, y + part.dy, part.tileId, layer + 2);
                }
            }
        }
    }

    /**
     * Pixel-art contact shadow: a few zoom-scaled bands (not a smooth screen fade),
     * with a dithered outer row so the edge stays rough.
     */
    private void drawWallContactShadow(
            Renderer renderer,
            GridManager grid,
            CamGame cam,
            int cellX,
            int cellY,
            int layer)
    {
        float zoom = cam.getZoom();
        int tileSize = grid.getTilesize();
        float worldLeft = grid.getWorldX() + cellX * tileSize;
        float worldBottom = grid.getWorldY() + (cellY + 1) * tileSize;

        int drawX = Math.round((worldLeft - cam.getCamX()) * zoom);
        int drawBottom = Math.round((worldBottom - cam.getCamY()) * zoom);
        int drawW = Math.round((worldLeft + tileSize - cam.getCamX()) * zoom) - drawX;

        // One art-pixel tall → fat rectangle matching tile scale
        float bandH = Math.max(1f, zoom);

        for (int row = 0; row < CONTACT_SHADOW_ROWS; row++)
        {
            int alpha = CONTACT_SHADOW_ALPHAS[row];
            float bandY = drawBottom + row * bandH;
            boolean dither = row >= CONTACT_SHADOW_ROWS - 2;

            if (!dither)
            {
                renderer.drawRect(
                        drawX, bandY, drawW, bandH,
                        new Color(0, 0, 0, alpha),
                        true,
                        layer);
                continue;
            }

            // Checker / offset dither so the soft edge looks chunky, not blurred
            int dots = Math.max(1, Math.round(drawW / bandH));
            for (int d = 0; d < dots; d++)
            {
                boolean on = ((d + row + cellX + cellY) & 1) == 0;
                if (!on)
                {
                    continue;
                }
                float px = drawX + d * bandH;
                float pw = Math.min(bandH, drawX + drawW - px);
                if (pw <= 0f)
                {
                    break;
                }
                renderer.drawRect(
                        px, bandY, pw, bandH,
                        new Color(0, 0, 0, alpha),
                        true,
                        layer);
            }
        }
    }

    private void drawAtCell(Renderer renderer, GridManager grid, CamGame cam,
                            int cellX, int cellY, int tileId, int layer)
    {
        Tile tile = tileRegistry.getTile(tileId);
        BufferedImage img = tileTextureRegistry.getTexture(tile.getTextureId());

        float zoom = cam.getZoom();
        int tileSize = grid.getTilesize();
        float worldLeft = grid.getWorldX() + cellX * tileSize;
        float worldTop = grid.getWorldY() + cellY * tileSize;

        int drawX = Math.round((worldLeft - cam.getCamX()) * zoom);
        int drawY = Math.round((worldTop - cam.getCamY()) * zoom);
        int drawW = Math.round((worldLeft + tileSize - cam.getCamX()) * zoom) - drawX;
        int drawH = Math.round((worldTop + tileSize - cam.getCamY()) * zoom) - drawY;

        renderer.drawSprite(img, drawX, drawY, drawW, drawH, layer,
                RenderTransform.rotation(0f));
    }
}
