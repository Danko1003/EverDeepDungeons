package com.varcore.game;

public class CamGame 
{
    /** Far zoom floor. Rendering switches to a cheap mode before this point. */
    private static final float MIN_ZOOM = 0.18f;
    private static final float MAX_ZOOM = 4.0f;
    /** Zoom change per mouse-wheel tick. */
    private static final float ZOOM_PER_TICK = 0.10f;

    private float camX;
    private float camY;
    private float zoom;

    private int viewportWidth = 800;
    private int viewportHeight = 600;

    public CamGame(int camX, int camY, float zoom) {
        this.camX = camX;
        this.camY = camY;
        this.zoom = clampZoom(zoom);
    }

    public void setViewportSize(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public float getCamX() {
        return camX;
    }

    public void setCamX(float camX) {
        this.camX = camX;
    }

    public float getCamY() {
        return camY;
    }

    public void setCamY(float camY) {
        this.camY = camY;
    }

    public float getZoom() {
        return zoom;
    }

    public void setZoom(float zoom) {
        applyZoom(zoom);
    }

    /**
     * Applies mouse-wheel zoom.
     * Engine convention: positive delta = scroll down = zoom out,
     * negative delta = scroll up = zoom in.
     */
    public void zoomByWheel(int wheelDelta) {
        if (wheelDelta == 0) {
            return;
        }
        applyZoom(zoom - wheelDelta * ZOOM_PER_TICK);
    }

    /** Change zoom while keeping the world point under the screen center fixed. */
    private void applyZoom(float newZoom) {
        float oldZoom = this.zoom;
        newZoom = clampZoom(newZoom);

        if (newZoom == oldZoom || oldZoom <= 0f) {
            this.zoom = newZoom;
            return;
        }

        float centerX = viewportWidth * 0.5f;
        float centerY = viewportHeight * 0.5f;

        // World point currently at screen center
        float worldCenterX = camX + centerX / oldZoom;
        float worldCenterY = camY + centerY / oldZoom;

        this.zoom = newZoom;

        // Reposition camera so that same world point stays centered
        camX = worldCenterX - centerX / newZoom;
        camY = worldCenterY - centerY / newZoom;
    }

    private static float clampZoom(float value) {
        if (value < MIN_ZOOM) return MIN_ZOOM;
        if (value > MAX_ZOOM) return MAX_ZOOM;
        return value;
    }
}
