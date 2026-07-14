package com.varcore.game;

import com.varcore.engine.core.Game;
import com.varcore.engine.render.Renderer;
import com.varcore.game.RoomMaker.RoomMaker;
import com.varcore.game.TilesAndGrids.TileRegistry;
import com.varcore.game.TilesAndGrids.TileTextureRegistry;

public class Main extends Game
{
    private RoomMaker roomMaker;

    public Main()
    {
        super("EverDeep Room Maker", 1100, 700);
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

        TileRegistry tileRegistry = new TileRegistry();
        TileTextureRegistry textureRegistry = new TileTextureRegistry();
        roomMaker = new RoomMaker(tileRegistry, textureRegistry, getWindow().getWidth(), getWindow().getHeight());
        roomMaker.getCamera().setViewportSize(getWindow().getWidth(), getWindow().getHeight());
    }

    @Override
    protected void update(float dt)
    {
        roomMaker.update(getInput(), dt, getWindow().getWidth(), getWindow().getHeight());
    }

    @Override
    protected void render(Renderer renderer)
    {
        super.render(renderer);
        roomMaker.render(renderer);
    }
}
