package com.varcore.game.TilesAndGrids;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds clean, soft expansion masks — shallow, smoothed edges with light
 * undulation (no hairy combs or deep jagged spikes).
 */
public final class ExpansionMaskGenerator
{
    public enum Style
    {
        SOFT,
        TUFTED,
        JAGGED,
        SPARSE
    }

    private static final int SIZE = 32;
    private static final int VARIANTS = 4;

    private ExpansionMaskGenerator()
    {
    }

    public static ExpansionProfile createProfile(String id, Style style)
    {
        Map<Direction, List<PixelMask>> edges = new EnumMap<>(Direction.class);
        for (Direction dir : new Direction[] { Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.LEFT })
        {
            List<PixelMask> variants = new ArrayList<>(VARIANTS);
            for (int v = 0; v < VARIANTS; v++)
            {
                variants.add(generateEdge(style, dir, v));
            }
            edges.put(dir, variants);
        }

        Map<Corner, List<PixelMask>> corners = new EnumMap<>(Corner.class);
        for (Corner corner : Corner.values())
        {
            List<PixelMask> variants = new ArrayList<>(VARIANTS);
            for (int v = 0; v < VARIANTS; v++)
            {
                variants.add(generateCorner(style, corner, v));
            }
            corners.put(corner, variants);
        }

        return new ExpansionProfile(id, edges, corners);
    }

    public static PixelMask generateEdge(Style style, Direction direction, int variant)
    {
        PixelMask mask = PixelMask.empty(SIZE);
        int seed = hash(style.ordinal(), direction.ordinal(), variant, 0xA11CE);
        int[] depth = buildSmoothedDepth(style, seed, variant);

        // Continuous column fill from the shared edge — smooth silhouette
        for (int i = 0; i < SIZE; i++)
        {
            int d = depth[i];
            for (int k = 0; k < d; k++)
            {
                switch (direction)
                {
                    case UP -> mask.setAlpha(i, k, 255);
                    case DOWN -> mask.setAlpha(i, SIZE - 1 - k, 255);
                    case LEFT -> mask.setAlpha(k, i, 255);
                    case RIGHT -> mask.setAlpha(SIZE - 1 - k, i, 255);
                    default -> {
                    }
                }
            }
        }

        return mask;
    }

    public static PixelMask generateCorner(Style style, Corner corner, int variant)
    {
        PixelMask mask = PixelMask.empty(SIZE);
        int seed = hash(style.ordinal(), corner.ordinal(), variant, 0xC0FFEE);
        int arm = Math.max(4, maxDepthFor(style));

        for (int y = 0; y <= arm + 1; y++)
        {
            for (int x = 0; x <= arm + 1; x++)
            {
                // Soft quarter-circle — clean rounded convex corners
                double nx = x / (double) arm;
                double ny = y / (double) arm;
                double wobble = 0.08 * Math.sin((x + y + variant) * 0.7 + seed);
                if (nx * nx + ny * ny <= 1.0 + wobble)
                {
                    int px;
                    int py;
                    switch (corner)
                    {
                        case TOP_LEFT -> {
                            px = x;
                            py = y;
                        }
                        case TOP_RIGHT -> {
                            px = SIZE - 1 - x;
                            py = y;
                        }
                        case BOTTOM_LEFT -> {
                            px = x;
                            py = SIZE - 1 - y;
                        }
                        case BOTTOM_RIGHT -> {
                            px = SIZE - 1 - x;
                            py = SIZE - 1 - y;
                        }
                        default -> {
                            px = x;
                            py = y;
                        }
                    }
                    if (px >= 0 && py >= 0 && px < SIZE && py < SIZE)
                    {
                        mask.setAlpha(px, py, 255);
                    }
                }
            }
        }

        return mask;
    }

    /**
     * Half-tile diagonal bevel for concave stair corners. Turns a 90° tile step
     * into a ~45° cut so diagonal paths look straighter.
     */
    public static PixelMask generateStairBevel(Corner corner, int variant)
    {
        PixelMask mask = PixelMask.empty(SIZE);
        int seed = hash(corner.ordinal(), variant, 0x57A18, 0xBE7E1);

        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                // Soft wobble on the cut (±2px) so it isn't a perfect ruler line
                int wobble = ((hash(seed, x / 2, y / 2, variant) & 3) - 1);
                int limit = SIZE - 1 + wobble;

                boolean inside = switch (corner)
                {
                    case TOP_LEFT -> x + y <= limit;
                    case TOP_RIGHT -> (SIZE - 1 - x) + y <= limit;
                    case BOTTOM_LEFT -> x + (SIZE - 1 - y) <= limit;
                    case BOTTOM_RIGHT -> (SIZE - 1 - x) + (SIZE - 1 - y) <= limit;
                };

                if (inside)
                {
                    mask.setAlpha(x, y, 255);
                }
            }
        }

        return mask;
    }

    private static int[] buildSmoothedDepth(Style style, int seed, int variant)
    {
        int max = maxDepthFor(style);
        int[] raw = new int[SIZE];
        for (int i = 0; i < SIZE; i++)
        {
            double t = i / (double) (SIZE - 1);
            // One gentle lobe — not noisy spikes
            double wave = 0.72 + 0.28 * Math.sin(Math.PI * t + variant * 0.5);
            int base = (int) Math.round(max * wave);

            if (style == Style.JAGGED)
            {
                base += ((hash(seed, i / 2, variant, 31) & 3) - 1);
            }
            else if (style == Style.TUFTED && (hash(seed, i / 5, variant, 7) & 7) == 0)
            {
                base += 1;
            }

            raw[i] = Math.max(2, Math.min(max, base));
        }

        // Strong smooth so adjacent columns only differ by ~1px
        int[] smooth = new int[SIZE];
        for (int i = 0; i < SIZE; i++)
        {
            int sum = 0;
            int count = 0;
            for (int k = -3; k <= 3; k++)
            {
                int j = i + k;
                if (j < 0 || j >= SIZE)
                {
                    continue;
                }
                sum += raw[j];
                count++;
            }
            smooth[i] = Math.max(2, Math.min(max, Math.round(sum / (float) count)));
        }
        return smooth;
    }

    private static int maxDepthFor(Style style)
    {
        return switch (style)
        {
            case SOFT -> 8;
            case TUFTED -> 9;
            case JAGGED -> 9;
            case SPARSE -> 6;
        };
    }

    public static int hash(int a, int b, int c, int d)
    {
        int h = a * 374761393
                + b * 668265263
                + c * 1274126177
                + d * 0x85ebca6b;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return h;
    }

    /**
     * Deep, scalloped invasion used when burnable grass eats into burnt dirt.
     * Breaks square tile silhouettes on the burnt side only (no spill onto grass).
     */
    public static PixelMask generateBurnInvasionEdge(Direction direction, int variant, int seed)
    {
        PixelMask mask = PixelMask.empty(SIZE);
        int maxDepth = 12;
        for (int i = 0; i < SIZE; i++)
        {
            double t = i / (double) (SIZE - 1);
            double lobe = 0.62 + 0.38 * Math.sin(Math.PI * t + variant * 0.7);
            double scallop = 0.14 * Math.sin(i * 0.55 + seed * 0.01);
            int depth = (int) Math.round(maxDepth * (lobe + scallop));
            depth += ((hash(seed, i / 2, variant, 41) & 5) - 2);
            depth = Math.max(7, Math.min(SIZE - 3, depth));
            for (int k = 0; k < depth; k++)
            {
                switch (direction)
                {
                    case UP -> mask.setAlpha(i, k, 255);
                    case DOWN -> mask.setAlpha(i, SIZE - 1 - k, 255);
                    case LEFT -> mask.setAlpha(k, i, 255);
                    case RIGHT -> mask.setAlpha(SIZE - 1 - k, i, 255);
                    default -> {
                    }
                }
            }
        }
        return mask;
    }

    /** Soft quarter-blob corner for burn invasion / char spill. */
    public static PixelMask generateBurnInvasionCorner(Corner corner, int variant, int seed)
    {
        PixelMask mask = PixelMask.empty(SIZE);
        int arm = 12 + (variant & 3);
        for (int y = 0; y <= arm + 2; y++)
        {
            for (int x = 0; x <= arm + 2; x++)
            {
                double nx = x / (double) arm;
                double ny = y / (double) arm;
                double wobble = 0.12 * Math.sin((x + y) * 0.55 + seed * 0.02 + variant);
                if (nx * nx + ny * ny <= 1.05 + wobble)
                {
                    int px;
                    int py;
                    switch (corner)
                    {
                        case TOP_LEFT -> {
                            px = x;
                            py = y;
                        }
                        case TOP_RIGHT -> {
                            px = SIZE - 1 - x;
                            py = y;
                        }
                        case BOTTOM_LEFT -> {
                            px = x;
                            py = SIZE - 1 - y;
                        }
                        case BOTTOM_RIGHT -> {
                            px = SIZE - 1 - x;
                            py = SIZE - 1 - y;
                        }
                        default -> {
                            px = x;
                            py = y;
                        }
                    }
                    if (px >= 0 && py >= 0 && px < SIZE && py < SIZE)
                    {
                        mask.setAlpha(px, py, 255);
                    }
                }
            }
        }
        return mask;
    }
}
