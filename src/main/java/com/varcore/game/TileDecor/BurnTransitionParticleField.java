package com.varcore.game.TileDecor;

import java.awt.Color;

import com.varcore.engine.render.Renderer;
import com.varcore.game.CamGame;
import com.varcore.game.TilesAndGrids.GridManager;
import com.varcore.game.TilesAndGrids.MaterialTrait;
import com.varcore.game.TilesAndGrids.TileMaterial;
import com.varcore.game.TilesAndGrids.TileMaterialRegistry;

/** Animated sparks emitted specifically along burnt/burnable material borders. */
public final class BurnTransitionParticleField
{
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final Color[] HOT = {
            new Color(255, 205, 72, 235),
            new Color(255, 205, 72, 180),
            new Color(255, 205, 72, 120),
            new Color(255, 205, 72, 60),
    };
    private static final Color[] COOL = {
            new Color(246, 92, 25, 235),
            new Color(246, 92, 25, 180),
            new Color(246, 92, 25, 120),
            new Color(246, 92, 25, 60),
    };

    private BurnTransitionParticleField() {}

    public static void draw(
            Renderer renderer, GridManager grid, CamGame cam, TileMaterialRegistry materials,
            int minX, int minY, int maxX, int maxY, int layer, float animTime)
    {
        float zoom = cam.getZoom();
        if (zoom < 0.76f || materials == null)
        {
            return;
        }

        int tileSize = grid.getTilesize();
        for (int gy = minY; gy <= maxY; gy++)
        {
            for (int gx = minX; gx <= maxX; gx++)
            {
                TileMaterial burnt = materials.getMaterial(grid.getTileId(gx, gy));
                if (burnt == null || !burnt.hasTrait(MaterialTrait.BURNT) || grid.hasOverlay(gx, gy))
                {
                    continue;
                }

                for (int side = 0; side < SIDES.length; side++)
                {
                    int nx = gx + SIDES[side][0];
                    int ny = gy + SIDES[side][1];
                    if (!grid.inBounds(nx, ny)) continue;
                    TileMaterial neighbour = materials.getMaterial(grid.getTileId(nx, ny));
                    if (neighbour == null || !neighbour.hasTrait(MaterialTrait.BURNABLE)) continue;

                    int seed = hash(gx, gy, side, 0xB04D3);
                    float cycle = 2.7f + ((seed >>> 9) & 7) * 0.18f;
                    float age = positiveMod(animTime + ((seed >>> 17) & 0xFF) / 255f * cycle, cycle);
                    float active = 0.9f + ((seed >>> 25) & 3) * 0.15f;
                    if (age > active) continue;

                    float t = age / active;
                    float along = 4f + ((seed & 0xFF) / 255f) * (tileSize - 8f);
                    float lx;
                    float ly;
                    switch (side)
                    {
                        case 0 -> { lx = along; ly = 2f; }
                        case 1 -> { lx = tileSize - 2f; ly = along; }
                        case 2 -> { lx = along; ly = tileSize - 2f; }
                        default -> { lx = 2f; ly = along; }
                    }

                    float sway = (float) Math.sin(age * 5f + (seed & 31)) * (0.7f + t);
                    float rise = t * (7f + ((seed >>> 5) & 3));
                    float worldX = grid.getWorldX() + gx * tileSize + lx + sway;
                    float worldY = grid.getWorldY() + gy * tileSize + ly - rise;
                    float sx = (worldX - cam.getCamX()) * zoom;
                    float sy = (worldY - cam.getCamY()) * zoom;
                    int bucket = Math.min(3, Math.max(0, Math.round((1f - t) * 3f)));
                    Color color = t < 0.32f ? HOT[bucket] : COOL[bucket];
                    float size = Math.max(1f, zoom * (t < 0.25f ? 1.45f : 1f));
                    renderer.drawRect(sx, sy, size, size, color, true, layer);
                }
            }
        }
    }

    private static float positiveMod(float value, float modulus)
    {
        float result = value % modulus;
        return result < 0f ? result + modulus : result;
    }

    private static int hash(int a, int b, int c, int d)
    {
        int h = a * 374761393 + b * 668265263 + c * 1274126177 + d * 0x85ebca6b;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }
}
