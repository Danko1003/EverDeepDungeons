package com.varcore.game;

import java.awt.event.KeyEvent;

import com.varcore.engine.core.Game;
import com.varcore.engine.render.Renderer;
import com.varcore.game.RoomMaker.RoomMaker;
import com.varcore.game.TilesAndGrids.TileRegistry;
import com.varcore.game.TilesAndGrids.TileTextureRegistry;
import com.varcore.game.world.OpenWorldMode;

public class Main extends Game
{
    private enum Mode
    {
        MENU,
        BUILD,
        OPEN_WORLD
    }

    private Mode mode = Mode.MENU;
    private MainMenu menu;
    private RoomMaker roomMaker;
    private OpenWorldMode openWorld;

    public Main()
    {
        super("EverDeep Dungeons", 1920, 1080);
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
        openWorld = new OpenWorldMode(tileRegistry, textureRegistry);
        openWorld.getCamera().setViewportSize(getWindow().getWidth(), getWindow().getHeight());

        menu = new MainMenu(
                () -> switchMode(Mode.BUILD),
                () -> switchMode(Mode.OPEN_WORLD));
        getWindow().setTitle("EverDeep Dungeons");
    }

    @Override
    protected void update(float dt)
    {
        int width = getWindow().getWidth();
        int height = getWindow().getHeight();

        if (getInput().isKeyPressed(KeyEvent.VK_F11))
        {
            getWindow().setFullscreen(!getWindow().isFullscreen());
        }

        if (getInput().isKeyPressed(KeyEvent.VK_ESCAPE) && mode != Mode.MENU)
        {
            switchMode(Mode.MENU);
            return;
        }

        switch (mode)
        {
            case MENU -> menu.update(getInput(), width, height);
            case BUILD -> roomMaker.update(getInput(), dt, width, height);
            case OPEN_WORLD -> openWorld.update(getInput(), dt, width, height);
        }
    }

    @Override
    protected void render(Renderer renderer)
    {
        super.render(renderer);
        switch (mode)
        {
            case MENU -> menu.render(renderer);
            case BUILD -> roomMaker.render(renderer);
            case OPEN_WORLD -> openWorld.render(renderer);
        }
    }

    private void switchMode(Mode next)
    {
        mode = next;
        if (mode == Mode.OPEN_WORLD)
        {
            openWorld.onEnter();
        }
        getWindow().setTitle(switch (mode)
        {
            case MENU -> "EverDeep Dungeons";
            case BUILD -> "EverDeep Dungeons — Build Mode";
            case OPEN_WORLD -> "EverDeep Dungeons — Open World";
        });
    }
}
