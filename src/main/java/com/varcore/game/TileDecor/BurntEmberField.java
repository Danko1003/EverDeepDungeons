package com.varcore.game.TileDecor;

import java.awt.Color;

import com.varcore.engine.render.Renderer;
import com.varcore.game.CamGame;
import com.varcore.game.TilesAndGrids.GridManager;

/** Sparse, slowly rising embers above Burnt Dirt tiles. */
public final class BurntEmberField
{
    private static final int BURNT_DIRT_TILE_ID = 15;
    private static final float MIN_ZOOM = 0.72f;

    private BurntEmberField() {}

    public static void draw(
            Renderer renderer, GridManager grid, CamGame cam,
            int minX, int minY, int maxX, int maxY, int layer, float animTime)
    {
        float zoom = cam.getZoom();
        if (zoom < MIN_ZOOM)
        {
            return;
        }

        int tileSize = grid.getTilesize();
        for (int gy = minY; gy <= maxY; gy++)
        {
            for (int gx = minX; gx <= maxX; gx++)
            {
                if (grid.getTileId(gx, gy) != BURNT_DIRT_TILE_ID || grid.hasOverlay(gx, gy))
                {
                    continue;
                }

                int cellSeed = hash(gx, gy, BURNT_DIRT_TILE_ID, 0xE8B3A);
                // Only some cells emit at all, keeping the field quiet and inexpensive.
                if ((cellSeed & 7) >= 3)
                {
                    continue;
                }

                int emberCount = (cellSeed & 31) == 0 ? 2 : 1;
                float cellWorldX = grid.getWorldX() + gx * tileSize;
                float cellWorldY = grid.getWorldY() + gy * tileSize;

                for (int i = 0; i < emberCount; i++)
                {
                    int seed = hash(gx, gy, i, 0xF1AE);
                    float lifetime = 4.8f + ((seed >>> 8) & 7) * 0.32f;
                    float phaseOffset = ((seed >>> 16) & 0xFF) / 255f * lifetime;
                    float age = positiveMod(animTime + phaseOffset, lifetime);
                    float activeTime = 1.8f + ((seed >>> 24) & 3) * 0.28f;
                    if (age > activeTime)
                    {
                        continue;
                    }

                    float t = age / activeTime;
                    float startX = 5f + ((seed & 0xFF) / 255f) * (tileSize - 10f);
                    float startY = tileSize * (0.58f + ((seed >>> 5) & 7) * 0.035f);
                    float rise = (5.5f + ((seed >>> 12) & 7) * 0.7f) * t;
                    float wobble = (float) Math.sin(age * 2.1f + seed * 0.0001f)
                            * (0.7f + t * 0.8f);

                    float worldX = cellWorldX + startX + wobble;
                    float worldY = cellWorldY + startY - rise;
                    float sx = (worldX - cam.getCamX()) * zoom;
                    float sy = (worldY - cam.getCamY()) * zoom;
                    float size = Math.max(1f, zoom * (t < 0.35f ? 1.45f : 1.05f));

                    int alpha = clamp(Math.round(210f * fade(t)));
                    int heat = clamp(Math.round((1f - t) * 70f));
                    Color ember = new Color(255, 105 + heat, 28, alpha);
                    renderer.drawRect(sx, sy, size, size, ember, true, layer);

                    // A tiny glow is reserved for close zoom and the hottest part.
                    if (zoom >= 1.05f && t < 0.38f)
                    {
                        Color glow = new Color(255, 194, 80, Math.max(0, alpha / 3));
                        renderer.drawRect(sx - zoom, sy - zoom,
                                size + zoom * 2f, size + zoom * 2f, glow, false, layer - 1);
                    }
                }
            }
        }
    }

    private static float fade(float t)
    {
        float fadeIn = Math.min(1f, t / 0.12f);
        float fadeOut = Math.min(1f, (1f - t) / 0.42f);
        return fadeIn * fadeOut;
    }

    private static float positiveMod(float value, float modulus)
    {
        float result = value % modulus;
        return result < 0f ? result + modulus : result;
    }

    private static int clamp(int value)
    {
        return Math.max(0, Math.min(255, value));
    }

    private static int hash(int a, int b, int c, int d)
    {
        int h = a * 374761393 + b * 668265263 + c * 1274126177 + d * 0x85ebca6b;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }
}
