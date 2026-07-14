package com.varcore.game.RoomMaker;

import com.varcore.engine.ui.UIAnchor;
import com.varcore.engine.ui.UIButton;

/** Thin UIButton subclass so the room editor can reposition tools each frame. */
public class EditorButton extends UIButton
{
    public EditorButton(float x, float y, float width, float height)
    {
        super(x, y, width, height);
    }

    public void setBounds(float x, float y, float width, float height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setPosition(float x, float y)
    {
        this.x = x;
        this.y = y;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }

    public void placeTopRight(int screenW, float marginRight, float y, float width, float height)
    {
        setAnchor(UIAnchor.TOP_LEFT); // we place absolutely after computing right margin
        setBounds(screenW - width - marginRight, y, width, height);
    }
}
