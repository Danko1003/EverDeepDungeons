package com.varcore.game.TilesAndGrids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Definition of a multi-tile structure (walls, props, etc.).
 * Register new ones in {@link StructureRegistry}.
 */
public final class StructureDefinition
{
    public enum Kind
    {
        /**
         * Vertical stack that merges by column. Occupancy is rebuilt so
         * foot / middle / cap stay correct regardless of paint order.
         */
        COLUMN_WALL,

        /**
         * Fixed stamp of overlay parts relative to the click cell.
         * Parts are written as-is (dx right, dy down; negative dy = up).
         */
        STAMP
    }

    public static final class StampPart
    {
        public final int dx;
        public final int dy;
        public final int overlayTileId;

        public StampPart(int dx, int dy, int overlayTileId)
        {
            this.dx = dx;
            this.dy = dy;
            this.overlayTileId = overlayTileId;
        }
    }

    private final String id;
    private final String name;
    private final Kind kind;
    private final int brushTileId;
    private final int stackHeight;
    private final int footTileId;
    private final int middleTileId;
    private final int capTileId;
    private final List<StampPart> stampParts;
    private final List<Integer> pieceTileIds;

    private StructureDefinition(
            String id,
            String name,
            Kind kind,
            int brushTileId,
            int stackHeight,
            int footTileId,
            int middleTileId,
            int capTileId,
            List<StampPart> stampParts,
            List<Integer> pieceTileIds)
    {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.brushTileId = brushTileId;
        this.stackHeight = stackHeight;
        this.footTileId = footTileId;
        this.middleTileId = middleTileId;
        this.capTileId = capTileId;
        this.stampParts = stampParts;
        this.pieceTileIds = pieceTileIds;
    }

    /**
     * Vertical wall-style structure: click places {@code stackHeight} cells upward,
     * then the column run is rebuilt as foot → middle(s) → cap.
     */
    public static StructureDefinition columnWall(
            String id,
            String name,
            int brushTileId,
            int stackHeight,
            int footTileId,
            int middleTileId,
            int capTileId)
    {
        List<Integer> pieces = new ArrayList<>();
        pieces.add(footTileId);
        pieces.add(middleTileId);
        pieces.add(capTileId);
        return new StructureDefinition(
                id, name, Kind.COLUMN_WALL, brushTileId,
                Math.max(1, stackHeight),
                footTileId, middleTileId, capTileId,
                List.of(),
                List.copyOf(pieces));
    }

    /**
     * Fixed multi-cell stamp. Example:
     * <pre>
     * StructureDefinition.stamp("crate_pair", "Crate Pair", BRUSH_ID,
     *     new StampPart(0, 0, TILE_A),
     *     new StampPart(1, 0, TILE_B));
     * </pre>
     */
    public static StructureDefinition stamp(
            String id,
            String name,
            int brushTileId,
            StampPart... parts)
    {
        List<StampPart> list = new ArrayList<>();
        List<Integer> pieces = new ArrayList<>();
        pieces.add(brushTileId);
        for (StampPart part : parts)
        {
            list.add(part);
            if (!pieces.contains(part.overlayTileId))
            {
                pieces.add(part.overlayTileId);
            }
        }
        return new StructureDefinition(
                id, name, Kind.STAMP, brushTileId,
                1, 0, 0, 0,
                List.copyOf(list),
                List.copyOf(pieces));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Kind getKind() { return kind; }
    public int getBrushTileId() { return brushTileId; }
    public int getStackHeight() { return stackHeight; }
    public int getFootTileId() { return footTileId; }
    public int getMiddleTileId() { return middleTileId; }
    public int getCapTileId() { return capTileId; }
    public List<StampPart> getStampParts() { return stampParts; }
    public List<Integer> getPieceTileIds() { return pieceTileIds; }

    public boolean usesPiece(int tileId)
    {
        return pieceTileIds.contains(tileId);
    }

    /** Pieces that should be hidden from the tile palette (auto-placed only). */
    public List<Integer> getHiddenPaletteTileIds()
    {
        if (kind == Kind.COLUMN_WALL)
        {
            List<Integer> hidden = new ArrayList<>();
            if (middleTileId != brushTileId) hidden.add(middleTileId);
            if (capTileId != brushTileId) hidden.add(capTileId);
            return hidden;
        }
        return Collections.emptyList();
    }
}
