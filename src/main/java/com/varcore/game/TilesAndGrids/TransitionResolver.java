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
        List<MaterialRegion> overlays = bakeExclusiveOverlays(contributions);
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

        BufferedImage out = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        paintMaterial(out, baseMat, PixelMask.full(tileSize), worldX, worldY);

        // Per-pixel collaborative ownership
        for (int py = 0; py < tileSize; py++)
        {
            for (int px = 0; px < tileSize; px++)
            {
                Contribution winner = pickWinner(contributions, px, py);
                if (winner == null)
                {
                    continue;
                }
                int rgb = sample(winner.material.getTexture(), worldX + px, worldY + py);
                if ((rgb >>> 24) != 0)
                {
                    out.setRGB(px, py, rgb);
                }
            }
        }
        return out;
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
            TileMaterial cornerMat = null;
            Direction sourceDir = corner.diagonal;

            TileMaterial vMat = cardinalSource.get(corner.vertical);
            TileMaterial hMat = cardinalSource.get(corner.horizontal);
            if (vMat != null && hMat != null)
            {
                cornerMat = vMat.getId() == hMat.getId()
                        ? vMat
                        : (vMat.getDominanceLayer() >= hMat.getDominanceLayer() ? vMat : hMat);
                sourceDir = corner.diagonal;

                // Concave L from a diagonal stair: fill a half-tile bevel so the
                // path reads as a straighter diagonal instead of tile steps.
                int variant = Math.floorMod(
                        ExpansionMaskGenerator.hash(
                                cellX, cellY, cornerMat.getId(), corner.ordinal() + 90),
                        4);
                PixelMask bevel = ExpansionMaskGenerator.generateStairBevel(corner, variant);
                contributions.add(new Contribution(cornerMat, bevel, sourceDir, true));
                continue;
            }

            TileMaterial diagonalMat = diagonalSource.get(corner.diagonal);
            if (diagonalMat != null)
            {
                cornerMat = diagonalMat;
                sourceDir = corner.diagonal;
            }

            if (cornerMat == null)
            {
                continue;
            }

            PixelMask mask = cornerMask(cornerMat, corner, cellX, cellY);
            if (mask != null && mask.isAnyOpaque())
            {
                contributions.add(new Contribution(cornerMat, mask, sourceDir, true));
            }
        }

        return contributions;
    }

    /**
     * Mask-first ownership. Different materials share a cell by proximity to
     * their source edge. Small gaps (≤4px) between opposing fringes are bridged
     * so they meet cleanly without one filling the whole cell.
     */
    private Contribution pickWinner(List<Contribution> contributions, int px, int py)
    {
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
            int reach = maxOpaqueDepth(c, px, py);
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

    /** How far this contribution's mask reaches along the ray from its source through (px,py). */
    private int maxOpaqueDepth(Contribution c, int px, int py)
    {
        int max = -1;
        if (!c.corner)
        {
            switch (c.fromDir)
            {
                case UP -> {
                    for (int y = 0; y < tileSize; y++)
                    {
                        if (c.mask.getAlpha(px, y) > 0)
                        {
                            max = y;
                        }
                    }
                }
                case DOWN -> {
                    for (int y = tileSize - 1; y >= 0; y--)
                    {
                        if (c.mask.getAlpha(px, y) > 0)
                        {
                            max = tileSize - 1 - y;
                        }
                    }
                }
                case LEFT -> {
                    for (int x = 0; x < tileSize; x++)
                    {
                        if (c.mask.getAlpha(x, py) > 0)
                        {
                            max = x;
                        }
                    }
                }
                case RIGHT -> {
                    for (int x = tileSize - 1; x >= 0; x--)
                    {
                        if (c.mask.getAlpha(x, py) > 0)
                        {
                            max = tileSize - 1 - x;
                        }
                    }
                }
                default -> {
                }
            }
            return max;
        }

        // Corner: farthest opaque chebyshev distance from the corner origin
        for (int y = 0; y < tileSize; y++)
        {
            for (int x = 0; x < tileSize; x++)
            {
                if (c.mask.getAlpha(x, y) == 0)
                {
                    continue;
                }
                int d = switch (c.fromDir)
                {
                    case UP_LEFT -> Math.max(x, y);
                    case UP_RIGHT -> Math.max(tileSize - 1 - x, y);
                    case DOWN_LEFT -> Math.max(x, tileSize - 1 - y);
                    case DOWN_RIGHT -> Math.max(tileSize - 1 - x, tileSize - 1 - y);
                    default -> Math.min(x, y);
                };
                max = Math.max(max, d);
            }
        }
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

        // Different materials: proximity first so fringes meet smoothly.
        // When depths are nearly equal, higher dominance layer wins.
        if (Math.abs(depthA - depthB) > 2)
        {
            return depthB < depthA ? b : a;
        }

        int layerCmp = Integer.compare(
                b.material.getDominanceLayer(), a.material.getDominanceLayer());
        if (layerCmp != 0)
        {
            return layerCmp > 0 ? b : a;
        }
        if (depthB != depthA)
        {
            return depthB < depthA ? b : a;
        }
        return b.material.getId() > a.material.getId() ? b : a;
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

    private List<MaterialRegion> bakeExclusiveOverlays(List<Contribution> contributions)
    {
        java.util.LinkedHashMap<Integer, MaterialRegionBuilder> builders =
                new java.util.LinkedHashMap<>();

        for (int py = 0; py < tileSize; py++)
        {
            for (int px = 0; px < tileSize; px++)
            {
                Contribution winner = pickWinner(contributions, px, py);
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
        final TileMaterial material;
        final PixelMask mask;
        final Direction fromDir;
        final boolean corner;

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
