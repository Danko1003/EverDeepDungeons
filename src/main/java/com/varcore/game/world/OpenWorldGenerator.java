package com.varcore.game.world;

import com.varcore.game.TilesAndGrids.TileRegistry;

/**
 * Deterministic chunk terrain + ruin generator.
 * Same seed + chunk coord always yields the same content.
 */
public final class OpenWorldGenerator
{
    public static final int CHUNK_SIZE = 32;

    private static final int G_LIGHT = 4;
    private static final int G_MED = 5;
    private static final int G_DARK = 6;
    private static final int G_DEEP = 7;
    private static final int STONE_A = 2;
    private static final int STONE_B = 3;
    private static final int DIRT_ONE = 11;
    private static final int DIRT_THREE = 13;
    private static final int DIRT_TWO = 14;
    private static final int DIRT_BURNT = 15;
    private static final int WALL = TileRegistry.WALL_FACE_ID;

    /** World-tile spacing for ruin placement candidates. */
    private static final int RUIN_REGION = 48;
    /** World-tile spacing for wandering dirt-path segments. */
    private static final int PATH_REGION = 36;
    /** World-tile spacing for scorched biome anchors. */
    private static final int BURNT_REGION = 64;

    private int seed;

    public OpenWorldGenerator(int seed)
    {
        this.seed = seed;
    }

    public int getSeed()
    {
        return seed;
    }

    public void setSeed(int seed)
    {
        this.seed = seed;
    }

    public WorldChunk generate(ChunkCoord coord)
    {
        WorldChunk chunk = new WorldChunk(coord, CHUNK_SIZE);
        int ox = chunk.worldOriginX();
        int oy = chunk.worldOriginY();

        for (int ly = 0; ly < CHUNK_SIZE; ly++)
        {
            for (int lx = 0; lx < CHUNK_SIZE; lx++)
            {
                chunk.setFloor(lx, ly, terrainTile(ox + lx, oy + ly));
            }
        }

        stampDirtPathsOverlapping(chunk);
        stampRuinsOverlapping(chunk);
        return chunk;
    }

    /** Floor tile at absolute world tile coordinates. */
    public int terrainTile(int wx, int wy)
    {
        float biome = fbm(wx * 0.012f, wy * 0.012f, seed ^ 0xB10BE);
        float detail = fbm(wx * 0.045f, wy * 0.045f, seed ^ 0xD37A11);
        float shade = fbm(wx * 0.028f, wy * 0.028f, seed ^ 0x67A55);
        float stoneN = fbm(wx * 0.02f, wy * 0.02f, seed ^ 0x5704E);
        float scorched = scorchedField(wx, wy);

        // Scorched / burnt biomes with grass islands (so burn borders are visible)
        if (scorched > 0.28f)
        {
            return scorchedTile(wx, wy, scorched, shade, detail);
        }

        // Stone-heavy pockets — mix both stone tiles
        if (stoneN > 0.58f && biome > 0.05f)
        {
            float mix = fbm(wx * 0.09f, wy * 0.09f, seed ^ 0x51A1);
            return mix > 0.1f ? STONE_A : STONE_B;
        }

        // Dirt core
        if (biome < -0.22f)
        {
            float strength = (-biome - 0.22f) / 0.78f + detail * 0.25f;
            if (strength > 0.55f)
            {
                return DIRT_THREE;
            }
            if (strength > 0.32f)
            {
                return DIRT_TWO;
            }
            return DIRT_ONE;
        }

        // Soft fringe: darkest grass + Dirt Three meet along the dirt boundary
        if (biome < -0.05f)
        {
            float edge = (-biome - 0.05f) / 0.17f;
            float speck = fbm(wx * 0.11f, wy * 0.11f, seed ^ 0xD17F);
            if (edge + speck * 0.35f > 0.55f)
            {
                return DIRT_THREE;
            }
            return G_DEEP;
        }

        return grassFromShade(shade, biome, detail);
    }

    /**
     * Continuous scorched intensity in roughly [0, 1+]. Peaks near occasional
     * biome anchors so burnt fields appear as coherent patches, not noise.
     */
    private float scorchedField(int wx, int wy)
    {
        float base = fbm(wx * 0.009f, wy * 0.009f, seed ^ 0x5C04);
        // Remap typical [-1,1] fbm into a rarer high band
        float field = (base + 0.15f) * 0.72f;

        int rx = floorDiv(wx, BURNT_REGION);
        int ry = floorDiv(wy, BURNT_REGION);
        // Check this region + neighbors so patches blend across region borders
        float boost = 0f;
        for (int oy = -1; oy <= 1; oy++)
        {
            for (int ox = -1; ox <= 1; ox++)
            {
                int arx = rx + ox;
                int ary = ry + oy;
                if (!shouldSpawnBurntBiome(arx, ary))
                {
                    continue;
                }
                int h = hash(arx, ary, seed, 0xB04E);
                int cx = arx * BURNT_REGION + 12 + Math.floorMod(h >>> 4, BURNT_REGION - 24);
                int cy = ary * BURNT_REGION + 12 + Math.floorMod(h >>> 10, BURNT_REGION - 24);
                float radius = 14f + Math.floorMod(h >>> 16, 18); // 14–31 tiles
                float dx = wx - cx;
                float dy = wy - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float falloff = 1f - dist / radius;
                if (falloff > 0f)
                {
                    // Soft dome — keeps interiors solid burnt
                    boost = Math.max(boost, falloff * falloff * (0.85f + (h & 7) * 0.02f));
                }
            }
        }
        return field + boost;
    }

    private boolean shouldSpawnBurntBiome(int regionX, int regionY)
    {
        int h = hash(regionX, regionY, seed, 0xB04E);
        return Math.floorMod(h, 100) < 22;
    }

    private int scorchedTile(int wx, int wy, float scorched, float shade, float detail)
    {
        // Grass islands / chunks inside the burn — large enough to show flaming borders
        float island = fbm(wx * 0.055f, wy * 0.055f, seed ^ 0x15A4D);
        float islandDetail = fbm(wx * 0.12f, wy * 0.12f, seed ^ 0x6485);
        float islandStrength = island * 0.75f + islandDetail * 0.25f;

        // Prefer islands away from the absolute hottest core so borders stay readable
        if (scorched < 0.92f && islandStrength > 0.38f)
        {
            // Soft island edge: thin burnt fringe between grass and deep scorched
            if (islandStrength < 0.48f && scorched > 0.4f)
            {
                return DIRT_BURNT;
            }
            return grassFromShade(shade, islandStrength, detail);
        }

        // Outer scorched fringe mixes ash dirt with burnt
        if (scorched < 0.38f)
        {
            return detail > 0.15f ? DIRT_BURNT : DIRT_ONE;
        }
        return DIRT_BURNT;
    }

    private static int grassFromShade(float shade, float biome, float detail)
    {
        float g = shade * 0.7f + biome * 0.25f + detail * 0.12f;
        if (g > 0.42f)
        {
            return G_DEEP;
        }
        if (g > 0.12f)
        {
            return G_DARK;
        }
        if (g > -0.18f)
        {
            return G_MED;
        }
        return G_LIGHT;
    }

    /**
     * Wandering Dirt Path One roads of varying width. Region-hashed so segments
     * stitch across chunk borders.
     */
    private void stampDirtPathsOverlapping(WorldChunk chunk)
    {
        int ox = chunk.worldOriginX();
        int oy = chunk.worldOriginY();
        int margin = 48;
        int x0 = ox - margin;
        int y0 = oy - margin;
        int x1 = ox + CHUNK_SIZE + margin;
        int y1 = oy + CHUNK_SIZE + margin;

        int rx0 = floorDiv(x0, PATH_REGION);
        int ry0 = floorDiv(y0, PATH_REGION);
        int rx1 = floorDiv(x1, PATH_REGION);
        int ry1 = floorDiv(y1, PATH_REGION);

        for (int ry = ry0; ry <= ry1; ry++)
        {
            for (int rx = rx0; rx <= rx1; rx++)
            {
                if (!shouldSpawnDirtPath(rx, ry))
                {
                    continue;
                }
                stampDirtPath(chunk, rx, ry);
            }
        }
    }

    private boolean shouldSpawnDirtPath(int regionX, int regionY)
    {
        int h = hash(regionX, regionY, seed, 0x0A71);
        return Math.floorMod(h, 100) < 38;
    }

    private void stampDirtPath(WorldChunk chunk, int regionX, int regionY)
    {
        int h = hash(regionX, regionY, seed, 0x0A71);
        int regionOx = regionX * PATH_REGION;
        int regionOy = regionY * PATH_REGION;

        int x = regionOx + 4 + Math.floorMod(h >>> 2, PATH_REGION - 8);
        int y = regionOy + 4 + Math.floorMod(h >>> 8, PATH_REGION - 8);
        int length = 16 + Math.floorMod(h >>> 14, 28); // 16–43
        int width = 1 + Math.floorMod(h >>> 20, 3);    // 1–3 tiles
        int dir = Math.floorMod(h >>> 24, 4);          // N E S W
        int turnChance = 3 + Math.floorMod(h >>> 5, 4); // turn every ~3–6 steps

        for (int step = 0; step < length; step++)
        {
            paintDirtOneBrush(chunk, x, y, width);

            // Occasional meander
            if (step > 0 && step % turnChance == 0)
            {
                int turn = Math.floorMod(hash(x, y, seed, step), 3) - 1; // -1,0,1
                dir = Math.floorMod(dir + turn, 4);
            }
            // Slight lateral wobble on longer stretches
            if ((hash(x, y, seed, 0xA0B1) & 7) == 0)
            {
                int side = Math.floorMod(dir + 1, 4);
                int[] sd = dirDelta(side);
                paintDirtOneBrush(chunk, x + sd[0], y + sd[1], Math.max(1, width - 1));
            }

            int[] d = dirDelta(dir);
            x += d[0];
            y += d[1];
        }
    }

    private static int[] dirDelta(int dir)
    {
        return switch (dir)
        {
            case 0 -> new int[] {0, -1};
            case 1 -> new int[] {1, 0};
            case 2 -> new int[] {0, 1};
            default -> new int[] {-1, 0};
        };
    }

    private void paintDirtOneBrush(WorldChunk chunk, int wx, int wy, int width)
    {
        int half = width / 2;
        for (int dy = -half; dy <= half; dy++)
        {
            for (int dx = -half; dx <= half; dx++)
            {
                // Round-ish brush for width 3 so paths aren't perfect squares
                if (width > 1 && dx * dx + dy * dy > half * half + 1)
                {
                    continue;
                }
                paintDirtOne(chunk, wx + dx, wy + dy);
            }
        }
    }

    private void paintDirtOne(WorldChunk chunk, int wx, int wy)
    {
        int lx = wx - chunk.worldOriginX();
        int ly = wy - chunk.worldOriginY();
        if (!inChunk(lx, ly))
        {
            return;
        }
        if (chunk.getOverlay(lx, ly) != 0)
        {
            return;
        }
        int existing = chunk.getFloor(lx, ly);
        if (existing == STONE_A || existing == STONE_B)
        {
            return;
        }
        chunk.setFloor(lx, ly, DIRT_ONE);
    }

    /**
     * Place ruins whose regions intersect this chunk (including neighbor
     * regions so walls/paths can cross chunk borders consistently).
     */
    private void stampRuinsOverlapping(WorldChunk chunk)
    {
        int ox = chunk.worldOriginX();
        int oy = chunk.worldOriginY();
        int x0 = ox - 16;
        int y0 = oy - 16;
        int x1 = ox + CHUNK_SIZE + 16;
        int y1 = oy + CHUNK_SIZE + 16;

        int rx0 = floorDiv(x0, RUIN_REGION);
        int ry0 = floorDiv(y0, RUIN_REGION);
        int rx1 = floorDiv(x1, RUIN_REGION);
        int ry1 = floorDiv(y1, RUIN_REGION);

        for (int ry = ry0; ry <= ry1; ry++)
        {
            for (int rx = rx0; rx <= rx1; rx++)
            {
                if (!shouldSpawnRuin(rx, ry))
                {
                    continue;
                }
                stampRuin(chunk, rx, ry);
            }
        }
    }

    private boolean shouldSpawnRuin(int regionX, int regionY)
    {
        int h = hash(regionX, regionY, seed, 0x2014E);
        return Math.floorMod(h, 100) < 14;
    }

    private void stampRuin(WorldChunk chunk, int regionX, int regionY)
    {
        int h = hash(regionX, regionY, seed, 0x2014E);
        int regionOx = regionX * RUIN_REGION;
        int regionOy = regionY * RUIN_REGION;

        int w = 8 + Math.floorMod(h >>> 3, 7);   // 8–14
        int ht = 6 + Math.floorMod(h >>> 8, 7);  // 6–12
        int padX = 4 + Math.floorMod(h >>> 12, Math.max(1, RUIN_REGION - w - 8));
        int padY = 4 + Math.floorMod(h >>> 16, Math.max(1, RUIN_REGION - ht - 8));
        int sx = regionOx + padX;
        int sy = regionOy + padY;

        int doorCount = 1 + Math.floorMod(h >>> 20, 3); // 1–3
        int doorMask = pickDoorMask(doorCount, h);

        int ox = chunk.worldOriginX();
        int oy = chunk.worldOriginY();

        // Mixed stone floor + perimeter walls
        for (int y = 0; y < ht; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int wx = sx + x;
                int wy = sy + y;
                int lx = wx - ox;
                int ly = wy - oy;
                if (!inChunk(lx, ly))
                {
                    continue;
                }

                boolean edge = x == 0 || y == 0 || x == w - 1 || y == ht - 1;
                float mix = valueNoise(wx * 0.2f, wy * 0.2f, seed ^ 0xF100E);
                int floor = mix > 0.15f ? STONE_A : STONE_B;
                chunk.setFloor(lx, ly, floor);

                if (edge)
                {
                    chunk.setOverlay(lx, ly, WALL);
                }
                else
                {
                    chunk.setOverlay(lx, ly, 0);
                }
                chunk.setEntrance(lx, ly, false);
            }
        }

        // Carve entrances + short dirt paths outward
        if ((doorMask & 1) != 0)
        {
            carveDoorNorth(chunk, sx, sy, w, ht);
        }
        if ((doorMask & 2) != 0)
        {
            carveDoorEast(chunk, sx, sy, w, ht);
        }
        if ((doorMask & 4) != 0)
        {
            carveDoorSouth(chunk, sx, sy, w, ht);
        }
        if ((doorMask & 8) != 0)
        {
            carveDoorWest(chunk, sx, sy, w, ht);
        }

        // Occasional interior broken wall bits (ruins look)
        if (Math.floorMod(h, 3) == 0 && w > 6 && ht > 5)
        {
            int ix = sx + 2 + Math.floorMod(h >>> 6, w - 4);
            int iy = sy + 2 + Math.floorMod(h >>> 10, ht - 4);
            setWallLocal(chunk, ix, iy);
            if (Math.floorMod(h, 2) == 0)
            {
                setWallLocal(chunk, ix + 1, iy);
            }
        }
    }

    private static int pickDoorMask(int doorCount, int h)
    {
        int[] sides = {1, 2, 4, 8};
        // Shuffle-ish via hash
        for (int i = 0; i < sides.length; i++)
        {
            int j = Math.floorMod(h + i * 17, sides.length);
            int tmp = sides[i];
            sides[i] = sides[j];
            sides[j] = tmp;
        }
        int mask = 0;
        for (int i = 0; i < doorCount && i < sides.length; i++)
        {
            mask |= sides[i];
        }
        return mask;
    }

    private void carveDoorNorth(WorldChunk chunk, int sx, int sy, int w, int ht)
    {
        int mx = sx + w / 2;
        openDoorPair(chunk, mx - 1, sy, mx, sy);
        for (int i = 1; i <= 5; i++)
        {
            paintPath(chunk, mx - 1, sy - i);
            paintPath(chunk, mx, sy - i);
        }
    }

    private void carveDoorSouth(WorldChunk chunk, int sx, int sy, int w, int ht)
    {
        int mx = sx + w / 2;
        int ey = sy + ht - 1;
        openDoorPair(chunk, mx - 1, ey, mx, ey);
        for (int i = 1; i <= 5; i++)
        {
            paintPath(chunk, mx - 1, ey + i);
            paintPath(chunk, mx, ey + i);
        }
    }

    private void carveDoorWest(WorldChunk chunk, int sx, int sy, int w, int ht)
    {
        int my = sy + ht / 2;
        openDoorPair(chunk, sx, my - 1, sx, my);
        for (int i = 1; i <= 5; i++)
        {
            paintPath(chunk, sx - i, my - 1);
            paintPath(chunk, sx - i, my);
        }
    }

    private void carveDoorEast(WorldChunk chunk, int sx, int sy, int w, int ht)
    {
        int my = sy + ht / 2;
        int ex = sx + w - 1;
        openDoorPair(chunk, ex, my - 1, ex, my);
        for (int i = 1; i <= 5; i++)
        {
            paintPath(chunk, ex + i, my - 1);
            paintPath(chunk, ex + i, my);
        }
    }

    private void openDoorPair(WorldChunk chunk, int x0, int y0, int x1, int y1)
    {
        openDoorCell(chunk, x0, y0);
        openDoorCell(chunk, x1, y1);
    }

    private void openDoorCell(WorldChunk chunk, int wx, int wy)
    {
        int lx = wx - chunk.worldOriginX();
        int ly = wy - chunk.worldOriginY();
        if (!inChunk(lx, ly))
        {
            return;
        }
        chunk.setOverlay(lx, ly, 0);
        chunk.setFloor(lx, ly, DIRT_THREE);
        chunk.setEntrance(lx, ly, true);
    }

    private void paintPath(WorldChunk chunk, int wx, int wy)
    {
        int lx = wx - chunk.worldOriginX();
        int ly = wy - chunk.worldOriginY();
        if (!inChunk(lx, ly))
        {
            return;
        }
        // Prefer Dirt Three near ruin entrances; blend with Dirt Two at ends
        int existing = chunk.getFloor(lx, ly);
        if (existing == STONE_A || existing == STONE_B)
        {
            return;
        }
        chunk.setFloor(lx, ly, DIRT_THREE);
        chunk.setOverlay(lx, ly, 0);
    }

    private void setWallLocal(WorldChunk chunk, int wx, int wy)
    {
        int lx = wx - chunk.worldOriginX();
        int ly = wy - chunk.worldOriginY();
        if (!inChunk(lx, ly))
        {
            return;
        }
        chunk.setOverlay(lx, ly, WALL);
    }

    private static boolean inChunk(int lx, int ly)
    {
        return lx >= 0 && ly >= 0 && lx < CHUNK_SIZE && ly < CHUNK_SIZE;
    }

    private static int floorDiv(int a, int b)
    {
        return a >= 0 ? a / b : (a - b + 1) / b;
    }

    // --- Deterministic noise ------------------------------------------------

    private static float fbm(float x, float y, int salt)
    {
        float sum = 0f;
        float amp = 1f;
        float freq = 1f;
        float norm = 0f;
        for (int o = 0; o < 4; o++)
        {
            sum += valueNoise(x * freq, y * freq, salt + o * 1013) * amp;
            norm += amp;
            amp *= 0.5f;
            freq *= 2.05f;
        }
        return sum / norm;
    }

    /** Smooth value noise in roughly [-1, 1]. */
    private static float valueNoise(float x, float y, int salt)
    {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        float tx = x - x0;
        float ty = y - y0;
        float sx = tx * tx * (3f - 2f * tx);
        float sy = ty * ty * (3f - 2f * ty);

        float n00 = hashFloat(x0, y0, salt);
        float n10 = hashFloat(x1, y0, salt);
        float n01 = hashFloat(x0, y1, salt);
        float n11 = hashFloat(x1, y1, salt);

        float nx0 = n00 + (n10 - n00) * sx;
        float nx1 = n01 + (n11 - n01) * sx;
        return nx0 + (nx1 - nx0) * sy;
    }

    private static float hashFloat(int x, int y, int salt)
    {
        int h = hash(x, y, salt, 0x9E3779B9);
        // Map to [-1, 1]
        return ((h & 0xFFFF) / 32767.5f) - 1f;
    }

    public static int hash(int a, int b, int c, int d)
    {
        int h = a * 374761393
                + b * 668265263
                + c * 1274126177
                + d * 0x85ebca6b;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return h;
    }
}
