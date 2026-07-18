package com.varcore.game.TilesAndGrids;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds projected cell visuals from neighbor expansions.
 * When several materials expand onto one empty cell, they share it: each pixel
 * goes to the expander closest to its source edge so fringes meet without
 * stacking. Same-family shades still prefer higher dominance.
 */
public final class TransitionResolver
{
    private final TileMaterialRegistry materials;
    private final int tileSize;

    public TransitionResolver(TileMaterialRegistry materials, int tileSize)
    {
        this.materials = materials;
        this.tileSize = tileSize;
    }

    public CellVisual buildCellVisual(GridManager grid, int cellX, int cellY)
    {
        int tileId = grid.getTileId(cellX, cellY);
        TileMaterial baseMat = materials.getMaterial(tileId);
        if (baseMat == null)
        {
            return null;
        }

        List<Contribution> contributions = collectContributions(grid, cellX, cellY, baseMat);
        MaterialRegion base = new MaterialRegion(baseMat, PixelMask.full(tileSize));

        // Exclusive masks after collaborative resolve (for inspection / debugging)
        List<MaterialRegion> overlays = bakeExclusiveOverlays(contributions, cellX, cellY);
        return new CellVisual(base, overlays);
    }

    public BufferedImage compose(CellVisual visual, int cellWorldX, int cellWorldY)
    {
        BufferedImage out = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        paintRegion(out, visual.getBase(), cellWorldX, cellWorldY);
        for (MaterialRegion overlay : visual.getOverlays())
        {
            paintRegion(out, overlay, cellWorldX, cellWorldY);
        }
        return out;
    }

    public BufferedImage buildImage(GridManager grid, int cellX, int cellY)
    {
        int tileId = grid.getTileId(cellX, cellY);
        TileMaterial baseMat = materials.getMaterial(tileId);
        if (baseMat == null)
        {
            return null;
        }

        List<Contribution> contributions = collectContributions(grid, cellX, cellY, baseMat);
        int worldX = grid.getWorldX() + cellX * tileSize;
        int worldY = grid.getWorldY() + cellY * tileSize;
        boolean revealCore = isHeavilySurrounded(contributions);

        BufferedImage out = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        // Flat arrays avoid 66 small row-array allocations per rebuilt cell.
        boolean[] burnableOverlay = new boolean[tileSize * tileSize];
        boolean[] baseExposed = new boolean[tileSize * tileSize];
        paintMaterial(out, baseMat, PixelMask.full(tileSize), worldX, worldY);

        // Per-pixel collaborative ownership
        for (int py = 0; py < tileSize; py++)
        {
            for (int px = 0; px < tileSize; px++)
            {
                Contribution winner = pickWinner(contributions, px, py, cellX, cellY, revealCore);
                if (winner == null)
                {
                    baseExposed[py * tileSize + px] = true;
                    continue;
                }
                int rgb = sample(winner.material.getTexture(), worldX + px, worldY + py);
                if (winner.material.hasTrait(MaterialTrait.BURNABLE)
                        && baseMat.hasTrait(MaterialTrait.BURNT))
                {
                    // Keep grass fringe colors normal — only the 1px tip against
                    // remaining burnt gets flaming (below). Tinting the whole fringe
                    // makes a murky rectangular border around the burnt blob.
                    burnableOverlay[py * tileSize + px] = true;
                }
                if ((rgb >>> 24) != 0)
                {
                    out.setRGB(px, py, rgb);
                }
            }
        }
        paintFlamingBorderTips(out, burnableOverlay, baseExposed, worldX, worldY);
        return out;
    }

    /**
     * Thin flame rim where burnable fringe meets remaining burnt base only.
     * No inward smear — that recreated the weird colored tile border.
     */
    private static void paintFlamingBorderTips(
            BufferedImage out,
            boolean[] burnableOverlay,
            boolean[] baseExposed,
            int worldX,
            int worldY)
    {
        int width = out.getWidth();
        int height = out.getHeight();
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                int idx = y * width + x;
                if (!burnableOverlay[idx] || !touchesExposedBase(baseExposed, width, height, x, y))
                {
                    continue;
                }
                int noise = ExpansionMaskGenerator.hash(worldX + x, worldY + y, 0xF1AE, 19);
                int existing = out.getRGB(x, y);
                int heat = noise & 7;
                int fire = switch (heat)
                {
                    case 0 -> 0xFFFFE08A;
                    case 1, 2 -> 0xFFFFC04A;
                    case 3, 4 -> 0xFFF07A24;
                    default -> 0xFFE24A12;
                };
                float amount = heat <= 1 ? 0.82f : heat <= 4 ? 0.72f : 0.62f;
                out.setRGB(x, y, blendRgb(existing, fire, amount));
            }
        }
    }

    private static boolean touchesExposedBase(
            boolean[] baseExposed, int width, int height, int x, int y)
    {
        if (x > 0 && baseExposed[y * width + x - 1])
        {
            return true;
        }
        if (x < width - 1 && baseExposed[y * width + x + 1])
        {
            return true;
        }
        if (y > 0 && baseExposed[(y - 1) * width + x])
        {
            return true;
        }
        if (y < height - 1 && baseExposed[(y + 1) * width + x])
        {
            return true;
        }
        return false;
    }

    private static int blendRgb(int from, int to, float amount)
    {
        int a = (from >>> 24) & 0xFF;
        int r = Math.round(((from >>> 16) & 0xFF) * (1f - amount) + ((to >>> 16) & 0xFF) * amount);
        int g = Math.round(((from >>> 8) & 0xFF) * (1f - amount) + ((to >>> 8) & 0xFF) * amount);
        int b = Math.round((from & 0xFF) * (1f - amount) + (to & 0xFF) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private List<Contribution> collectContributions(
            GridManager grid, int cellX, int cellY, TileMaterial baseMat)
    {
        EnumMap<Direction, TileMaterial> cardinalSource = new EnumMap<>(Direction.class);
        EnumMap<Direction, TileMaterial> diagonalSource = new EnumMap<>(Direction.class);

        for (Direction dir : Direction.values())
        {
            int nx = cellX + dir.dx;
            int ny = cellY + dir.dy;
            if (!grid.inBounds(nx, ny))
            {
                continue;
            }

            TileMaterial neighborMat = materials.getMaterial(grid.getTileId(nx, ny));
            if (!TileMaterial.canExpandOnto(neighborMat, baseMat))
            {
                continue;
            }

            if (dir.isCardinal())
            {
                cardinalSource.put(dir, neighborMat);
            }
            else
            {
                diagonalSource.put(dir, neighborMat);
            }
        }

        List<Contribution> contributions = new ArrayList<>();

        for (Map.Entry<Direction, TileMaterial> e : cardinalSource.entrySet())
        {
            PixelMask mask = edgeMask(e.getValue(), e.getKey(), cellX, cellY);
            if (mask != null && mask.isAnyOpaque())
            {
                contributions.add(new Contribution(e.getValue(), mask, e.getKey(), false));
            }
        }

        for (Corner corner : Corner.values())
        {
            TileMaterial vMat = cardinalSource.get(corner.vertical);
            TileMaterial hMat = cardinalSource.get(corner.horizontal);
            if (vMat != null && hMat != null)
            {
                // Same-material L only: half-tile bevels straighten stairs.
                // Mixed materials (dirt+grass onto stone, etc.) already have both
                // edge masks — a bevel here makes them nibble each other apart.
                if (vMat.getId() == hMat.getId())
                {
                    int variant = Math.floorMod(
                            ExpansionMaskGenerator.hash(
                                    cellX, cellY, vMat.getId(), corner.ordinal() + 90),
                            4);
                    PixelMask bevel = ExpansionMaskGenerator.generateStairBevel(
                            corner, variant);
                    contributions.add(new Contribution(
                            vMat, bevel, corner.diagonal, true));
                }
                continue;
            }

            TileMaterial diagonalMat = diagonalSource.get(corner.diagonal);
            if (diagonalMat == null)
            {
                continue;
            }

            PixelMask mask = cornerMask(diagonalMat, corner, cellX, cellY);
            if (mask != null && mask.isAnyOpaque())
            {
                contributions.add(new Contribution(
                        diagonalMat, mask, corner.diagonal, true));
            }
        }

        return contributions;
    }

    /**
     * Mask-first ownership. Different materials share a cell by proximity to
     * their source edge. Small gaps (≤4px) between opposing fringes are bridged
     * so they meet cleanly without one filling the whole cell.
     * When heavily surrounded, a tiny center core stays with the base tile.
     */
    private Contribution pickWinner(
            List<Contribution> contributions,
            int px,
            int py,
            int cellX,
            int cellY,
            boolean revealCore)
    {
        if (revealCore && inBaseRevealCore(px, py, cellX, cellY))
        {
            return null;
        }

        Contribution bestMasked = null;
        for (Contribution c : contributions)
        {
            if (c.mask.getAlpha(px, py) == 0)
            {
                continue;
            }
            if (bestMasked == null)
            {
                bestMasked = c;
                continue;
            }
            bestMasked = prefer(bestMasked, c, px, py);
        }
        if (bestMasked != null)
        {
            return bestMasked;
        }

        return bridgeGap(contributions, px, py);
    }

    /**
     * True when expanders approach from at least 3 sides — the case where a lone
     * lower-layer tile would otherwise disappear under neighbor fringes/bevels.
     */
    private static boolean isHeavilySurrounded(List<Contribution> contributions)
    {
        boolean north = false;
        boolean east = false;
        boolean south = false;
        boolean west = false;
        for (Contribution c : contributions)
        {
            Direction d = c.fromDir;
            if (d == Direction.UP || d == Direction.UP_LEFT || d == Direction.UP_RIGHT)
            {
                north = true;
            }
            if (d == Direction.RIGHT || d == Direction.UP_RIGHT || d == Direction.DOWN_RIGHT)
            {
                east = true;
            }
            if (d == Direction.DOWN || d == Direction.DOWN_LEFT || d == Direction.DOWN_RIGHT)
            {
                south = true;
            }
            if (d == Direction.LEFT || d == Direction.UP_LEFT || d == Direction.DOWN_LEFT)
            {
                west = true;
            }
        }
        int sides = (north ? 1 : 0) + (east ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0);
        return sides >= 3;
    }

    /** Soft irregular island near the cell center (~3–5 px). */
    private boolean inBaseRevealCore(int px, int py, int cellX, int cellY)
    {
        float cx = (tileSize - 1) * 0.5f;
        float cy = (tileSize - 1) * 0.5f;
        float dx = px - cx;
        float dy = py - cy;
        int seed = ExpansionMaskGenerator.hash(cellX, cellY, px / 2, py / 2);
        float wobble = 0.7f * (float) Math.sin((px * 1.1f + py * 0.9f) + (seed & 7));
        float radius = 3.6f + wobble;
        return dx * dx + dy * dy <= radius * radius;
    }

    /**
     * When two different materials push toward each other but leave a thin gap,
     * fill those pixels with the nearer fringe so the seam reads continuous.
     */
    private Contribution bridgeGap(List<Contribution> contributions, int px, int py)
    {
        if (contributions.size() < 2)
        {
            return null;
        }

        Contribution bestA = null;
        Contribution bestB = null;
        int depthA = Integer.MAX_VALUE;
        int depthB = Integer.MAX_VALUE;
        int reachA = -1;
        int reachB = -1;

        for (Contribution c : contributions)
        {
            int depth = depthFromSource(c, px, py);
            int reach = reachOf(c);
            if (reach < 0)
            {
                continue;
            }

            if (bestA == null)
            {
                bestA = c;
                depthA = depth;
                reachA = reach;
                continue;
            }

            // Prefer a different material as the second candidate (opposing fringe)
            if (c.material.getId() != bestA.material.getId()
                    && (bestB == null || depth < depthB))
            {
                bestB = c;
                depthB = depth;
                reachB = reach;
                continue;
            }

            if (c.material.getId() == bestA.material.getId() && depth < depthA)
            {
                bestA = c;
                depthA = depth;
                reachA = reach;
            }
        }

        if (bestA == null || bestB == null)
        {
            return null;
        }
        // Cross-family bridges (grass↔dirt on stone) fill the gap with speckles.
        // Only close seams between the same family / material.
        if (bestA.material.getFamily() != bestB.material.getFamily())
        {
            return null;
        }
        if (!opposing(bestA.fromDir, bestB.fromDir) && !roughlyOpposing(bestA, bestB, px, py))
        {
            return null;
        }

        final int bridge = 4;
        boolean pastA = depthA > reachA && depthA <= reachA + bridge;
        boolean pastB = depthB > reachB && depthB <= reachB + bridge;
        if (pastA && pastB)
        {
            return depthA <= depthB ? bestA : bestB;
        }
        return null;
    }

    private static boolean opposing(Direction a, Direction b)
    {
        return (a == Direction.UP && b == Direction.DOWN)
                || (a == Direction.DOWN && b == Direction.UP)
                || (a == Direction.LEFT && b == Direction.RIGHT)
                || (a == Direction.RIGHT && b == Direction.LEFT)
                || (a == Direction.UP_LEFT && b == Direction.DOWN_RIGHT)
                || (a == Direction.DOWN_RIGHT && b == Direction.UP_LEFT)
                || (a == Direction.UP_RIGHT && b == Direction.DOWN_LEFT)
                || (a == Direction.DOWN_LEFT && b == Direction.UP_RIGHT);
    }

    /** Soft opposing check via depth axes when directions are mixed cardinal/corner. */
    private boolean roughlyOpposing(Contribution a, Contribution b, int px, int py)
    {
        int da = depthFromSource(a, px, py);
        int db = depthFromSource(b, px, py);
        // Both claim this pixel is "inward" from their edges — good enough to bridge
        return da + db <= tileSize + 2;
    }

    /**
     * Max opaque depth for a contribution, computed once and cached.
     * Avoids O(n²) corner rescans on every pixel during buildImage/bridgeGap.
     */
    private int reachOf(Contribution c)
    {
        if (c.cachedReach != Contribution.REACH_UNCACHED)
        {
            return c.cachedReach;
        }
        int max = -1;
        for (int y = 0; y < tileSize; y++)
        {
            for (int x = 0; x < tileSize; x++)
            {
                if (c.mask.getAlpha(x, y) == 0)
                {
                    continue;
                }
                max = Math.max(max, depthFromSource(c, x, y));
            }
        }
        c.cachedReach = max;
        return max;
    }

    private Contribution prefer(Contribution a, Contribution b, int px, int py)
    {
        int depthA = depthFromSource(a, px, py);
        int depthB = depthFromSource(b, px, py);

        // Same material: nearer source edge owns the pixel.
        if (a.material.getId() == b.material.getId())
        {
            return depthB < depthA ? b : a;
        }

        int layerCmp = Integer.compare(
                b.material.getDominanceLayer(), a.material.getDominanceLayer());
        if (layerCmp != 0)
        {
            // Cross-layer (grass vs dirt on stone, etc.): hierarchy wins in the
            // contested band so lower fringes don't speck through higher ones.
            // Only let proximity override when clearly deep in one fringe.
            if (Math.abs(depthA - depthB) > 5)
            {
                return depthB < depthA ? b : a;
            }
            return layerCmp > 0 ? b : a;
        }

        // Same-layer peers: nearness, then dither on a dead heat.
        if (depthB != depthA)
        {
            return depthB < depthA ? b : a;
        }
        int h = ExpansionMaskGenerator.hash(
                px, py, a.material.getId(), b.material.getId());
        return (h & 1) == 0 ? a : b;
    }

    private int depthFromSource(Contribution c, int px, int py)
    {
        if (c.corner)
        {
            return switch (c.fromDir)
            {
                case UP_LEFT -> Math.max(px, py);
                case UP_RIGHT -> Math.max(tileSize - 1 - px, py);
                case DOWN_LEFT -> Math.max(px, tileSize - 1 - py);
                case DOWN_RIGHT -> Math.max(tileSize - 1 - px, tileSize - 1 - py);
                default -> Math.min(px, py);
            };
        }

        return switch (c.fromDir)
        {
            case UP -> py;
            case DOWN -> tileSize - 1 - py;
            case LEFT -> px;
            case RIGHT -> tileSize - 1 - px;
            default -> 0;
        };
    }

    private List<MaterialRegion> bakeExclusiveOverlays(
            List<Contribution> contributions, int cellX, int cellY)
    {
        java.util.LinkedHashMap<Integer, MaterialRegionBuilder> builders =
                new java.util.LinkedHashMap<>();
        boolean revealCore = isHeavilySurrounded(contributions);

        for (int py = 0; py < tileSize; py++)
        {
            for (int px = 0; px < tileSize; px++)
            {
                Contribution winner = pickWinner(contributions, px, py, cellX, cellY, revealCore);
                if (winner == null)
                {
                    continue;
                }
                MaterialRegionBuilder b = builders.computeIfAbsent(
                        winner.material.getId(),
                        id -> new MaterialRegionBuilder(winner.material, tileSize));
                b.mask.setAlpha(px, py, 255);
            }
        }

        List<MaterialRegion> overlays = new ArrayList<>();
        for (MaterialRegionBuilder b : builders.values())
        {
            if (b.mask.isAnyOpaque())
            {
                overlays.add(new MaterialRegion(b.material, b.mask));
            }
        }
        overlays.sort((a, c) -> Integer.compare(
                a.getMaterial().getDominanceLayer(),
                c.getMaterial().getDominanceLayer()));
        return overlays;
    }

    private PixelMask edgeMask(TileMaterial mat, Direction fromNeighbor, int cellX, int cellY)
    {
        ExpansionProfile profile = materials.getProfileFor(mat);
        if (profile == null)
        {
            return null;
        }
        Direction expandDir = fromNeighbor;
        int variants = Math.max(1, profile.getEdgeVariantCount(expandDir));
        int variant = Math.floorMod(
                ExpansionMaskGenerator.hash(cellX, cellY, mat.getId(), expandDir.ordinal()),
                variants);
        return profile.getEdgeMask(expandDir, variant).copy();
    }

    private PixelMask cornerMask(TileMaterial mat, Corner corner, int cellX, int cellY)
    {
        ExpansionProfile profile = materials.getProfileFor(mat);
        if (profile == null)
        {
            return null;
        }
        int variants = Math.max(1, profile.getCornerVariantCount(corner));
        int variant = Math.floorMod(
                ExpansionMaskGenerator.hash(cellX, cellY, mat.getId(), corner.ordinal() + 40),
                variants);
        return profile.getCornerMask(corner, variant).copy();
    }

    private static void paintRegion(
            BufferedImage out,
            MaterialRegion region,
            int cellWorldX,
            int cellWorldY)
    {
        paintMaterial(out, region.getMaterial(), region.getMask(), cellWorldX, cellWorldY);
    }

    private static void paintMaterial(
            BufferedImage out,
            TileMaterial material,
            PixelMask mask,
            int cellWorldX,
            int cellWorldY)
    {
        BufferedImage texture = material.getTexture();
        for (int py = 0; py < mask.getHeight(); py++)
        {
            for (int px = 0; px < mask.getWidth(); px++)
            {
                if (mask.getAlpha(px, py) == 0)
                {
                    continue;
                }
                int rgb = sample(texture, cellWorldX + px, cellWorldY + py);
                if ((rgb >>> 24) != 0)
                {
                    out.setRGB(px, py, rgb);
                }
            }
        }
    }

    private static int sample(BufferedImage texture, int worldPixelX, int worldPixelY)
    {
        int tw = texture.getWidth();
        int th = texture.getHeight();
        return texture.getRGB(
                Math.floorMod(worldPixelX, tw),
                Math.floorMod(worldPixelY, th));
    }

    private static final class Contribution
    {
        static final int REACH_UNCACHED = -2;

        final TileMaterial material;
        final PixelMask mask;
        final Direction fromDir;
        final boolean corner;
        /** Lazily filled by {@link TransitionResolver#reachOf}; -1 = empty mask. */
        int cachedReach = REACH_UNCACHED;

        Contribution(TileMaterial material, PixelMask mask, Direction fromDir, boolean corner)
        {
            this.material = material;
            this.mask = mask;
            this.fromDir = fromDir;
            this.corner = corner;
        }
    }

    private static final class MaterialRegionBuilder
    {
        final TileMaterial material;
        final PixelMask mask;

        MaterialRegionBuilder(TileMaterial material, int size)
        {
            this.material = material;
            this.mask = PixelMask.empty(size);
        }
    }
}
