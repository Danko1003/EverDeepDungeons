package com.varcore.game.RoomMaker;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

import com.varcore.engine.input.InputManager;
import com.varcore.engine.render.Renderer;
import com.varcore.engine.ui.UIAnchor;
import com.varcore.engine.ui.UIElement;

/**
 * Left-side room settings: name, dimensions, export, entrances overlay toggle.
 */
public class RoomSettingsPanel extends UIElement
{
    public static final float PANEL_W = 220f;
    public static final float PANEL_H = 268f;

    private static final float MARGIN = 10f;
    private static final float ROW_H = 28f;

    private final Font titleFont = new Font("Segoe UI", Font.BOLD, 14);
    private final Font bodyFont = new Font("Segoe UI", Font.PLAIN, 12);
    private final UITextField nameField;

    private int roomWidth;
    private int roomHeight;
    private boolean entrancesMode;
    private boolean hovered;
    private String statusMessage = "";
    private float statusTimer;

    private Runnable onExport;
    private IntConsumer onWidthChanged;
    private IntConsumer onHeightChanged;
    private Runnable onEntrancesToggled;

    public RoomSettingsPanel(float x, float y, int roomWidth, int roomHeight)
    {
        super(x, y, PANEL_W, PANEL_H);
        this.roomWidth = roomWidth;
        this.roomHeight = roomHeight;
        setAnchor(UIAnchor.TOP_LEFT);

        nameField = new UITextField(x + MARGIN, y + 48, PANEL_W - MARGIN * 2, 28f);
        nameField.setPlaceholder("room name...");
        nameField.setText("untitled");
    }

    public void setBounds(float x, float y)
    {
        this.x = x;
        this.y = y;
        this.width = PANEL_W;
        this.height = PANEL_H;
        nameField.setBounds(x + MARGIN, y + 48, PANEL_W - MARGIN * 2, 28f);
    }

    public UITextField getNameField()
    {
        return nameField;
    }

    public String getRoomName()
    {
        return nameField.getText();
    }

    public boolean isEntrancesMode()
    {
        return entrancesMode;
    }

    public void setEntrancesMode(boolean entrancesMode)
    {
        this.entrancesMode = entrancesMode;
    }

    public void toggleEntrancesMode()
    {
        entrancesMode = !entrancesMode;
        if (onEntrancesToggled != null)
        {
            onEntrancesToggled.run();
        }
    }

    public boolean isHovered()
    {
        return hovered;
    }

    public boolean containsPoint(float px, float py)
    {
        return contains(px, py);
    }

    public void setStatus(String message)
    {
        statusMessage = message == null ? "" : message;
        statusTimer = 3.5f;
    }

    public void setOnExport(Runnable onExport)
    {
        this.onExport = onExport;
    }

    public void setOnWidthChanged(IntConsumer onWidthChanged)
    {
        this.onWidthChanged = onWidthChanged;
    }

    public void setOnHeightChanged(IntConsumer onHeightChanged)
    {
        this.onHeightChanged = onHeightChanged;
    }

    public void setOnEntrancesToggled(Runnable onEntrancesToggled)
    {
        this.onEntrancesToggled = onEntrancesToggled;
    }

    public void syncDimensions(int width, int height)
    {
        this.roomWidth = width;
        this.roomHeight = height;
    }

    public void updatePanel(InputManager input, float dt)
    {
        hovered = contains(input.getMouseX(), input.getMouseY());
        if (statusTimer > 0f)
        {
            statusTimer -= dt;
            if (statusTimer <= 0f)
            {
                statusMessage = "";
            }
        }

        nameField.update(input);

        if (!hovered || !visible)
        {
            return;
        }

        if (!input.isMousePressed(MouseEvent.BUTTON1))
        {
            return;
        }

        float mx = input.getMouseX();
        float my = input.getMouseY();

        // Skip name field clicks (handled by field focus)
        if (nameField.contains(mx, my))
        {
            return;
        }

        // Width - / +
        if (hit(mx, my, widthBtnX(false), dimRowY(), 28, ROW_H))
        {
            changeWidth(-1);
            return;
        }
        if (hit(mx, my, widthBtnX(true), dimRowY(), 28, ROW_H))
        {
            changeWidth(1);
            return;
        }

        // Height - / +
        if (hit(mx, my, widthBtnX(false), dimRowY() + 34, 28, ROW_H))
        {
            changeHeight(-1);
            return;
        }
        if (hit(mx, my, widthBtnX(true), dimRowY() + 34, 28, ROW_H))
        {
            changeHeight(1);
            return;
        }

        // Export
        if (hit(mx, my, x + MARGIN, exportY(), width - MARGIN * 2, 32))
        {
            if (onExport != null)
            {
                onExport.run();
            }
            return;
        }

        // Entrances toggle
        if (hit(mx, my, x + MARGIN, entrancesY(), width - MARGIN * 2, 32))
        {
            toggleEntrancesMode();
        }
    }

    @Override
    public void update(InputManager input)
    {
        // Used via updatePanel so we can pass dt for status fade
        updatePanel(input, 0f);
    }

    @Override
    public void render(Renderer renderer)
    {
        renderer.drawRect(x, y, width, height, new Color(18, 22, 30, 235), true, 920);
        renderer.drawRect(x, y, width, height, new Color(80, 200, 160), false, 921);
        renderer.drawText("Room Settings", x + MARGIN, y + 22, titleFont, new Color(230, 230, 236), 922);
        renderer.drawText("Name", x + MARGIN, y + 42, bodyFont, new Color(150, 158, 175), 923);

        nameField.render(renderer);

        // Dimensions
        renderer.drawText("Width", x + MARGIN, dimRowY() + 18, bodyFont, new Color(180, 190, 205), 924);
        drawStepper(renderer, widthBtnX(false), dimRowY(), "-", roomWidth, widthBtnX(true));

        renderer.drawText("Height", x + MARGIN, dimRowY() + 52, bodyFont, new Color(180, 190, 205), 925);
        drawStepper(renderer, widthBtnX(false), dimRowY() + 34, "-", roomHeight, widthBtnX(true));

        // Export
        renderer.drawRect(x + MARGIN, exportY(), width - MARGIN * 2, 32, new Color(40, 90, 70), true, 926);
        renderer.drawRect(x + MARGIN, exportY(), width - MARGIN * 2, 32, new Color(80, 200, 160), false, 927);
        renderer.drawText("Export Room", x + MARGIN + 10, exportY() + 21, bodyFont, new Color(230, 240, 235), 928);

        // Entrances
        Color entBg = entrancesMode ? new Color(40, 110, 140) : new Color(40, 46, 58);
        Color entBorder = entrancesMode ? new Color(90, 210, 240) : new Color(91, 140, 255);
        renderer.drawRect(x + MARGIN, entrancesY(), width - MARGIN * 2, 32, entBg, true, 929);
        renderer.drawRect(x + MARGIN, entrancesY(), width - MARGIN * 2, 32, entBorder, false, 930);
        renderer.drawText(entrancesMode ? "Entrances: ON" : "Entrances: OFF",
                x + MARGIN + 10, entrancesY() + 21, bodyFont, new Color(230, 230, 236), 931);

        if (!statusMessage.isEmpty())
        {
            renderer.drawText(statusMessage, x + MARGIN, y + height - 10, bodyFont, new Color(140, 220, 180), 932);
        }
    }

    private void drawStepper(Renderer renderer, float minusX, float rowY, String ignored, int value, float plusX)
    {
        drawSmallBtn(renderer, minusX, rowY, "-");
        renderer.drawText(String.valueOf(value), minusX + 40, rowY + 18, bodyFont, new Color(230, 230, 236), 933);
        drawSmallBtn(renderer, plusX, rowY, "+");
    }

    private void drawSmallBtn(Renderer renderer, float bx, float by, String label)
    {
        renderer.drawRect(bx, by, 28, ROW_H, new Color(40, 46, 58), true, 934);
        renderer.drawRect(bx, by, 28, ROW_H, new Color(91, 140, 255), false, 935);
        renderer.drawText(label, bx + 9, by + 18, bodyFont, new Color(230, 230, 236), 936);
    }

    private float dimRowY()
    {
        return y + 88;
    }

    private float exportY()
    {
        return y + 168;
    }

    private float entrancesY()
    {
        return y + 208;
    }

    private float widthBtnX(boolean plus)
    {
        return plus ? x + width - MARGIN - 28 : x + 70;
    }

    private void changeWidth(int delta)
    {
        int next = Math.max(4, Math.min(128, roomWidth + delta));
        if (next == roomWidth)
        {
            return;
        }
        roomWidth = next;
        if (onWidthChanged != null)
        {
            onWidthChanged.accept(roomWidth);
        }
    }

    private void changeHeight(int delta)
    {
        int next = Math.max(4, Math.min(128, roomHeight + delta));
        if (next == roomHeight)
        {
            return;
        }
        roomHeight = next;
        if (onHeightChanged != null)
        {
            onHeightChanged.accept(roomHeight);
        }
    }

    private static boolean hit(float mx, float my, float bx, float by, float bw, float bh)
    {
        return mx >= bx && my >= by && mx < bx + bw && my < by + bh;
    }
}
