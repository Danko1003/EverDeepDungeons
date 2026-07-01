package com.varcore.game;

import java.awt.event.KeyEvent;

import com.varcore.engine.core.Game;
import com.varcore.engine.render.Renderer;
import com.varcore.game.TilesAndGrids.*;

public class Main extends Game 
{
    private TileRegistry tileRegistry;
    private TileTextureRegistry textureRegistry;
    private GridRenderer gridRenderer;
    private GridManager grid;
    private CamGame camera;
    private Inputs inputs;

    public Main() 
    {
        super("Tile Render Test", 800, 600);
    }

    public static void main(String[] args) 
    {
        new Main().run();
    }

    @Override
    protected void onStart() 
    {
        super.onStart();
        getScenes().createScene("main");

        tileRegistry = new TileRegistry();
        textureRegistry = new TileTextureRegistry();
        gridRenderer = new GridRenderer(tileRegistry, textureRegistry);
        inputs = new Inputs();

        grid = new GridManager(10, 8, 32, 100, 100);
        camera = new CamGame(0, 0, 1);
        for (int y = 0; y < grid.getHeight(); y++)
        {
            for (int x = 0; x < grid.getWidth(); x++)
            {
                if ((x + y) % 2 == 0)
                {
                    grid.setTileId(x, y, 1);
                }
                else
                {
                    grid.setTileId(x, y, 2);
                }
            }
        }
    }

    @Override
    protected void update(float dt) 
    {
        inputs.camInputs(getInput(), camera);
    }

    @Override
    protected void render(Renderer renderer) 
    {
        super.render(renderer);

        gridRenderer.render(renderer, grid, camera, 0);
    }
}