package com.varcore.game.RoomMaker;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import com.varcore.engine.input.InputManager;
import com.varcore.engine.render.Renderer;
import com.varcore.engine.ui.UIAnchor;
import com.varcore.engine.ui.UIElement;

/** Simple click-to-focus text field using VarCore input key presses. */
public class UITextField extends UIElement
{
    private static final int MAX_LENGTH = 32;

    private final Font font = new Font("Segoe UI", Font.PLAIN, 13);
    private String text = "";
    private boolean focused;
    private String placeholder = "";

    public UITextField(float x, float y, float width, float height)
    {
        super(x, y, width, height);
        setAnchor(UIAnchor.TOP_LEFT);
    }

    public void setBounds(float x, float y, float width, float height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public String getText()
    {
        return text;
    }

    public void setText(String text)
    {
        this.text = text == null ? "" : text;
        if (this.text.length() > MAX_LENGTH)
        {
            this.text = this.text.substring(0, MAX_LENGTH);
        }
    }

    public void setPlaceholder(String placeholder)
    {
        this.placeholder = placeholder == null ? "" : placeholder;
    }

    public boolean isFocused()
    {
        return focused;
    }

    public void setFocused(boolean focused)
    {
        this.focused = focused;
    }

    @Override
    public void update(InputManager input)
    {
        if (!visible)
        {
            return;
        }

        if (input.isMousePressed(MouseEvent.BUTTON1))
        {
            focused = contains(input.getMouseX(), input.getMouseY());
        }

        if (!focused)
        {
            return;
        }

        if (input.isKeyPressed(KeyEvent.VK_BACK_SPACE) && !text.isEmpty())
        {
            text = text.substring(0, text.length() - 1);
            return;
        }

        if (text.length() >= MAX_LENGTH)
        {
            return;
        }

        boolean shift = input.isKeyDown(KeyEvent.VK_SHIFT);
        for (int code = KeyEvent.VK_A; code <= KeyEvent.VK_Z; code++)
        {
            if (input.isKeyPressed(code))
            {
                char c = (char) ('a' + (code - KeyEvent.VK_A));
                if (shift) c = Character.toUpperCase(c);
                text += c;
                return;
            }
        }
        for (int code = KeyEvent.VK_0; code <= KeyEvent.VK_9; code++)
        {
            if (input.isKeyPressed(code))
            {
                text += (char) ('0' + (code - KeyEvent.VK_0));
                return;
            }
        }
        if (input.isKeyPressed(KeyEvent.VK_SPACE))
        {
            text += ' ';
        }
        else if (input.isKeyPressed(KeyEvent.VK_MINUS) || input.isKeyPressed(KeyEvent.VK_UNDERSCORE))
        {
            text += shift ? '_' : '-';
        }
    }

    @Override
    public void render(Renderer renderer)
    {
        Color bg = focused ? new Color(40, 48, 64) : new Color(28, 33, 44);
        Color border = focused ? new Color(120, 180, 255) : new Color(70, 80, 100);
        renderer.drawRect(x, y, width, height, bg, true, 930);
        renderer.drawRect(x, y, width, height, border, false, 931);

        String shown;
        Color color;
        if (text.isEmpty() && !focused)
        {
            shown = placeholder;
            color = new Color(120, 128, 144);
        }
        else
        {
            shown = focused ? text + "|" : text;
            color = new Color(230, 230, 236);
        }
        renderer.drawText(shown, x + 8, y + height / 2f + 5, font, color, 932);
    }
}
