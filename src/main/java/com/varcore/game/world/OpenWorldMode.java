package com.varcore.game.world;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;

import com.varcore.engine.input.InputManager;
import com.varcore.engine.render.Renderer;
import com.varcore.game.CamGame;
import com.varcore.game.Inputs;
import com.varcore.game.TilesAndGrids.CellVisualCache;
import com.varcore.game.TilesAndGrids.GridManager;
import com.varcore.game.TilesAndGrids.GridRenderer;
import com.varcore.game.TilesAndGrids.StructureRegistry;
import com.varcore.game.TilesAndGrids.TileMaterialRegistry;
import com.varcore.game.TilesAndGrids.TileRegistry;
import com.varcore.game.TilesAndGrids.TileTextureRegistry;
import com.varcore.game.TilesAndGrids.TransitionResolver;

/**
 * Chunk-backed open world: generates terrain around the camera and stamps a
 * sliding render window into {@link GridManager} for the existing renderer.
 */
public final class OpenWorldMode
{
    private static final int TILE_SIZE = 32;
    private static final int MAX_CACHED_CHUNKS = 96;
    /** Extra tiles around the viewport so pans don't immediately refill. */
    private static final int VIEW_PAD_TILES = 20;
    /** Recenter the render window when the camera leaves this inner margin. */
    private static final int RECENTER_MARGIN = 14;
    /** Projection prewarm cells per update (keeps render/input smooth). */
    private static final int PREWARM_BUILDS_PER_FRAME = 24;
    private static final int PREFETCH_CHUNKS_PER_FRAME = 2;
    private static final float PROJECTION_MIN_ZOOM = 0.60f;

    private final StructureRegistry structures;
    private final GridRenderer gridRenderer;
    private final CellVisualCache visualCache;
    private final GridManager grid;
    private final CamGame camera;
    private final Inputs inputs;
    private final OpenWorldGenerator generator;
    private final Map<ChunkCoord, WorldChunk> chunkCache;

    private final Font hudFont = new Font("Segoe UI", Font.PLAIN, 13);

    private int windowOriginTX;
    private int windowOriginTY;
    private boolean needsFullSync = true;
    private int prefetchCursor;
    private float animTime;

    public OpenWorldMode(TileRegistry tiles, TileTextureRegistry textures)
    {
        structures = new StructureRegistry();
        TileMaterialRegistry materials = new TileMaterialRegistry(textures);
        TransitionResolver resolver = new TransitionResolver(materials, TILE_SIZE);
        visualCache = new CellVisualCache(resolver, 64, 64);
        gridRenderer = new GridRenderer(tiles, textures, structures, materials, visualCache);
        camera = new CamGame(0, 0, 1f);
        inputs = new Inputs();
        generator = new OpenWorldGenerator(0xE7E7D);
        chunkCache = new LinkedHashMap<>(64, 0.75f, true)
        {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ChunkCoord, WorldChunk> eldest)
            {
                return size() > MAX_CACHED_CHUNKS;
            }
        };

        grid = new GridManager(64, 64, TILE_SIZE, 0, 0, TileRegistry.EMPTY_TILE_ID);
        grid.setVisualCache(visualCache);
        windowOriginTX = 0;
        windowOriginTY = 0;
    }

    public CamGame getCamera()
    {
        return camera;
    }

    public void onEnter()
    {
        camera.setCamX(-camera.getViewportWidth() * 0.5f / Math.max(0.01f, camera.getZoom()));
        camera.setCamY(-camera.getViewportHeight() * 0.5f / Math.max(0.01f, camera.getZoom()));
        needsFullSync = true;
    }

    public void update(InputManager input, float dt, int screenW, int screenH)
    {
        camera.setViewportSize(screenW, screenH);
        animTime += dt;
        gridRenderer.setAnimTime(animTime);
        inputs.camInputs(input, camera, dt);

        if (input.isKeyPressed(KeyEvent.VK_R))
        {
            regenerate();
        }

        ensureWindowSize(screenW, screenH);
        maybeRecenterWindow();
        if (needsFullSync)
        {
            stampWindowFromChunks();
            needsFullSync = false;
        }

        prefetchNearbyChunks();
        prewarmVisibleProjections();
    }

    public void render(Renderer renderer)
    {
        gridRenderer.render(renderer, grid, camera, 10);

        int tileX = (int) Math.floor(camera.getCamX() / TILE_SIZE);
        int tileY = (int) Math.floor(camera.getCamY() / TILE_SIZE);
        ChunkCoord cc = ChunkCoord.fromWorldTile(tileX, tileY, OpenWorldGenerator.CHUNK_SIZE);

        String zoomText = String.format("%.2f", camera.getZoom());
        String line1 = "Open World  |  zoom " + zoomText
                + "  |  seed " + generator.getSeed()
                + "  |  chunk " + cc
                + "  |  cached " + chunkCache.size();
        String line2 = "WASD pan   scroll zoom   R regenerate   ESC menu";

        renderer.drawRect(8, 8, 540, 44, new Color(10, 14, 20, 180), true, 800);
        renderer.drawTextScreen(line1, 16, 26, hudFont, new Color(210, 225, 215));
        renderer.drawTextScreen(line2, 16, 44, hudFont, new Color(140, 160, 150));
    }

    private void regenerate()
    {
        generator.setSeed(generator.getSeed() + 0x9E3779B9);
        chunkCache.clear();
        needsFullSync = true;
    }

    private void ensureWindowSize(int screenW, int screenH)
    {
        // Allocate for the current view plus zoom headroom, not the maximum
        // far-zoom world on the first frame.
        float sizingZoom = Math.max(0.18f, camera.getZoom() * 0.78f);
        int pad = VIEW_PAD_TILES;
        int needW = (int) Math.ceil(screenW / (TILE_SIZE * sizingZoom)) + pad * 2;
        int needH = (int) Math.ceil(screenH / (TILE_SIZE * sizingZoom)) + pad * 2;
        // Keep the far-zoom window large enough to cover fullscreen displays.
        // At low zoom we draw cheap average-color rects, so a bigger tile window
        // is acceptable and prevents black margins past the generated area.
        needW = Math.max(48, Math.min(420, needW));
        needH = Math.max(36, Math.min(260, needH));

        // Grow extreme zoom changes over a few frames and never shrink during
        // play, avoiding both a single allocation spike and resize thrashing.
        needW = Math.max(grid.getWidth(), Math.min(needW, grid.getWidth() + 56));
        needH = Math.max(grid.getHeight(), Math.min(needH, grid.getHeight() + 40));

        if (needW == grid.getWidth() && needH == grid.getHeight())
        {
            return;
        }

        int oldW = grid.getWidth();
        int oldH = grid.getHeight();
        grid.resize(needW, needH);

        if (needW > oldW || needH > oldH)
        {
            // Stamp only newly added bands; keep cached projections for the rest.
            stampGrownBands(oldW, oldH);
        }
        // Shrinking keeps the overlap; no full sync / markAllDirty.
    }

    private void stampGrownBands(int oldW, int oldH)
    {
        for (int gy = 0; gy < grid.getHeight(); gy++)
        {
            for (int gx = 0; gx < grid.getWidth(); gx++)
            {
                if (gx < oldW && gy < oldH)
                {
                    continue;
                }
                stampCell(gx, gy, windowOriginTX + gx, windowOriginTY + gy, true);
            }
        }
    }

    private void maybeRecenterWindow()
    {
        float zoom = Math.max(0.01f, camera.getZoom());
        float viewLeft = camera.getCamX();
        float viewTop = camera.getCamY();
        float viewRight = viewLeft + camera.getViewportWidth() / zoom;
        float viewBottom = viewTop + camera.getViewportHeight() / zoom;

        int winLeft = windowOriginTX * TILE_SIZE;
        int winTop = windowOriginTY * TILE_SIZE;
        int winRight = winLeft + grid.getWidth() * TILE_SIZE;
        int winBottom = winTop + grid.getHeight() * TILE_SIZE;
        int marginPx = RECENTER_MARGIN * TILE_SIZE;

        boolean nearEdge =
                viewLeft < winLeft + marginPx
                        || viewTop < winTop + marginPx
                        || viewRight > winRight - marginPx
                        || viewBottom > winBottom - marginPx;

        if (!nearEdge && !needsFullSync)
        {
            return;
        }

        int centerTX = (int) Math.floor((viewLeft + viewRight) * 0.5f / TILE_SIZE);
        int centerTY = (int) Math.floor((viewTop + viewBottom) * 0.5f / TILE_SIZE);
        int newOx = centerTX - grid.getWidth() / 2;
        int newOy = centerTY - grid.getHeight() / 2;

        if (newOx != windowOriginTX || newOy != windowOriginTY || needsFullSync)
        {
            int oldOx = windowOriginTX;
            int oldOy = windowOriginTY;

            if (!needsFullSync)
            {
                int shiftX = oldOx - newOx;
                int shiftY = oldOy - newOy;
                grid.shiftWindow(
                        shiftX,
                        shiftY,
                        newOx * TILE_SIZE,
                        newOy * TILE_SIZE);
            }

            windowOriginTX = newOx;
            windowOriginTY = newOy;
            if (needsFullSync)
            {
                grid.setWorldX(windowOriginTX * TILE_SIZE);
                grid.setWorldY(windowOriginTY * TILE_SIZE);
            }
            else
            {
                stampNewlyExposedCells(oldOx, oldOy);
            }
        }
    }

    private WorldChunk getOrCreateChunk(ChunkCoord coord)
    {
        WorldChunk existing = chunkCache.get(coord);
        if (existing != null)
        {
            return existing;
        }
        WorldChunk created = generator.generate(coord);
        chunkCache.put(coord, created);
        return created;
    }

    /** Generate a couple of not-yet-cached chunks near the window each frame. */
    private void prefetchNearbyChunks()
    {
        int pad = OpenWorldGenerator.CHUNK_SIZE;
        int x0 = windowOriginTX - pad;
        int y0 = windowOriginTY - pad;
        int x1 = windowOriginTX + grid.getWidth() + pad;
        int y1 = windowOriginTY + grid.getHeight() + pad;

        ChunkCoord c0 = ChunkCoord.fromWorldTile(x0, y0, OpenWorldGenerator.CHUNK_SIZE);
        ChunkCoord c1 = ChunkCoord.fromWorldTile(x1, y1, OpenWorldGenerator.CHUNK_SIZE);

        int generated = 0;
        int spanX = c1.cx - c0.cx + 1;
        int spanY = c1.cy - c0.cy + 1;
        int total = Math.max(1, spanX * spanY);

        for (int i = 0; i < total && generated < PREFETCH_CHUNKS_PER_FRAME; i++)
        {
            int idx = Math.floorMod(prefetchCursor + i, total);
            int cx = c0.cx + (idx % spanX);
            int cy = c0.cy + (idx / spanX);
            ChunkCoord coord = new ChunkCoord(cx, cy);
            if (!chunkCache.containsKey(coord))
            {
                getOrCreateChunk(coord);
                generated++;
            }
        }
        prefetchCursor = Math.floorMod(prefetchCursor + PREFETCH_CHUNKS_PER_FRAME, total);
    }

    /**
     * Builds projected materials for the visible (+pad) area over several frames
     * so render never has to do a huge sync rebuild while moving.
     */
    private void prewarmVisibleProjections()
    {
        if (camera.getZoom() < PROJECTION_MIN_ZOOM)
        {
            return;
        }

        float zoom = Math.max(0.01f, camera.getZoom());
        float viewLeft = camera.getCamX();
        float viewTop = camera.getCamY();
        float viewRight = viewLeft + camera.getViewportWidth() / zoom;
        float viewBottom = viewTop + camera.getViewportHeight() / zoom;

        int pad = 4;
        int minX = (int) Math.floor((viewLeft - grid.getWorldX()) / TILE_SIZE) - pad;
        int minY = (int) Math.floor((viewTop - grid.getWorldY()) / TILE_SIZE) - pad;
        int maxX = (int) Math.floor((viewRight - grid.getWorldX()) / TILE_SIZE) + pad;
        int maxY = (int) Math.floor((viewBottom - grid.getWorldY()) / TILE_SIZE) + pad;

        visualCache.buildDirtyInBounds(
                grid, minX, minY, maxX, maxY, PREWARM_BUILDS_PER_FRAME);
    }

    private void stampWindowFromChunks()
    {
        int w = grid.getWidth();
        int h = grid.getHeight();
        for (int gy = 0; gy < h; gy++)
        {
            for (int gx = 0; gx < w; gx++)
            {
                stampCell(gx, gy, windowOriginTX + gx, windowOriginTY + gy, false);
            }
        }
        visualCache.markAllDirty();
    }

    /** Populates only strips that were outside the previous world window. */
    private void stampNewlyExposedCells(int oldOx, int oldOy)
    {
        int oldRight = oldOx + grid.getWidth();
        int oldBottom = oldOy + grid.getHeight();
        for (int gy = 0; gy < grid.getHeight(); gy++)
        {
            int wy = windowOriginTY + gy;
            for (int gx = 0; gx < grid.getWidth(); gx++)
            {
                int wx = windowOriginTX + gx;
                boolean wasInOldWindow =
                        wx >= oldOx && wx < oldRight
                                && wy >= oldOy && wy < oldBottom;
                if (!wasInOldWindow)
                {
                    stampCell(gx, gy, wx, wy, true);
                }
            }
        }
    }

    private void stampCell(int gx, int gy, int wx, int wy, boolean markDirty)
    {
        ChunkCoord cc = ChunkCoord.fromWorldTile(wx, wy, OpenWorldGenerator.CHUNK_SIZE);
        WorldChunk chunk = getOrCreateChunk(cc);
        int lx = wx - chunk.worldOriginX();
        int ly = wy - chunk.worldOriginY();

        // Silent write avoids dirtying the whole neighborhood on every stamp.
        grid.setTileIdSilent(gx, gy, chunk.getFloor(lx, ly));
        grid.setOverlayTileId(gx, gy, 0);
        grid.setEntrance(gx, gy, chunk.isEntrance(lx, ly));

        if (chunk.getOverlay(lx, ly) == TileRegistry.WALL_FACE_ID)
        {
            structures.place(grid, TileRegistry.WALL_FACE_ID, gx, gy);
        }

        if (markDirty)
        {
            // Only the new cell + seam neighbors need projection rebuild.
            visualCache.markDirty3x3(gx, gy);
        }
    }
}
