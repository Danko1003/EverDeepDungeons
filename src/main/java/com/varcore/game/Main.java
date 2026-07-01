package com.varcore.game;

import com.varcore.engine.core.Game;
import com.varcore.engine.render.Renderer;
import java.awt.Color;

public class Main extends Game {
    public Main() {
        super("Blank Project", 800, 600);
    }

    public static void main(String[] args) {
        new Main().run();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getScenes().createScene("main");
    }

    @Override
    protected void update(float dt) {}

    @Override
    protected void render(Renderer renderer) {
        super.render(renderer);
        renderer.drawRect(100, 100, 200, 150, new Color(0x5B, 0x8C, 0xFF), true, 0);
    }
}
