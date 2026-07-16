package com.varcore.game.TilesAndGrids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Individual tile definitions (art + type). Multi-tile placement rules live in
 * {@link StructureRegistry}.
 */
public class TileRegistry
{
    public static final int PLACEHOLDER_TILE_ID = 0;
    public static final int EMPTY_TILE_ID = 1;

    /** Bottom wall face — collision, stored on the overlay layer. */
    public static final int WALL_FACE_ID = 8;
    /** Wall cap — overlaying, no collision. */
    public static final int WALL_TOP_ID = 9;
    /** Upper wall face — overlaying, no collision (same art as face). */
    public static final int WALL_FACE_OVERLAY_ID = 10;

    /** Default stone wall stack height (foot + mid + cap). */
    public static final int WALL_STACK_HEIGHT = 3;

    private final HashMap<Integer, Tile> tiles = new HashMap<>();
    private Tile placeholderTile;

    public TileRegistry()
    {
        registerTiles();
        loadPlaceholderTile();
    }

    public Tile getTile(int tileID)
    {
        if (!tiles.containsKey(tileID))
        {
            return placeholderTile;
        }

        return tiles.get(tileID);
    }

    public List<Tile> getAllTiles()
    {
        List<Tile> list = new ArrayList<>(tiles.values());
        list.sort(Comparator.comparingInt(Tile::getId));
        return list;
    }

    /**
     * Candidate placeable tiles (excludes placeholder).
     * Pass through {@link StructureRegistry#filterPlaceableTiles} before UI use.
     */
    public List<Tile> getPlaceableTiles()
    {
        List<Tile> list = new ArrayList<>();
        for (Tile tile : getAllTiles())
        {
            if (tile.getId() != PLACEHOLDER_TILE_ID)
            {
                list.add(tile);
            }
        }
        return list;
    }

    private void loadPlaceholderTile()
    {
        placeholderTile = new Tile(
            PLACEHOLDER_TILE_ID,
            Tile.TileType.FLOOR,
            false,
            0,
            "Missing"
        );

        tiles.put(PLACEHOLDER_TILE_ID, placeholderTile);
    }

    private void registerTile(Tile tile)
    {
        tiles.put(tile.getId(), tile);
    }

    private void registerTiles()
    {
        // ID, Type, Solid, Texture ID, Name
        registerTile(new Tile(EMPTY_TILE_ID, Tile.TileType.FLOOR, false, 1, "Empty"));
        registerTile(new Tile(2, Tile.TileType.FLOOR, false, 2, "Stone 1"));
        registerTile(new Tile(3, Tile.TileType.FLOOR, false, 3, "Stone 2"));
        registerTile(new Tile(4, Tile.TileType.FLOOR, false, 4, "Grass Light"));
        registerTile(new Tile(5, Tile.TileType.FLOOR, false, 5, "Grass Medium"));
        registerTile(new Tile(6, Tile.TileType.FLOOR, false, 6, "Grass Dark"));
        registerTile(new Tile(7, Tile.TileType.FLOOR, false, 7, "Grass Darkest"));
        registerTile(new Tile(11, Tile.TileType.FLOOR, false, 11, "Dirt One"));
        registerTile(new Tile(13, Tile.TileType.FLOOR, false, 12, "Dirt Three"));
        registerTile(new Tile(14, Tile.TileType.FLOOR, false, 13, "Dirt Two"));
        registerTile(new Tile(15, Tile.TileType.FLOOR, false, 14, "Dirt Burnt"));

        // Wall piece tiles — placement/erase rules: StructureRegistry
        registerTile(new Tile(WALL_FACE_ID, Tile.TileType.WALL, true, 8, "Stone Wall"));
        registerTile(new Tile(WALL_FACE_OVERLAY_ID, Tile.TileType.OVERLAYING, false, 9, "Stone Wall Face Overlay"));
        registerTile(new Tile(WALL_TOP_ID, Tile.TileType.OVERLAYING, false, 10, "Stone Wall Top"));
    }
}
