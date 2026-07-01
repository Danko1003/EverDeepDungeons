package com.varcore.game.TilesAndGrids;

public class GridManager 
{
    private final int width;
    private final int height;
    private final int tilesize;
    private final int[][] grid;
    private int worldX;
    private int worldY;

    public GridManager(int width, int height, int tilesize, int worldX, int worldY)
    {
        this.width = width;
        this.height = height;
        this.tilesize = tilesize;
        this.worldX = worldX;
        this.worldY = worldY;
        grid = new int[this.height][this.width];
    }

    public int getTileId(int gridX, int gridY)
    {
        return grid[gridY][gridX];
    }

    public void setTileId(int gridX, int gridY, int newID)
    {
        grid[gridY][gridX] = newID;
    }

    public int getTilesize() {
        return tilesize;
    }

    public int getWorldX() {
        return worldX;
    }

    public void setWorldX(int worldX) {
        this.worldX = worldX;
    }

    public int getWorldY() {
        return worldY;
    }

    public void setWorldY(int worldY) {
        this.worldY = worldY;
    }    
}
