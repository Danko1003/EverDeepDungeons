package com.varcore.game.TilesAndGrids;

import java.util.HashMap;

public class TileRegistry 
{
    private final HashMap<Integer, Tile> tiles = new HashMap<>();

    private Tile placeholderTile;
    private static final int PLACEHOLDER_TILE_ID = 0;

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

    private void loadPlaceholderTile()
    {
        placeholderTile = new Tile(
            PLACEHOLDER_TILE_ID,
            Tile.TileType.FLOOR,
            false,
            0
        );

        tiles.put(PLACEHOLDER_TILE_ID, placeholderTile);
    }

    private void registerTile(Tile tile)
    {
        tiles.put(tile.getId(), tile);
    }

    private void registerTiles()
    {
        // ID, Type, Solid, Texture ID

        registerTile(new Tile(1, Tile.TileType.FLOOR, false, 1));
        registerTile(new Tile(2, Tile.TileType.FLOOR, false, 2));
    }
}