package com.varcore.game.TilesAndGrids;

/**
 * 8-bit alpha mask. For crisp pixel art, values are typically 0 or 255.
 */
public final class PixelMask
{
    private final int width;
    private final int height;
    private final byte[] alpha;

    public PixelMask(int width, int height)
    {
        this.width = width;
        this.height = height;
        this.alpha = new byte[width * height];
    }

    public PixelMask(int width, int height, byte[] alpha)
    {
        if (alpha.length != width * height)
        {
            throw new IllegalArgumentException("alpha length must equal width * height");
        }
        this.width = width;
        this.height = height;
        this.alpha = alpha;
    }

    public static PixelMask full(int size)
    {
        PixelMask mask = new PixelMask(size, size);
        for (int i = 0; i < mask.alpha.length; i++)
        {
            mask.alpha[i] = (byte) 255;
        }
        return mask;
    }

    public static PixelMask empty(int size)
    {
        return new PixelMask(size, size);
    }

    public int getWidth()
    {
        return width;
    }

    public int getHeight()
    {
        return height;
    }

    public int getAlpha(int x, int y)
    {
        if (x < 0 || y < 0 || x >= width || y >= height)
        {
            return 0;
        }
        return Byte.toUnsignedInt(alpha[y * width + x]);
    }

    public void setAlpha(int x, int y, int value)
    {
        if (x < 0 || y < 0 || x >= width || y >= height)
        {
            return;
        }
        alpha[y * width + x] = (byte) Math.max(0, Math.min(255, value));
    }

    public void mergeMax(PixelMask source)
    {
        int w = Math.min(width, source.width);
        int h = Math.min(height, source.height);
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int merged = Math.max(getAlpha(x, y), source.getAlpha(x, y));
                setAlpha(x, y, merged);
            }
        }
    }

    public PixelMask copy()
    {
        return new PixelMask(width, height, alpha.clone());
    }

    public boolean isAnyOpaque()
    {
        for (byte b : alpha)
        {
            if (Byte.toUnsignedInt(b) > 0)
            {
                return true;
            }
        }
        return false;
    }
}
