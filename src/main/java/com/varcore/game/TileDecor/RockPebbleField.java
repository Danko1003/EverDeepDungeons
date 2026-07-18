package com.varcore.game.TileDecor;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;

import com.varcore.engine.render.Renderer;
import com.varcore.game.CamGame;
import com.varcore.game.TilesAndGrids.Corner;
import com.varcore.game.TilesAndGrids.Direction;
import com.varcore.game.TilesAndGrids.ExpansionMaskGenerator;
import com.varcore.game.TilesAndGrids.ExpansionProfile;
import com.varcore.game.TilesAndGrids.GridManager;
import com.varcore.game.TilesAndGrids.MaterialFamily;
import com.varcore.game.TilesAndGrids.PixelMask;
import com.varcore.game.TilesAndGrids.TileMaterial;
import com.varcore.game.TilesAndGrids.TileMaterialRegistry;

/** Deterministic pixel-art pebbles clipped to visible dirt and stone material. */
public final class RockPebbleField
{
    private static final int MASK_SIZE = 32;
    private static final int MAX_CACHED_COVERAGE = 2048;
    private static final FringeInfo NO_FRINGE = new FringeInfo(null, 0, null);
    /**
     * Transition masks are deterministic for a cell and its eight neighbours.
     * Caching them avoids rebuilding and merging several 32x32 masks every frame.
     */
    private static final Map<Long, FringeInfo> COVERAGE_CACHE =
            new LinkedHashMap<>(256, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, FringeInfo> eldest)
                {
                    return size() > MAX_CACHED_COVERAGE;
                }
            };

    private RockPebbleField() {}

    public static void draw(
            Renderer renderer, GridManager grid, CamGame cam,
            TileMaterialRegistry materials,
            IntFunction<Color> averageColorOfTexture,
            IntFunction<Integer> textureIdOfTile,
            int minX, int minY, int maxX, int maxY, int layer)
    {
        float zoom = cam.getZoom();
        if (zoom < 0.72f || materials == null)
        {
            return;
        }

        int tileSize = grid.getTilesize();
        float densityScale = zoom < 0.9f ? 0.65f : 1f;
        for (int gy = minY; gy <= maxY; gy++)
        {
            for (int gx = minX; gx <= maxX; gx++)
            {
                if (grid.hasOverlay(gx, gy))
                {
                    continue;
                }

                int tileId = grid.getTileId(gx, gy);
                TileMaterial own = materials.getMaterial(tileId);
                MaterialFamily family = decorFamily(own);
                PixelMask coverage = null;
                int sourceTile = tileId;

                if (family == null)
                {
                    FringeInfo fringe = cachedExpansionCoverage(grid, materials, gx, gy, own);
                    if (fringe == NO_FRINGE)
                    {
                        continue;
                    }
                    family = fringe.family;
                    sourceTile = fringe.sourceTile;
                    coverage = fringe.coverage;
                }

                int density = family == MaterialFamily.STONE ? 3 : 2;
                density = Math.max(1, Math.round(density * densityScale));
                // Empty spaces in the hash make the distribution feel clustered, not uniform.
                if ((hash(gx, gy, sourceTile, 0x50454242) & 15) == 0)
                {
                    density += 1;
                }

                int attempts = coverage == null ? density * 2 : density * 3;
                Color ground = averageColorOfTexture.apply(textureIdOfTile.apply(sourceTile));
                float worldX = grid.getWorldX() + gx * tileSize;
                float worldY = grid.getWorldY() + gy * tileSize;
                int placed = 0;

                for (int i = 0; i < attempts && placed < density; i++)
                {
                    int seed = hash(gx, gy, i, sourceTile ^ 0x524F434B);
                    // Leave a small gutter so adjacent cells do not form clipped half-rocks.
                    float lx = 3f + ((seed & 0xFF) / 255f) * (tileSize - 7f);
                    float ly = 5f + (((seed >>> 8) & 0xFF) / 255f) * (tileSize - 10f);
                    int px = Math.max(0, Math.min(MASK_SIZE - 1, Math.round(lx)));
                    int py = Math.max(0, Math.min(MASK_SIZE - 1, Math.round(ly)));
                    if (coverage != null && coverage.getAlpha(px, py) == 0)
                    {
                        continue;
                    }

                    boolean stone = family == MaterialFamily.STONE;
                    float w = (stone ? 2.8f : 2.2f) + ((seed >>> 16) & 1);
                    float h = Math.max(2f, w * (0.48f + ((seed >>> 19) & 3) * 0.08f));
                    if ((seed & 63) == 0)
                    {
                        w *= 1.55f;
                        h *= 1.35f;
                    }

                    Color shadow = shade(ground, stone ? -30 : -24, 165);
                    Color body = pebbleColor(ground, seed, stone);
                    Color highlight = shade(body, stone ? 18 : 12, 195);
                    float sx = (worldX + lx - cam.getCamX()) * zoom;
                    float sy = (worldY + ly - cam.getCamY()) * zoom;
                    float sw = Math.max(2f, w * zoom);
                    float sh = Math.max(1.5f, h * zoom);

                    // Three tiny rectangles read as a faceted pebble in the pixel-art world.
                    renderer.drawRect(sx - sw * 0.5f + zoom, sy - sh * 0.25f + zoom,
                            sw, sh, shadow, true, layer);
                    renderer.drawRect(sx - sw * 0.5f, sy - sh * 0.5f,
                            sw, sh, body, true, layer + 1);
                    // At small screen sizes the highlight becomes sub-pixel noise and
                    // costs an extra queued draw for no visible benefit.
                    if (zoom >= 0.95f && sw >= 3.25f)
                    {
                        renderer.drawRect(sx - sw * 0.32f, sy - sh * 0.42f,
                                Math.max(1f, sw * 0.42f), Math.max(1f, sh * 0.28f),
                                highlight, true, layer + 2);
                    }
                    placed++;
                }
            }
        }
    }

    private static MaterialFamily decorFamily(TileMaterial material)
    {
        if (material == null) return null;
        MaterialFamily family = material.getFamily();
        return family == MaterialFamily.DIRT || family == MaterialFamily.STONE ? family : null;
    }

    private static FringeInfo buildExpansionCoverage(
            GridManager grid, TileMaterialRegistry materials, int gx, int gy, TileMaterial target)
    {
        if (target == null || !target.acceptsExpansion()) return null;
        PixelMask coverage = PixelMask.empty(MASK_SIZE);
        TileMaterial best = null;

        for (Direction dir : Direction.values())
        {
            int nx = gx + dir.dx;
            int ny = gy + dir.dy;
            if ((!dir.isCardinal() && !dir.isDiagonal()) || !grid.inBounds(nx, ny)) continue;
            TileMaterial source = materials.getMaterial(grid.getTileId(nx, ny));
            MaterialFamily family = decorFamily(source);
            if (family == null || !TileMaterial.canExpandOnto(source, target)) continue;
            ExpansionProfile profile = materials.getProfileFor(source);
            if (profile == null) continue;

            PixelMask mask;
            if (dir.isCardinal())
            {
                int count = Math.max(1, profile.getEdgeVariantCount(dir));
                int variant = Math.floorMod(
                        ExpansionMaskGenerator.hash(gx, gy, source.getId(), dir.ordinal()), count);
                mask = profile.getEdgeMask(dir, variant);
            }
            else
            {
                Corner corner = cornerFor(dir);
                if (corner == null) continue;
                int count = Math.max(1, profile.getCornerVariantCount(corner));
                int variant = Math.floorMod(
                        ExpansionMaskGenerator.hash(gx, gy, source.getId(), corner.ordinal() + 40), count);
                mask = profile.getCornerMask(corner, variant);
            }
            coverage.mergeMax(mask);
            if (best == null || source.getDominanceLayer() > best.getDominanceLayer()) best = source;
        }

        return best == null || !coverage.isAnyOpaque()
                ? null : new FringeInfo(coverage, best.getId(), best.getFamily());
    }

    private static FringeInfo cachedExpansionCoverage(
            GridManager grid, TileMaterialRegistry materials, int gx, int gy, TileMaterial target)
    {
        long key = coverageKey(grid, gx, gy);
        FringeInfo cached = COVERAGE_CACHE.get(key);
        if (cached != null)
        {
            return cached;
        }
        FringeInfo built = buildExpansionCoverage(grid, materials, gx, gy, target);
        FringeInfo stored = built == null ? NO_FRINGE : built;
        COVERAGE_CACHE.put(key, stored);
        return stored;
    }

    /** Includes absolute position and neighbour tile IDs, so editor paint invalidates naturally. */
    private static long coverageKey(GridManager grid, int gx, int gy)
    {
        int worldTileX = Math.floorDiv(grid.getWorldX(), grid.getTilesize()) + gx;
        int worldTileY = Math.floorDiv(grid.getWorldY(), grid.getTilesize()) + gy;
        int fingerprint = 0x811C9DC5;
        for (int oy = -1; oy <= 1; oy++)
        {
            for (int ox = -1; ox <= 1; ox++)
            {
                int tile = grid.inBounds(gx + ox, gy + oy)
                        ? grid.getTileId(gx + ox, gy + oy) : -1;
                fingerprint = (fingerprint ^ tile) * 0x01000193;
            }
        }
        long position = ((long) worldTileX << 32) ^ (worldTileY & 0xFFFFFFFFL);
        return position ^ ((long) fingerprint * 0x9E3779B97F4A7C15L);
    }

    private static Corner cornerFor(Direction direction)
    {
        return switch (direction)
        {
            case UP_LEFT -> Corner.TOP_LEFT;
            case UP_RIGHT -> Corner.TOP_RIGHT;
            case DOWN_LEFT -> Corner.BOTTOM_LEFT;
            case DOWN_RIGHT -> Corner.BOTTOM_RIGHT;
            default -> null;
        };
    }

    private static Color pebbleColor(Color ground, int seed, boolean stone)
    {
        int warm = stone ? 0 : 5;
        int variation = ((seed >>> 22) & 15) - 7;
        return new Color(
                clamp(ground.getRed() + variation + warm),
                clamp(ground.getGreen() + variation + warm / 2),
                clamp(ground.getBlue() + variation - (stone ? 0 : 3)), 205);
    }

    private static Color shade(Color color, int amount, int alpha)
    {
        return new Color(clamp(color.getRed() + amount), clamp(color.getGreen() + amount),
                clamp(color.getBlue() + amount), alpha);
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }

    private static int hash(int a, int b, int c, int d)
    {
        int h = a * 374761393 + b * 668265263 + c * 1274126177 + d * 0x85ebca6b;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static final class FringeInfo
    {
        final PixelMask coverage;
        final int sourceTile;
        final MaterialFamily family;

        FringeInfo(PixelMask coverage, int sourceTile, MaterialFamily family)
        {
            this.coverage = coverage;
            this.sourceTile = sourceTile;
            this.family = family;
        }
    }
}
