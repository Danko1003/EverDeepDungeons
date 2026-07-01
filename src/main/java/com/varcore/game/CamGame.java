package com.varcore.game;

public class CamGame 
{
    private int camX;
    private int camY;
    private float zoom;

    public CamGame(int camX, int camY, float zoom) {
        this.camX = camX;
        this.camY = camY;
        this.zoom = zoom;
    }
    
    public int getCamX() {
        return camX;
    }
    public void setCamX(int camX) {
        this.camX = camX;
    }
    public int getCamY() {
        return camY;
    }
    public void setCamY(int camY) {
        this.camY = camY;
    }
    public float getZoom() {
        return zoom;
    }
    public void setZoom(float zoom) {
        this.zoom = zoom;
    }



}
