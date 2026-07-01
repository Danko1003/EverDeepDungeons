package com.varcore.game.TilesAndGrids;

public class Tile 
{
    private int textureId;
    private final int idTile;



    private TileType type;
    private boolean walkable;
    public enum TileType
    {
        WALL, FLOOR
    }


    public Tile(int id, TileType type, boolean collsion, int textureId)
    {
        this.idTile = id;
        this.type = type;
        this.walkable = collsion;
        this.textureId = textureId;
    }

    public int getId() {
        return idTile;
    }
}


