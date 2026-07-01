package com.varcore.game.TilesAndGrids;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;

import javax.imageio.ImageIO;

public class TileTextureRegistry 
{
    private final HashMap<Integer, String> texturePaths = new HashMap<>();
    private final HashMap<Integer, BufferedImage> loadedTextures = new HashMap<>();
    private BufferedImage missingTexture;
    private static final int missingTextureID = 0;

    public TileTextureRegistry()
    {
        addTexturePaths();
        loadMissingTexture();
    }

    public BufferedImage getTexture(int textureID) //texture 0 will be id for the missing texture tile asset
    {
        BufferedImage img;
        if (!texturePaths.containsKey(textureID)) return missingTexture;
        if (loadedTextures.containsKey(textureID))
        {
            return loadedTextures.get(textureID);
        }
        File file = new File(texturePaths.get(textureID));
        try
        {
            img = ImageIO.read(file);
            loadedTextures.put(textureID, img);
            return img;
        }
        catch (Exception e) 
        {
            System.err.print(e);
            return missingTexture;
        }
        
    }

    private void loadMissingTexture()
    {
        try
        {
            missingTexture = ImageIO.read(new File(texturePaths.get(0)));

            if (missingTexture == null)
            {
                throw new RuntimeException("Missing texture file could not be read.");
            }

            loadedTextures.put(missingTextureID, missingTexture);
        }
        catch (Exception e)
        {
            throw new RuntimeException("CRITICAL: Missing texture ID: " + 0 + ", failed to load.", e);
        }
    }

    public void unloadTexture(int key)
    {
        if (key == missingTextureID) return;
        loadedTextures.remove(key);
    }

    public void unloadAllTextures()
    {
        loadedTextures.clear();
        loadedTextures.put(missingTextureID, missingTexture);
    }

    private void addTexturePaths() //I will manually add the paths so I can reuse them later
    {
        texturePaths.put(0, "Missing Texxture asset path");
        texturePaths.put(1, "THIS WONT WORK CAUSE THIS AINT A PATH BUT A PLACEHOLDER FOR NOW");
    }
}
