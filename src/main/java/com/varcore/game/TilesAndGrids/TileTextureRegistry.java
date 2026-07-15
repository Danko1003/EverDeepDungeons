package com.varcore.game.TilesAndGrids;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;

import javax.imageio.ImageIO;

public class TileTextureRegistry 
{
    private final HashMap<Integer, String> sheetPaths = new HashMap<>();
    private final HashMap<Integer, BufferedImage> loadedSheets = new HashMap<>();
    private final HashMap<Integer, TextureRegion> textureRegions = new HashMap<>();
    private final HashMap<Integer, BufferedImage> loadedTextures = new HashMap<>();

    private BufferedImage missingTexture;

    private static final int MISSING_TEXTURE_ID = 0;
    private static final int MISSING_SHEET_ID = 0;

    public TileTextureRegistry()
    {
        addSheetPaths();
        addTextureRegions();
        loadMissingTexture();
    }

    public BufferedImage getTexture(int textureID)
    {
        if (!textureRegions.containsKey(textureID))
        {
            return missingTexture;
        }

        if (loadedTextures.containsKey(textureID))
        {
            return loadedTextures.get(textureID);
        }

        TextureRegion region = textureRegions.get(textureID);
        BufferedImage sheet = getSheet(region.sheetID);

        if (sheet == null)
        {
            return missingTexture;
        }

        try
        {
            BufferedImage texture = sheet.getSubimage(
                region.x,
                region.y,
                region.width,
                region.height
            );

            loadedTextures.put(textureID, texture);
            return texture;
        }
        catch (Exception e)
        {
            System.err.println("Failed to crop texture ID " + textureID + ": " + e.getMessage());
            return missingTexture;
        }
    }

    private BufferedImage getSheet(int sheetID)
    {
        if (!sheetPaths.containsKey(sheetID))
        {
            return null;
        }

        if (loadedSheets.containsKey(sheetID))
        {
            return loadedSheets.get(sheetID);
        }

        try
        {
            BufferedImage sheet = ImageIO.read(new File(sheetPaths.get(sheetID)));

            if (sheet == null)
            {
                return null;
            }

            loadedSheets.put(sheetID, sheet);
            return sheet;
        }
        catch (Exception e)
        {
            System.err.println("Failed to load sheet ID " + sheetID + ": " + e.getMessage());
            return null;
        }
    }

    private void loadMissingTexture()
    {
        BufferedImage texture = getTexture(MISSING_TEXTURE_ID);

        if (texture == null)
        {
            throw new RuntimeException("CRITICAL: Missing texture failed to load.");
        }

        missingTexture = texture;
        loadedTextures.put(MISSING_TEXTURE_ID, missingTexture);
    }

    public void unloadTexture(int textureID)
    {
        if (textureID == MISSING_TEXTURE_ID) return;
        loadedTextures.remove(textureID);
    }

    public void unloadSheet(int sheetID)
    {
        if (sheetID == MISSING_SHEET_ID) return;

        loadedSheets.remove(sheetID);

        loadedTextures.entrySet().removeIf(entry -> {
            TextureRegion region = textureRegions.get(entry.getKey());
            return region != null && region.sheetID == sheetID;
        });
    }

    public void unloadAllTextures()
    {
        loadedTextures.clear();
        loadedTextures.put(MISSING_TEXTURE_ID, missingTexture);
    }

    public void unloadAllSheets()
    {
        loadedSheets.clear();
        getSheet(MISSING_SHEET_ID);
        loadedTextures.clear();
        loadedTextures.put(MISSING_TEXTURE_ID, missingTexture);
    }

    private void addSheetPaths()
    {
        sheetPaths.put(0, "assets/Tiles/MissingTexture.png");
        sheetPaths.put(1, "assets/Tiles/TilesSheetOne.png");
        sheetPaths.put(2, "assets/Tiles/EmptyTile.png");
    }

    private void addTextureRegions()
    {
        textureRegions.put(0, new TextureRegion(0, 0, 0, 32, 32)); // Missing fallback
        textureRegions.put(1, new TextureRegion(2, 0, 0, 32, 32)); // EmptyTile
        textureRegions.put(2, new TextureRegion(1, 96, 0, 32, 32)); // Stone1
        textureRegions.put(3, new TextureRegion(1, 96, 32, 32, 32)); // Stone2
        textureRegions.put(4, new TextureRegion(1, 0, 0, 32, 32)); // GrassLight
        textureRegions.put(5, new TextureRegion(1, 32, 0, 32, 32)); // GrassMedium
        textureRegions.put(6, new TextureRegion(1, 32, 32, 32, 32)); // GrassDark
        textureRegions.put(7, new TextureRegion(1, 0, 32, 32, 32)); // GrassDarkest
        textureRegions.put(8, new TextureRegion(1, 64, 96, 32, 32)); // StoneWallFaceLower (brick)
        textureRegions.put(9, new TextureRegion(1, 96, 96, 32, 32)); // StoneWallFaceOverLay (brick)
        textureRegions.put(10, new TextureRegion(1, 96, 64, 32, 32)); // StoneWallTop
        textureRegions.put(11, new TextureRegion(1, 0, 64, 32, 32)); // DirtPathOne
        textureRegions.put(12, new TextureRegion(1, 32, 64, 32, 32)); // DirtPathThree
        textureRegions.put(13, new TextureRegion(1, 64, 0, 32, 32)); // DirtNormal
        textureRegions.put(14, new TextureRegion(1, 64, 32, 32, 32)); // DirtBurnt
    }
}