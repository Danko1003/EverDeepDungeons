package com.varcore.game.RoomMaker;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import com.varcore.engine.input.InputManager;
import com.varcore.engine.render.Renderer;
import com.varcore.engine.ui.UIAnchor;
import com.varcore.engine.ui.UIElement;
import com.varcore.game.TilesAndGrids.Tile;
import com.varcore.game.TilesAndGrids.TileTextureRegistry;

/**
 * Right-side scrollable tile palette built from the tile registry.
 * Uses VarCore {@link UIElement} layout / hit-testing.
 */
public class TilePalettePanel extends UIElement
{
    private static final float MARGIN = 10f;
    private static final float HEADER_H = 34f;
    private static final float ERASER_H = 36f;
    private static final float CELL_H = 56f;
    private static final float CELL_GAP = 6f;
    private static final float PREVIEW = 40f;
    private static final float SCROLL_STEP = 28f;

    private final TileTextureRegistry textures;
    private final List<Tile> tiles = new ArrayList<>();
    private final Font titleFont = new Font("Segoe UI", Font.BOLD, 15);
    private final Font bodyFont = new Font("Segoe UI", Font.PLAIN, 12);

    private float scrollY;
    private int selectedTileId = -1;
    private boolean eraserMode;
    private boolean hovered;

    public TilePalettePanel(float x, float y, float width, float height,
                            TileTextureRegistry textures)
    {
        super(x, y, width, height);
        this.textures = textures;
        setAnchor(UIAnchor.TOP_LEFT);
    }

    public void setBounds(float x, float y, float width, float height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    public float getPanelWidth()
    {
        return width;
    }

    public float getPanelHeight()
    {
        return height;
    }

    public float getPanelX()
    {
        return x;
    }

    public float getPanelY()
    {
        return y;
    }

    public void setTiles(List<Tile> placeableTiles)
    {
        tiles.clear();
        tiles.addAll(placeableTiles);
        if (selectedTileId < 0 && !tiles.isEmpty() && !eraserMode)
        {
            // Prefer first non-empty tile as the default brush
            selectedTileId = tiles.get(0).getId();
            for (Tile tile : tiles)
            {
                if (tile.getId() != com.varcore.game.TilesAndGrids.TileRegistry.EMPTY_TILE_ID)
                {
                    selectedTileId = tile.getId();
                    break;
                }
            }
        }
        clampScroll();
    }

    public boolean isEraserMode()
    {
        return eraserMode;
    }

    public int getSelectedTileId()
    {
        return eraserMode ? com.varcore.game.TilesAndGrids.TileRegistry.EMPTY_TILE_ID : selectedTileId;
    }

    public boolean isHovered()
    {
        return hovered;
    }

    public void setEraserMode(boolean eraserMode)
    {
        this.eraserMode = eraserMode;
    }

    public void toggleEraserMode()
    {
        setEraserMode(!eraserMode);
    }

    @Override
    public void update(InputManager input)
    {
        hovered = contains(input.getMouseX(), input.getMouseY());

        if (!hovered || !visible)
        {
            return;
        }

        int wheel = input.getMouseWheelDelta();
        if (wheel != 0)
        {
            scrollY += wheel * SCROLL_STEP;
            clampScroll();
        }

        if (input.isMousePressed(java.awt.event.MouseEvent.BUTTON1))
        {
            handleClick(input.getMouseX(), input.getMouseY());
        }
    }

    private void handleClick(float mx, float my)
    {
        float contentTop = y + HEADER_H + 6f;
        float eraserTop = contentTop;
        float eraserBottom = eraserTop + ERASER_H;

        if (my >= eraserTop && my < eraserBottom && mx >= x + MARGIN && mx < x + width - MARGIN)
        {
            toggleEraserMode();
            return;
        }

        float listTop = eraserBottom + 10f;
        float listBottom = y + height - MARGIN;
        if (my < listTop || my >= listBottom)
        {
            return;
        }

        float localY = (my - listTop) + scrollY;
        int index = (int) (localY / (CELL_H + CELL_GAP));
        if (index < 0 || index >= tiles.size())
        {
            return;
        }

        float cellTop = index * (CELL_H + CELL_GAP);
        if (localY < cellTop || localY >= cellTop + CELL_H)
        {
            return;
        }

        eraserMode = false;
        selectedTileId = tiles.get(index).getId();
    }

    @Override
    public void render(Renderer renderer)
    {
        renderer.drawRect(x, y, width, height, new Color(18, 22, 30, 235), true, 900);
        renderer.drawRect(x, y, width, height, new Color(91, 140, 255), false, 901);
        renderer.drawText("Room Maker", x + MARGIN, y + 22, titleFont, new Color(230, 230, 236), 902);

        float contentTop = y + HEADER_H + 6f;

        Color eraserBg = eraserMode ? new Color(180, 70, 70) : new Color(40, 46, 58);
        Color eraserBorder = eraserMode ? new Color(255, 120, 120) : new Color(91, 140, 255);
        renderer.drawRect(x + MARGIN, contentTop, width - MARGIN * 2, ERASER_H, eraserBg, true, 903);
        renderer.drawRect(x + MARGIN, contentTop, width - MARGIN * 2, ERASER_H, eraserBorder, false, 904);
        renderer.drawText(eraserMode ? "Eraser: ON" : "Eraser: OFF",
                x + MARGIN + 10, contentTop + ERASER_H / 2f + 5,
                bodyFont, new Color(230, 230, 236), 905);

        float listTop = contentTop + ERASER_H + 10f;
        float listHeight = height - (listTop - y) - MARGIN;

        for (int i = 0; i < tiles.size(); i++)
        {
            float cellY = listTop + i * (CELL_H + CELL_GAP) - scrollY;
            if (cellY + CELL_H < listTop || cellY > listTop + listHeight)
            {
                continue;
            }

            Tile tile = tiles.get(i);
            boolean selected = !eraserMode && tile.getId() == selectedTileId;

            Color cellBg = selected ? new Color(55, 80, 140) : new Color(34, 39, 51);
            Color cellBorder = selected ? new Color(120, 180, 255) : new Color(60, 68, 82);

            renderer.drawRect(x + MARGIN, cellY, width - MARGIN * 2, CELL_H, cellBg, true, 906);
            renderer.drawRect(x + MARGIN, cellY, width - MARGIN * 2, CELL_H, cellBorder, false, 907);

            BufferedImage img = textures.getTexture(tile.getTextureId());
            float previewX = x + MARGIN + 8;
            float previewY = cellY + (CELL_H - PREVIEW) / 2f;
            if (img != null)
            {
                renderer.drawSprite(img, previewX, previewY, PREVIEW, PREVIEW, 908);
            }
            renderer.drawRect(previewX, previewY, PREVIEW, PREVIEW, new Color(91, 140, 255), false, 909);

            renderer.drawText(tile.getName(), previewX + PREVIEW + 10, cellY + 22,
                    bodyFont, new Color(230, 230, 236), 910);
            renderer.drawText("ID " + tile.getId(), previewX + PREVIEW + 10, cellY + 40,
                    bodyFont, new Color(150, 158, 175), 911);
        }

        String footer = eraserMode ? "Placing: ERASER" : "Placing: ID " + selectedTileId;
        renderer.drawText(footer, x + MARGIN, y + height - 8, bodyFont, new Color(160, 170, 190), 912);
    }

    private float contentHeight()
    {
        if (tiles.isEmpty())
        {
            return 0f;
        }
        return tiles.size() * (CELL_H + CELL_GAP) - CELL_GAP;
    }

    private void clampScroll()
    {
        float listTop = HEADER_H + 6f + ERASER_H + 10f;
        float viewH = height - listTop - MARGIN;
        float maxScroll = Math.max(0f, contentHeight() - viewH);
        if (scrollY < 0f) scrollY = 0f;
        if (scrollY > maxScroll) scrollY = maxScroll;
    }
}
