package com.varcore.game.TilesAndGrids;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry for multi-tile structures (walls, stamps, etc.).
 *
 * <p><b>To add a new structure:</b> append a {@code register(...)} call
 * inside {@link #registerStructures()}. Use:
 * <ul>
 *   <li>{@link StructureDefinition#columnWall} — foot stored on grid; mid/cap drawn above</li>
 *   <li>{@link StructureDefinition#stamp} — fixed multi-cell overlays</li>
 * </ul>
 *
 * Column walls only store the <b>foot</b> cell so you can place another wall
 * on cells that the mid/cap merely draw over (“behind” a front wall).
 */
public class StructureRegistry
{
    /** One drawn tile relative to a stored foot (dy negative = up). */
    public static final class VisualPart
    {
        public final int dy;
        public final int tileId;
        /** Extra world-pixel lift (negative = up). Applied after cell offset. */
        public final int pixelOffsetY;

        public VisualPart(int dy, int tileId)
        {
            this(dy, tileId, 0);
        }

        public VisualPart(int dy, int tileId, int pixelOffsetY)
        {
            this.dy = dy;
            this.tileId = tileId;
            this.pixelOffsetY = pixelOffsetY;
        }
    }

    /** Raise the whole column-wall stack this many world pixels (foot + mid + cap). */
    public static final int COLUMN_WALL_LIFT_PX = 16;

    private final List<StructureDefinition> structures = new ArrayList<>();
    private final Map<Integer, StructureDefinition> byBrush = new HashMap<>();
    private final Map<Integer, StructureDefinition> byPiece = new HashMap<>();
    private final Set<Integer> hiddenPaletteTiles = new HashSet<>();

    public StructureRegistry()
    {
        registerStructures();
    }

    /**
     * ============================================================
     *  ADD / EDIT STRUCTURES HERE
     * ============================================================
     */
    private void registerStructures()
    {
        // Stone wall: only the foot is stored. Mid + top are drawn above it,
        // so those cells stay free for placing another wall behind.
        register(StructureDefinition.columnWall(
                "stone_wall",
                "Stone Wall",
                TileRegistry.WALL_FACE_ID,
                TileRegistry.WALL_STACK_HEIGHT,
                TileRegistry.WALL_FACE_ID,
                TileRegistry.WALL_FACE_OVERLAY_ID,
                TileRegistry.WALL_TOP_ID
        ));

        // Example stamp (uncomment / copy to add props):
        // register(StructureDefinition.stamp(
        //         "example_stamp",
        //         "Example Stamp",
        //         SOME_BRUSH_TILE_ID,
        //         new StructureDefinition.StampPart(0, 0, TILE_A),
        //         new StructureDefinition.StampPart(1, 0, TILE_B)
        // ));
    }

    private void register(StructureDefinition def)
    {
        structures.add(def);
        byBrush.put(def.getBrushTileId(), def);
        for (int pieceId : def.getPieceTileIds())
        {
            byPiece.putIfAbsent(pieceId, def);
        }
        hiddenPaletteTiles.addAll(def.getHiddenPaletteTileIds());
    }

    public List<StructureDefinition> getStructures()
    {
        return List.copyOf(structures);
    }

    public boolean isStructureBrush(int tileId)
    {
        return byBrush.containsKey(tileId);
    }

    public boolean isStructurePiece(int tileId)
    {
        return byPiece.containsKey(tileId);
    }

    public boolean isHiddenPalettePiece(int tileId)
    {
        return hiddenPaletteTiles.contains(tileId);
    }

    /**
     * Mid/cap tiles for column walls are visuals only — skip when iterating cell overlays
     * if they still exist from older saves; feet expand into these visuals.
     */
    public boolean isColumnWallVisualOnlyPiece(int tileId)
    {
        StructureDefinition def = byPiece.get(tileId);
        return def != null
                && def.getKind() == StructureDefinition.Kind.COLUMN_WALL
                && tileId != def.getFootTileId();
    }

    /** True when this overlay is the stored collision foot of a column wall. */
    public boolean isColumnWallFoot(int tileId)
    {
        StructureDefinition def = byPiece.get(tileId);
        return def != null
                && def.getKind() == StructureDefinition.Kind.COLUMN_WALL
                && tileId == def.getFootTileId();
    }

    public List<Tile> filterPlaceableTiles(List<Tile> candidates)
    {
        List<Tile> list = new ArrayList<>();
        for (Tile tile : candidates)
        {
            if (!isHiddenPalettePiece(tile.getId()))
            {
                list.add(tile);
            }
        }
        return list;
    }

    public StructureDefinition getByBrush(int brushTileId)
    {
        return byBrush.get(brushTileId);
    }

    public StructureDefinition getByPiece(int pieceTileId)
    {
        return byPiece.get(pieceTileId);
    }

    public int getHoverHeight(int brushTileId)
    {
        StructureDefinition def = byBrush.get(brushTileId);
        return def == null ? 1 : def.getStackHeight();
    }

    /**
     * Visual stack for an overlay cell. Column-wall feet expand to mid/cap above
     * (including above the room edge). Other overlays draw as a single tile.
     */
    public List<VisualPart> getVisualParts(int overlayTileId)
    {
        StructureDefinition def = byPiece.get(overlayTileId);
        if (def != null
                && def.getKind() == StructureDefinition.Kind.COLUMN_WALL
                && overlayTileId == def.getFootTileId())
        {
            List<VisualPart> parts = new ArrayList<>();
            int h = def.getStackHeight();
            int lift = -COLUMN_WALL_LIFT_PX;
            parts.add(new VisualPart(0, def.getFootTileId(), lift));
            for (int i = 1; i < h - 1; i++)
            {
                parts.add(new VisualPart(-i, def.getMiddleTileId(), lift));
            }
            if (h >= 2)
            {
                parts.add(new VisualPart(-(h - 1), def.getCapTileId(), lift));
            }
            return parts;
        }

        return List.of(new VisualPart(0, overlayTileId));
    }

    public boolean place(GridManager grid, int brushTileId, int gridX, int gridY)
    {
        StructureDefinition def = byBrush.get(brushTileId);
        if (def == null)
        {
            return false;
        }

        switch (def.getKind())
        {
            case COLUMN_WALL -> placeColumnWall(grid, def, gridX, gridY);
            case STAMP -> placeStamp(grid, def, gridX, gridY);
        }
        return true;
    }

    public boolean erase(GridManager grid, int gridX, int gridY)
    {
        if (!grid.inBounds(gridX, gridY) || !grid.hasOverlay(gridX, gridY))
        {
            return false;
        }

        int pieceId = grid.getOverlayTileId(gridX, gridY);
        StructureDefinition def = byPiece.get(pieceId);
        if (def == null)
        {
            return false;
        }

        switch (def.getKind())
        {
            case COLUMN_WALL -> eraseColumnWall(grid, def, gridX, gridY);
            case STAMP -> eraseStampCluster(grid, def, gridX, gridY);
        }
        return true;
    }

    // --- Column wall: foot-only storage ------------------------------------

    private void placeColumnWall(GridManager grid, StructureDefinition def, int gridX, int gridY)
    {
        if (!grid.inBounds(gridX, gridY))
        {
            return;
        }

        // Only the collision foot is stored. Mid/cap are drawn upward from here,
        // so cells "behind" / above stay free for another wall foot.
        grid.setOverlayTileId(gridX, gridY, def.getFootTileId());

        // Clean up legacy mid/cap cells from older wall placement (if any)
        clearLegacyVisualCells(grid, def, gridX, gridY);
    }

    private void eraseColumnWall(GridManager grid, StructureDefinition def, int gridX, int gridY)
    {
        int piece = grid.getOverlayTileId(gridX, gridY);

        if (piece == def.getFootTileId())
        {
            grid.setOverlayTileId(gridX, gridY, 0);
            clearLegacyVisualCells(grid, def, gridX, gridY);
            return;
        }

        // Legacy save: clicked mid/cap — remove nearby foot + visual cells
        int footY = findLegacyFoot(grid, def, gridX, gridY);
        if (footY >= 0)
        {
            grid.setOverlayTileId(gridX, footY, 0);
            clearLegacyVisualCells(grid, def, gridX, footY);
        }
        grid.setOverlayTileId(gridX, gridY, 0);
    }

    private void clearLegacyVisualCells(GridManager grid, StructureDefinition def, int footX, int footY)
    {
        int h = def.getStackHeight();
        for (int i = 1; i < h; i++)
        {
            int gy = footY - i;
            if (!grid.inBounds(footX, gy))
            {
                continue;
            }
            int id = grid.getOverlayTileId(footX, gy);
            if (id == def.getMiddleTileId() || id == def.getCapTileId())
            {
                grid.setOverlayTileId(footX, gy, 0);
            }
        }
    }

    private int findLegacyFoot(GridManager grid, StructureDefinition def, int gridX, int gridY)
    {
        int h = def.getStackHeight();
        for (int dist = 0; dist < h; dist++)
        {
            int fy = gridY + dist;
            if (grid.inBounds(gridX, fy) && grid.getOverlayTileId(gridX, fy) == def.getFootTileId())
            {
                return fy;
            }
        }
        return -1;
    }

    // --- Stamp rules -------------------------------------------------------

    private void placeStamp(GridManager grid, StructureDefinition def, int gridX, int gridY)
    {
        for (StructureDefinition.StampPart part : def.getStampParts())
        {
            int x = gridX + part.dx;
            int y = gridY + part.dy;
            if (grid.inBounds(x, y))
            {
                grid.setOverlayTileId(x, y, part.overlayTileId);
            }
        }
    }

    private void eraseStampCluster(GridManager grid, StructureDefinition def, int startX, int startY)
    {
        boolean[][] visited = new boolean[grid.getHeight()][grid.getWidth()];
        ArrayList<int[]> stack = new ArrayList<>();
        stack.add(new int[] { startX, startY });

        while (!stack.isEmpty())
        {
            int[] p = stack.remove(stack.size() - 1);
            int x = p[0];
            int y = p[1];
            if (!grid.inBounds(x, y) || visited[y][x])
            {
                continue;
            }
            visited[y][x] = true;

            if (!def.usesPiece(grid.getOverlayTileId(x, y)))
            {
                continue;
            }

            grid.setOverlayTileId(x, y, 0);
            stack.add(new int[] { x + 1, y });
            stack.add(new int[] { x - 1, y });
            stack.add(new int[] { x, y + 1 });
            stack.add(new int[] { x, y - 1 });
        }
    }
}
