package com.varcore.game;

import java.awt.event.KeyEvent;

import com.varcore.engine.input.InputManager;

public class Inputs
{
    /** Target pan speed in screen pixels per held frame (scales into world via zoom). */
    private static final float PAN_SCREEN_PX = 14f;

    public void camInputs(InputManager input, CamGame camera, float dt)
    {
        camPan(input, camera);
        camera.zoomByWheel(input.getMouseWheelDelta());
    }

    public void camPan(InputManager input, CamGame camera)
    {
        float zoom = Math.max(0.01f, camera.getZoom());
        // Keep pan feeling similar at every zoom: move ~PAN_SCREEN_PX on screen per tick.
        float step = PAN_SCREEN_PX / zoom;

        if (input.isKeyDown(KeyEvent.VK_A)) { camera.setCamX(camera.getCamX() - step); }
        if (input.isKeyDown(KeyEvent.VK_D)) { camera.setCamX(camera.getCamX() + step); }
        if (input.isKeyDown(KeyEvent.VK_W)) { camera.setCamY(camera.getCamY() - step); }
        if (input.isKeyDown(KeyEvent.VK_S)) { camera.setCamY(camera.getCamY() + step); }
    }
}
