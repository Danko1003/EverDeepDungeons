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
import com.varcore.game.TilesAndGrids.MaterialTrait;
import com.varcore.game.TilesAndGrids.PixelMask;
import com.varcore.game.TilesAndGrids.TileMaterial;
import com.varcore.game.TilesAndGrids.TileMaterialRegistry;

/**
 * Procedural grass blades drawn only where grass is visually present.
 * On grass tiles that means the full cell; on fringe cells blades are clipped
 * to the same expansion masks used by material projection.
 */
public final class GrassBladeField
{
    private static final int MASK_SIZE = 32;
    private static final int MAX_CACHED_COVERAGE = 2048;
    private static final FringeInfo NO_FRINGE = new FringeInfo(null, 0, 0);
    private static final Map<Long, FringeInfo> COVERAGE_CACHE =
            new LinkedHashMap<>(256, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, FringeInfo> eldest)
                {
                    return size() > MAX_CACHED_COVERAGE;
                }
            };

    private GrassBladeField()
    {
    }

    public static boolean isGrassTile(int tileId)
    {
        return tileId >= 4 && tileId <= 7;
    }

    /** Blades per cell before zoom scaling. Kept modest to avoid GC freezes. */
    public static int baseDensity(int grassTileId)
    {
        return switch (grassTileId)
        {
            case 4 -> 3;
            case 5 -> 4;
            case 6 -> 6;
            case 7 -> 8;
            default -> 0;
        };
    }

    // Reused palettes — avoid thousands of Color allocations per frame.
    private static final Color[] CHARCOAL = {
            new Color(22, 18, 16, 240),
            new Color(28, 22, 18, 240),
            new Color(34, 26, 20, 240),
            new Color(40, 30, 22, 240),
    };
    private static final Color[] FLAME_TIP = {
            new Color(255, 220, 90, 245),
            new Color(255, 170, 40, 240),
            new Color(255, 110, 20, 235),
            new Color(230, 70, 18, 230),
    };
    private static final Color[] EMBER_OUTLINE = {
            new Color(255, 192, 74, 210),
            new Color(255, 130, 36, 200),
            new Color(220, 70, 20, 190),
            new Color(180, 50, 16, 180),
    };

    public static void draw(
            Renderer renderer,
            GridManager grid,
            CamGame cam,
            TileMaterialRegistry materials,
            IntFunction<Color> averageColorOfTexture,
            IntFunction<Integer> textureIdOfTile,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int layer,
            float animTime)
    {
        float zoom = cam.getZoom();
        if (zoom < 0.70f || materials == null)
        {
            return;
        }

        float densityScale = zoom < 0.95f ? 0.45f : 0.75f;
        int tileSize = grid.getTilesize();

        for (int gy = minY; gy <= maxY; gy++)
        {
            for (int gx = minX; gx <= maxX; gx++)
            {
                if (grid.hasOverlay(gx, gy))
                {
                    continue;
                }

                int tileId = grid.getTileId(gx, gy);
                boolean solidGrass = isGrassTile(tileId);
                PixelMask coverage = null;
                int sourceGrass = solidGrass ? tileId : 0;
                int mixNeighbor = 0;
                boolean expansionFringe = false;
                boolean burntFringe = false;

                if (solidGrass)
                {
                    mixNeighbor = findMixNeighbor(grid, materials, gx, gy, tileId);
                    expansionFringe = mixNeighbor > 0;
                }
                else
                {
                    FringeInfo fringe = cachedExpansionCoverage(grid, materials, gx, gy, tileId);
                    if (fringe == NO_FRINGE || fringe.sourceGrass == 0)
                    {
                        continue;
                    }
                    coverage = fringe.coverage;
                    sourceGrass = fringe.sourceGrass;
                    mixNeighbor = fringe.mixNeighbor;
                    expansionFringe = true;
                    TileMaterial target = materials.getMaterial(tileId);
                    burntFringe = target != null && target.hasTrait(MaterialTrait.BURNT);
                }

                int count = Math.max(1, Math.round(baseDensity(sourceGrass) * densityScale));
                if (expansionFringe)
                {
                    count = Math.max(count, Math.round(count * 1.1f));
                }

                // Try more candidates on fringe so we still fill the mask after rejects.
                int attempts = coverage == null ? count : count * 2;

                Color baseAvg = averageColorOfTexture.apply(textureIdOfTile.apply(sourceGrass));
                Color mixAvg = burntFringe
                        ? averageColorOfTexture.apply(textureIdOfTile.apply(tileId))
                        : mixNeighbor > 0
                        ? averageColorOfTexture.apply(textureIdOfTile.apply(mixNeighbor))
                        : baseAvg;

                float sizeBoost = expansionFringe ? 1.35f : 1f;
                float cellWorldX = grid.getWorldX() + gx * tileSize;
                float cellWorldY = grid.getWorldY() + gy * tileSize;

                int placed = 0;
                for (int i = 0; i < attempts && placed < count; i++)
                {
                    int seed = hash(gx, gy, i, 0x67A55);
                    float lx = ((seed & 0xFF) / 255f) * (tileSize - 4) + 2f;
                    float ly = (((seed >>> 8) & 0xFF) / 255f) * (tileSize * 0.55f) + tileSize * 0.35f;

                    int px = Math.min(MASK_SIZE - 1, Math.max(0, Math.round(lx)));
                    int py = Math.min(MASK_SIZE - 1, Math.max(0, Math.round(ly)));

                    if (coverage != null && coverage.getAlpha(px, py) == 0)
                    {
                        continue;
                    }

                    boolean charred = burntFringe;

                    float height = (5f + ((seed >>> 16) & 7)) * sizeBoost;
                    if (charred)
                    {
                        height *= 0.82f;
                    }
                    float lean = (((seed >>> 20) & 7) - 3) * 0.35f * sizeBoost;
                    float phase = ((seed >>> 4) & 0xFF) / 255f * (float) Math.PI * 2f;
                    float sway = (float) Math.sin(animTime * 2.4f + phase) * (1.6f + height * 0.08f);
                    sway += (float) Math.sin(animTime * 3.7f + phase * 1.3f) * 0.55f;
                    if (charred)
                    {
                        sway *= 0.35f;
                    }

                    Color blade;
                    Color tip = null;
                    if (charred)
                    {
                        blade = CHARCOAL[seed & 3];
                    }
                    else
                    {
                        blade = bladeColor(
                                baseAvg, mixAvg, seed,
                                expansionFringe && mixNeighbor > 0);
                        tip = tipColor(blade, seed);
                    }

                    float baseX = cellWorldX + lx;
                    float baseY = cellWorldY + ly;
                    float midX = baseX + lean * 0.5f + sway * 0.35f;
                    float midY = baseY - height * 0.55f;
                    float tipX = baseX + lean + sway;
                    float tipY = baseY - height;

                    float sx0 = (baseX - cam.getCamX()) * zoom;
                    float sy0 = (baseY - cam.getCamY()) * zoom;
                    float sx1 = (midX - cam.getCamX()) * zoom;
                    float sy1 = (midY - cam.getCamY()) * zoom;
                    float sx2 = (tipX - cam.getCamX()) * zoom;
                    float sy2 = (tipY - cam.getCamY()) * zoom;

                    float thickness = Math.max(1f, zoom * 1.1f * (expansionFringe ? 1.2f : 1f));
                    if (charred)
                    {
                        // Flaming tip: bright ember outline + hot tip stroke.
                        Color outline = EMBER_OUTLINE[(seed >>> 3) & 3];
                        Color flame = FLAME_TIP[(seed >>> 5) & 3];
                        float tipThickness = Math.max(1f, thickness * 0.9f);
                        float ot = tipThickness + Math.max(1.2f, zoom * 1.35f);
                        renderer.drawLine(sx1, sy1, sx2, sy2, outline, ot, layer);
                        renderer.drawLine(sx0, sy0, sx1, sy1, blade, thickness, layer + 1);
                        renderer.drawLine(sx1, sy1, sx2, sy2, flame, tipThickness, layer + 1);
                    }
                    else
                    {
                        renderer.drawLine(sx0, sy0, sx1, sy1, blade, thickness, layer);
                        renderer.drawLine(
                                sx1, sy1, sx2, sy2, tip, Math.max(1f, thickness * 0.85f), layer);
                    }
                    placed++;
                }
            }
        }
    }

    /**
     * Builds the same style of edge/corner coverage grass would paint onto this
     * cell, so blades can be clipped to the visible fringe only.
     */
    private static FringeInfo buildExpansionCoverage(
            GridManager grid, TileMaterialRegistry materials, int gx, int gy, int tileId)
    {
        TileMaterial target = materials.getMaterial(tileId);
        if (target == null || !target.acceptsExpansion())
        {
            return null;
        }

        PixelMask coverage = PixelMask.empty(MASK_SIZE);
        int bestGrass = 0;
        int bestLayer = Integer.MIN_VALUE;
        int mixNeighbor = 0;

        for (Direction dir : Direction.values())
        {
            if (!dir.isCardinal() && !dir.isDiagonal())
            {
                continue;
            }
            int nx = gx + dir.dx;
            int ny = gy + dir.dy;
            if (!grid.inBounds(nx, ny))
            {
                continue;
            }
            int nid = grid.getTileId(nx, ny);
            if (!isGrassTile(nid))
            {
                continue;
            }
            TileMaterial grass = materials.getMaterial(nid);
            if (!TileMaterial.canExpandOnto(grass, target))
            {
                continue;
            }

            ExpansionProfile profile = materials.getProfileFor(grass);
            if (profile == null)
            {
                continue;
            }

            if (dir.isCardinal())
            {
                int variants = Math.max(1, profile.getEdgeVariantCount(dir));
                int variant = Math.floorMod(
                        ExpansionMaskGenerator.hash(gx, gy, grass.getId(), dir.ordinal()),
                        variants);
                orMask(coverage, profile.getEdgeMask(dir, variant));
            }
            else
            {
                Corner corner = cornerForDiagonal(dir);
                if (corner == null)
                {
                    continue;
                }
                int variants = Math.max(1, profile.getCornerVariantCount(corner));
                int variant = Math.floorMod(
                        ExpansionMaskGenerator.hash(gx, gy, grass.getId(), corner.ordinal() + 40),
                        variants);
                orMask(coverage, profile.getCornerMask(corner, variant));
            }

            if (grass.getDominanceLayer() > bestLayer)
            {
                if (bestGrass != 0 && bestGrass != nid)
                {
                    mixNeighbor = bestGrass;
                }
                bestLayer = grass.getDominanceLayer();
                bestGrass = nid;
            }
            else if (nid != bestGrass)
            {
                mixNeighbor = nid;
            }
        }

        if (bestGrass == 0 || !coverage.isAnyOpaque())
        {
            return null;
        }
        return new FringeInfo(coverage, bestGrass, mixNeighbor);
    }

    private static FringeInfo cachedExpansionCoverage(
            GridManager grid, TileMaterialRegistry materials, int gx, int gy, int tileId)
    {
        long key = coverageKey(grid, gx, gy);
        FringeInfo cached = COVERAGE_CACHE.get(key);
        if (cached != null)
        {
            return cached;
        }
        FringeInfo built = buildExpansionCoverage(grid, materials, gx, gy, tileId);
        FringeInfo stored = built == null ? NO_FRINGE : built;
        COVERAGE_CACHE.put(key, stored);
        return stored;
    }

    /** Absolute position plus neighbourhood IDs makes paint edits invalidate naturally. */
    private static long coverageKey(GridManager grid, int gx, int gy)
    {
        int worldTileX = Math.floorDiv(grid.getWorldX(), grid.getTilesize()) + gx;
        int worldTileY = Math.floorDiv(grid.getWorldY(), grid.getTilesize()) + gy;
        int fingerprint = 0x811C9DC5;
        for (int oy = -1; oy <= 1; oy++)
        {
            for (int ox = -1; ox <= 1; ox++)
            {
                int id = grid.inBounds(gx + ox, gy + oy)
                        ? grid.getTileId(gx + ox, gy + oy) : -1;
                fingerprint = (fingerprint ^ id) * 0x01000193;
            }
        }
        long position = ((long) worldTileX << 32) ^ (worldTileY & 0xFFFFFFFFL);
        return position ^ ((long) fingerprint * 0x9E3779B97F4A7C15L);
    }

    private static Corner cornerForDiagonal(Direction dir)
    {
        return switch (dir)
        {
            case UP_LEFT -> Corner.TOP_LEFT;
            case UP_RIGHT -> Corner.TOP_RIGHT;
            case DOWN_LEFT -> Corner.BOTTOM_LEFT;
            case DOWN_RIGHT -> Corner.BOTTOM_RIGHT;
            default -> null;
        };
    }

    private static void orMask(PixelMask dest, PixelMask src)
    {
        int size = dest.getWidth();
        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                if (src.getAlpha(x, y) > 0)
                {
                    dest.setAlpha(x, y, 255);
                }
            }
        }
    }

    private static int findMixNeighbor(
            GridManager grid, TileMaterialRegistry materials, int gx, int gy, int tileId)
    {
        TileMaterial self = materials.getMaterial(tileId);
        if (self == null)
        {
            return 0;
        }

        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int best = 0;
        int bestScore = Integer.MIN_VALUE;
        for (int[] d : dirs)
        {
            int nx = gx + d[0];
            int ny = gy + d[1];
            if (!grid.inBounds(nx, ny))
            {
                continue;
            }
            int nid = grid.getTileId(nx, ny);
            if (!isGrassTile(nid) || nid == tileId)
            {
                continue;
            }
            TileMaterial other = materials.getMaterial(nid);
            if (other == null)
            {
                continue;
            }
            boolean interacts = TileMaterial.canExpandOnto(other, self)
                    || TileMaterial.canExpandOnto(self, other);
            if (!interacts)
            {
                continue;
            }
            int score = Math.abs(other.getDominanceLayer() - self.getDominanceLayer()) * 10
                    + (hash(gx, gy, nid, 19) & 7);
            if (score > bestScore)
            {
                bestScore = score;
                best = nid;
            }
        }
        return best;
    }

    private static Color bladeColor(
            Color base, Color mix, int seed, boolean canMix)
    {
        int r = base.getRed();
        int g = base.getGreen();
        int b = base.getBlue();

        if (canMix)
        {
            int mode = seed & 7;
            if (mode <= 2)
            {
                float t = 0.35f + ((seed >>> 3) & 7) / 20f;
                r = lerp(r, mix.getRed(), t);
                g = lerp(g, mix.getGreen(), t);
                b = lerp(b, mix.getBlue(), t);
            }
            else if (mode == 3)
            {
                r = mix.getRed();
                g = mix.getGreen();
                b = mix.getBlue();
            }
        }

        int dr = ((seed >>> 9) & 31) - 14;
        int dg = ((seed >>> 14) & 31) - 10;
        int db = ((seed >>> 19) & 31) - 16;
        r = clamp(r + dr - 18);
        g = clamp(g + dg - 8);
        b = clamp(b + db - 22);
        return new Color(r, g, b, 230);
    }

    private static Color tipColor(Color blade, int seed)
    {
        int lift = 18 + ((seed >>> 6) & 15);
        int yellow = ((seed >>> 11) & 11);
        return new Color(
                clamp(blade.getRed() + lift + yellow),
                clamp(blade.getGreen() + lift + 6),
                clamp(blade.getBlue() + lift - 8),
                240);
    }

    private static int lerp(int a, int b, float t)
    {
        return Math.round(a + (b - a) * t);
    }

    private static int clamp(int v)
    {
        return Math.max(0, Math.min(255, v));
    }

    private static int hash(int a, int b, int c, int d)
    {
        int h = a * 374761393 + b * 668265263 + c * 1274126177 + d * 0x85ebca6b;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static final class FringeInfo
    {
        final PixelMask coverage;
        final int sourceGrass;
        final int mixNeighbor;

        FringeInfo(PixelMask coverage, int sourceGrass, int mixNeighbor)
        {
            this.coverage = coverage;
            this.sourceGrass = sourceGrass;
            this.mixNeighbor = mixNeighbor;
        }
    }
}
