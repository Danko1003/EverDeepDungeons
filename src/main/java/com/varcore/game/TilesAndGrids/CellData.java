package com.varcore.game.TilesAndGrids;

public class CellData
{
    /** Ground / floor tile — survives under walls. */
    private int tileID;
    /** Wall / overlay piece drawn on top of the base tile. 0 = none. */
    private int overlayTileID;
    private float rotation;
    private boolean entrance;
    private final int[][] textureIDQuadrant = new int[2][2];

    public CellData(int tileID, int rotation)
    {
        this.tileID = tileID;
        this.overlayTileID = 0;
        this.rotation = rotation;
        this.entrance = false;
    }

    public void setTileID(int tileID)
    {
        this.tileID = tileID;
    }

    public int getTileID()
    {
        return tileID;
    }

    public int getOverlayTileID()
    {
        return overlayTileID;
    }

    public void setOverlayTileID(int overlayTileID)
    {
        this.overlayTileID = overlayTileID;
    }

    public boolean hasOverlay()
    {
        return overlayTileID != 0;
    }

    public float getRotation()
    {
        return rotation;
    }

    public void setRotation(float rotation)
    {
        this.rotation = rotation;
    }

    public boolean isEntrance()
    {
        return entrance;
    }

    public void setEntrance(boolean entrance)
    {
        this.entrance = entrance;
    }
}
