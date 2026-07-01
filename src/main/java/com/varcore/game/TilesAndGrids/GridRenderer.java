package com.varcore.game.TilesAndGrids;

import java.awt.image.BufferedImage;


import com.varcore.engine.render.RenderEffects;
import com.varcore.engine.render.RenderTransform;
import com.varcore.engine.render.Renderer;
import com.varcore.game.CamGame;

public class GridRenderer 
{
    private final TileRegistry tileRegistry;
    private final TileTextureRegistry tileTextureRegistry;


    public GridRenderer(TileRegistry tileRegistry, TileTextureRegistry tileTextureRegistry)
    {
        this.tileRegistry = tileRegistry;
        this.tileTextureRegistry = tileTextureRegistry;
    }

    public void render(Renderer renderer, GridManager grid, CamGame cam, int layer)
    {
        for (int y = 0; y < grid.getHeight(); y++)
        {
            for (int x = 0; x < grid.getWidth(); x++)
            {
                int tileID = grid.getTileId(x, y);
                Tile tile = tileRegistry.getTile(tileID);

                int textureID = tile.getTextureId();
                BufferedImage img = tileTextureRegistry.getTexture(textureID);

                int drawX = grid.getWorldX() + x * grid.getTilesize();
                int drawY = grid.getWorldY() + y * grid.getTilesize();
                        
                
                
                renderer.drawSprite(img, drawX - cam.getCamX(), drawY - cam.getCamY(), grid.getTilesize(), grid.getTilesize(), layer, RenderTransform.rotation(90));

            }
        }
    }
}
