package com.varcore.game.TilesAndGrids;

public class TextureRegion 
{
    public final int sheetID;
    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public TextureRegion(int sheetID, int x, int y, int width, int height)
    {
        this.sheetID = sheetID;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
}