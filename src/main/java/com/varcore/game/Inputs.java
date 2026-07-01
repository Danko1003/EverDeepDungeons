package com.varcore.game;

import java.awt.event.KeyEvent;

import com.varcore.engine.input.InputManager;

public class Inputs
 
{
    public void camInputs(InputManager input, CamGame camera)
    {
        if (input.isKeyDown(KeyEvent.VK_A)) {camera.setCamX(camera.getCamX() - 10);}
        if (input.isKeyDown(KeyEvent.VK_D)) {camera.setCamX(camera.getCamX() + 10);}
        if (input.isKeyDown(KeyEvent.VK_W)) {camera.setCamY(camera.getCamY() - 10);}
        if (input.isKeyDown(KeyEvent.VK_S)) {camera.setCamY(camera.getCamY() + 10);}        
    }
}
