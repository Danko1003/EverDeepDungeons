package com.varcore.game.RoomMaker;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.nio.file.Path;

import com.varcore.engine.input.InputManager;
import com.varcore.engine.render.Renderer;
import com.varcore.game.CamGame;
import com.varcore.game.Inputs;
import com.varcore.game.TilesAndGrids.GridManager;
import com.varcore.game.TilesAndGrids.GridRenderer;
import com.varcore.game.TilesAndGrids.RoomSerializer;
import com.varcore.game.TilesAndGrids.StructureRegistry;
import com.varcore.game.TilesAndGrids.TileRegistry;
import com.varcore.game.TilesAndGrids.TileTextureRegistry;

/**
 * In-game room editor with tile palette, room settings (name / size / export),
 * and an entrances overlay for later dungeon stitching.
 */
public class RoomMaker
{
    private static final int TILE_SIZE = 32;
    private static final int MIN_MAP_W = 8;
    private static final int MIN_MAP_H = 8;
    private static final float PANEL_W = 230f;
    private static final float PANEL_MARGIN = 8f;

    private final GridRenderer gridRenderer;
    private final GridManager grid;
    private final StructureRegistry structures;
    private final CamGame camera;
    private final Inputs inputs;
    private final TilePalettePanel palette;
    private final RoomSettingsPanel settings;
    private final EditorButton clearButton;
    private final Font hintFont = new Font("Segoe UI", Font.PLAIN, 12);

    private int hoverGX = -1;
    private int hoverGY = -1;

    // Shift + drag rectangle fill
    private boolean fillDragging;
    private int fillButton;
    private int fillStartGX = -1;
    private int fillStartGY = -1;
    private int fillEndGX = -1;
    private int fillEndGY = -1;
    private int fillTileId = TileRegistry.EMPTY_TILE_ID;
    private boolean fillEntrances;
    private boolean fillEntranceValue;

    public RoomMaker(TileRegistry tileRegistry, TileTextureRegistry textureRegistry, int screenW, int screenH)
    {
        this.structures = new StructureRegistry();
        this.gridRenderer = new GridRenderer(tileRegistry, textureRegistry, structures);
        this.camera = new CamGame(0, 0, 1);
        this.inputs = new Inputs();

        int mapW = computeMapWidth(screenW);
        int mapH = computeMapHeight(screenH);
        this.grid = new GridManager(mapW, mapH, TILE_SIZE, 0, 0, TileRegistry.EMPTY_TILE_ID);

        settings = new RoomSettingsPanel(PANEL_MARGIN, PANEL_MARGIN, mapW, mapH);
        settings.setOnExport(this::exportRoom);
        settings.setOnWidthChanged(w -> grid.resize(w, grid.getHeight()));
        settings.setOnHeightChanged(h -> grid.resize(grid.getWidth(), h));

        palette = new TilePalettePanel(PANEL_MARGIN, PANEL_MARGIN, PANEL_W, 520f, textureRegistry);
        palette.setTiles(structures.filterPlaceableTiles(tileRegistry.getPlaceableTiles()));

        clearButton = new EditorButton(PANEL_MARGIN, 536f, PANEL_W, 36f);
        clearButton.setText("Clear Room");
        clearButton.setOnClick(b -> clearRoom());
    }

    public CamGame getCamera()
    {
        return camera;
    }

    public GridManager getGrid()
    {
        return grid;
    }

    public void update(InputManager input, float dt, int screenW, int screenH)
    {
        camera.setViewportSize(screenW, screenH);

        settings.setBounds(PANEL_MARGIN, PANEL_MARGIN);
        settings.syncDimensions(grid.getWidth(), grid.getHeight());
        settings.updatePanel(input, dt);

        float panelH = Math.max(280f, screenH - 88f);
        float panelX = screenW - PANEL_W - PANEL_MARGIN;
        palette.setBounds(panelX, PANEL_MARGIN, PANEL_W, panelH);

        float clearY = PANEL_MARGIN + panelH + 8f;
        clearButton.placeTopRight(screenW, PANEL_MARGIN, clearY, PANEL_W, 36f);

        // Name field + palette / clear handle their own input; avoid double layout damage
        // by only updating palette/clear through UIRenderer (settings already updated).
        palette.update(input);
        clearButton.update(input);

        boolean typing = settings.getNameField().isFocused();
        boolean overUi = settings.containsPoint(input.getMouseX(), input.getMouseY())
                || palette.isHovered()
                || clearButton.contains(input.getMouseX(), input.getMouseY());

        if (fillDragging)
        {
            updateFillDrag(input);
        }
        else if (!overUi && !typing)
        {
            inputs.camPan(input, camera);
            camera.zoomByWheel(input.getMouseWheelDelta());
            updateHoverAndPaint(input);
        }
        else
        {
            hoverGX = -1;
            hoverGY = -1;
            if (!overUi && !typing)
            {
                camera.zoomByWheel(input.getMouseWheelDelta());
            }
        }
    }

    public void render(Renderer renderer)
    {
        renderer.drawRect(-2000, -2000, 8000, 8000, new Color(12, 14, 20), true, -1);

        gridRenderer.render(renderer, grid, camera, 0);
        drawEntrancesOverlay(renderer);
        drawFillPreview(renderer);
        drawHoverGhost(renderer);

        String mode = settings.isEntrancesMode() ? "ENTRANCES" : "TILES";
        renderer.drawTextScreen(
                "WASD pan · Scroll zoom · LMB paint · RMB erase · Shift+drag fill · Mode "
                        + mode + " · Room " + grid.getWidth() + "x" + grid.getHeight(),
                12, 18, hintFont, new Color(180, 190, 210));

        settings.render(renderer);
        palette.render(renderer);
        clearButton.render(renderer);
    }

    private void exportRoom()
    {
        String name = settings.getRoomName();
        Path path = RoomSerializer.defaultExportPath(name);
        boolean ok = RoomSerializer.saveRoom(grid, name, path);
        if (ok)
        {
            settings.setStatus("Saved: " + path);
        }
        else
        {
            settings.setStatus("Export failed");
        }
    }

    private static int computeMapWidth(int screenW)
    {
        int usable = screenW - Math.round(PANEL_W + PANEL_MARGIN * 2 + RoomSettingsPanel.PANEL_W + 24f);
        return Math.max(MIN_MAP_W, usable / TILE_SIZE);
    }

    private static int computeMapHeight(int screenH)
    {
        int usable = screenH - 24;
        return Math.max(MIN_MAP_H, usable / TILE_SIZE);
    }

    private void updateHoverAndPaint(InputManager input)
    {
        int[] cell = mouseToCell(input.getMouseX(), input.getMouseY());
        if (cell == null)
        {
            hoverGX = -1;
            hoverGY = -1;
            return;
        }

        int gx = cell[0];
        int gy = cell[1];
        hoverGX = gx;
        hoverGY = gy;

        boolean shift = input.isKeyDown(java.awt.event.KeyEvent.VK_SHIFT);
        boolean entrances = settings.isEntrancesMode();

        if (shift && input.isMousePressed(MouseEvent.BUTTON1))
        {
            if (entrances)
            {
                beginEntranceFill(gx, gy, MouseEvent.BUTTON1, true);
            }
            else
            {
                beginFill(gx, gy, MouseEvent.BUTTON1, palette.getSelectedTileId());
            }
            return;
        }
        if (shift && input.isMousePressed(MouseEvent.BUTTON3))
        {
            if (entrances)
            {
                beginEntranceFill(gx, gy, MouseEvent.BUTTON3, false);
            }
            else
            {
                beginFill(gx, gy, MouseEvent.BUTTON3, TileRegistry.EMPTY_TILE_ID);
            }
            return;
        }

        if (!shift && input.isMouseDown(MouseEvent.BUTTON1))
        {
            if (entrances)
            {
                grid.setEntrance(gx, gy, true);
            }
            else
            {
                paintBrush(gx, gy, palette.getSelectedTileId());
            }
        }
        else if (!shift && input.isMouseDown(MouseEvent.BUTTON3))
        {
            if (entrances)
            {
                grid.setEntrance(gx, gy, false);
            }
            else
            {
                eraseAt(gx, gy);
            }
        }
    }

    private void paintBrush(int gx, int gy, int tileId)
    {
        if (structures.isStructureBrush(tileId))
        {
            structures.place(grid, tileId, gx, gy);
        }
        else if (tileId == TileRegistry.EMPTY_TILE_ID)
        {
            eraseAt(gx, gy);
        }
        else
        {
            // Floors / ground paint the base layer; structure overlays stay above
            grid.setTileId(gx, gy, tileId);
        }
    }

    /** Erase whole structure if present, otherwise clear base tile. */
    private void eraseAt(int gx, int gy)
    {
        if (!structures.erase(grid, gx, gy))
        {
            grid.clearBaseTile(gx, gy);
        }
    }

    private void updateFillDrag(InputManager input)
    {
        int[] cell = mouseToCell(input.getMouseX(), input.getMouseY());
        if (cell != null)
        {
            hoverGX = cell[0];
            hoverGY = cell[1];
            fillEndGX = cell[0];
            fillEndGY = cell[1];
        }

        if (!input.isMouseDown(fillButton))
        {
            applyFill();
            clearFillState();
        }
    }

    private void beginFill(int gx, int gy, int button, int tileId)
    {
        fillDragging = true;
        fillEntrances = false;
        fillButton = button;
        fillTileId = tileId;
        fillStartGX = gx;
        fillStartGY = gy;
        fillEndGX = gx;
        fillEndGY = gy;
    }

    private void beginEntranceFill(int gx, int gy, int button, boolean value)
    {
        fillDragging = true;
        fillEntrances = true;
        fillEntranceValue = value;
        fillButton = button;
        fillStartGX = gx;
        fillStartGY = gy;
        fillEndGX = gx;
        fillEndGY = gy;
    }

    private void applyFill()
    {
        if (fillStartGX < 0 || fillStartGY < 0 || fillEndGX < 0 || fillEndGY < 0)
        {
            return;
        }

        int x0 = Math.min(fillStartGX, fillEndGX);
        int x1 = Math.max(fillStartGX, fillEndGX);
        int y0 = Math.min(fillStartGY, fillEndGY);
        int y1 = Math.max(fillStartGY, fillEndGY);

        for (int y = y0; y <= y1; y++)
        {
            for (int x = x0; x <= x1; x++)
            {
                if (fillEntrances)
                {
                    grid.setEntrance(x, y, fillEntranceValue);
                }
                else if (structures.isStructureBrush(fillTileId))
                {
                    // Foot-only walls: every selected cell gets a wall foot so
                    // Shift+drag can fill rows behind existing walls.
                    structures.place(grid, fillTileId, x, y);
                }
                else if (fillTileId == TileRegistry.EMPTY_TILE_ID)
                {
                    eraseAt(x, y);
                }
                else
                {
                    grid.setTileId(x, y, fillTileId);
                }
            }
        }
    }

    private void clearFillState()
    {
        fillDragging = false;
        fillEntrances = false;
        fillButton = 0;
        fillStartGX = -1;
        fillStartGY = -1;
        fillEndGX = -1;
        fillEndGY = -1;
    }

    private int[] mouseToCell(int mx, int my)
    {
        float zoom = camera.getZoom();
        if (zoom <= 0f)
        {
            return null;
        }

        float worldX = mx / zoom + camera.getCamX();
        float worldY = my / zoom + camera.getCamY();

        int gx = (int) Math.floor((worldX - grid.getWorldX()) / (float) grid.getTilesize());
        int gy = (int) Math.floor((worldY - grid.getWorldY()) / (float) grid.getTilesize());

        if (gx < 0 || gy < 0 || gx >= grid.getWidth() || gy >= grid.getHeight())
        {
            return null;
        }

        return new int[] { gx, gy };
    }

    private void drawEntrancesOverlay(Renderer renderer)
    {
        float zoom = camera.getZoom();
        int tileSize = grid.getTilesize();

        for (int y = 0; y < grid.getHeight(); y++)
        {
            for (int x = 0; x < grid.getWidth(); x++)
            {
                if (!grid.isEntrance(x, y))
                {
                    continue;
                }

                float worldLeft = grid.getWorldX() + x * tileSize;
                float worldTop = grid.getWorldY() + y * tileSize;
                int drawX = Math.round((worldLeft - camera.getCamX()) * zoom);
                int drawY = Math.round((worldTop - camera.getCamY()) * zoom);
                int drawW = Math.round((worldLeft + tileSize - camera.getCamX()) * zoom) - drawX;
                int drawH = Math.round((worldTop + tileSize - camera.getCamY()) * zoom) - drawY;

                renderer.drawRect(drawX, drawY, drawW, drawH, new Color(40, 200, 230, 70), true, 40);
                renderer.drawRect(drawX + 3, drawY + 3, Math.max(1, drawW - 6), Math.max(1, drawH - 6),
                        new Color(90, 230, 255, 220), false, 41);
            }
        }
    }

    private void drawFillPreview(Renderer renderer)
    {
        if (!fillDragging || fillStartGX < 0 || fillEndGX < 0)
        {
            return;
        }

        int x0 = Math.min(fillStartGX, fillEndGX);
        int x1 = Math.max(fillStartGX, fillEndGX);
        int y0 = Math.min(fillStartGY, fillEndGY);
        int y1 = Math.max(fillStartGY, fillEndGY);

        float zoom = camera.getZoom();
        int tileSize = grid.getTilesize();
        float worldLeft = grid.getWorldX() + x0 * tileSize;
        float worldTop = grid.getWorldY() + y0 * tileSize;
        float worldRight = grid.getWorldX() + (x1 + 1) * tileSize;
        float worldBottom = grid.getWorldY() + (y1 + 1) * tileSize;

        int drawX = Math.round((worldLeft - camera.getCamX()) * zoom);
        int drawY = Math.round((worldTop - camera.getCamY()) * zoom);
        int drawW = Math.round((worldRight - camera.getCamX()) * zoom) - drawX;
        int drawH = Math.round((worldBottom - camera.getCamY()) * zoom) - drawY;

        Color tint;
        Color border;
        if (fillEntrances)
        {
            tint = fillEntranceValue ? new Color(40, 200, 230, 70) : new Color(220, 80, 80, 70);
            border = fillEntranceValue ? new Color(120, 230, 255, 220) : new Color(255, 140, 140, 220);
        }
        else
        {
            boolean erasing = fillTileId == TileRegistry.EMPTY_TILE_ID;
            tint = erasing ? new Color(220, 80, 80, 70) : new Color(91, 140, 255, 70);
            border = erasing ? new Color(255, 140, 140, 220) : new Color(160, 200, 255, 220);
        }
        renderer.drawRect(drawX, drawY, drawW, drawH, tint, true, 48);
        renderer.drawRect(drawX, drawY, drawW, drawH, border, false, 49);
    }

    private void drawHoverGhost(Renderer renderer)
    {
        if (fillDragging || hoverGX < 0 || hoverGY < 0)
        {
            return;
        }

        float zoom = camera.getZoom();
        int tileSize = grid.getTilesize();

        boolean structureBrush = !settings.isEntrancesMode()
                && structures.isStructureBrush(palette.getSelectedTileId())
                && !palette.isEraserMode();

        int hoverH = structureBrush ? structures.getHoverHeight(palette.getSelectedTileId()) : 1;
        int topGy = structureBrush ? Math.max(0, hoverGY - (hoverH - 1)) : hoverGY;

        float worldLeft = grid.getWorldX() + hoverGX * tileSize;
        float worldTop = grid.getWorldY() + topGy * tileSize;
        float worldBottom = grid.getWorldY() + (hoverGY + 1) * tileSize;

        int drawX = Math.round((worldLeft - camera.getCamX()) * zoom);
        int drawY = Math.round((worldTop - camera.getCamY()) * zoom);
        int drawW = Math.round((worldLeft + tileSize - camera.getCamX()) * zoom) - drawX;
        int drawH = Math.round((worldBottom - camera.getCamY()) * zoom) - drawY;

        Color tint;
        if (settings.isEntrancesMode())
        {
            tint = new Color(40, 200, 230, 90);
        }
        else if (structureBrush)
        {
            tint = new Color(180, 160, 100, 90);
        }
        else
        {
            tint = palette.isEraserMode()
                    ? new Color(220, 80, 80, 90)
                    : new Color(91, 140, 255, 80);
        }
        renderer.drawRect(drawX, drawY, drawW, drawH, tint, true, 50);
        renderer.drawRect(drawX, drawY, drawW, drawH, new Color(220, 230, 255, 200), false, 51);
    }

    private void clearRoom()
    {
        grid.fill(TileRegistry.EMPTY_TILE_ID);
        grid.clearOverlays();
        grid.clearEntrances();
    }
}
