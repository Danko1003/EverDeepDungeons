package com.varcore.game.TilesAndGrids;

public class Tile
{
    private final int idTile;
    private final int textureId;
    private final String name;
    private TileType type;
    private boolean walkable;

    public enum TileType
    {
        WALL, FLOOR, OVERLAYING // Overlaying: no collision; renders above walls/entities
    }

    public Tile(int id, TileType type, boolean collision, int textureId)
    {
        this(id, type, collision, textureId, "Tile " + id);
    }

    public Tile(int id, TileType type, boolean collision, int textureId, String name)
    {
        this.idTile = id;
        this.type = type;
        this.walkable = collision;
        this.textureId = textureId;
        this.name = name;
    }

    public int getId() {
        return idTile;
    }

    public int getTextureId() {
        return textureId;
    }

    public String getName() {
        return name;
    }

    public TileType getType() {
        return type;
    }

    public boolean isWalkable() {
        return walkable;
    }
}
