package com.varcore.game.TilesAndGrids;

public class CellData 
{
    private int tileID;

    private float rotation;


    
    public CellData(int tileID, int rotation) {
        this.tileID = tileID;
        this.rotation = rotation;
    }
    public void setTileID(int tileID) {
        this.tileID = tileID;
    }
    public int getTileID() {
        return tileID;
    }
    public float getRotation() {
        return rotation;
    }
    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

}
