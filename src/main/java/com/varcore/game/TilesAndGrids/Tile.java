package com.varcore.game.TilesAndGrids;

public class Tile 
{
    private final int idTile;
    private final int textureId;
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

    public int getTextureId() {
        return textureId;
    }









}


