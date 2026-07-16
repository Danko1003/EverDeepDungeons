package com.varcore.game;

import java.awt.Color;
import java.awt.Font;

import com.varcore.engine.input.InputManager;
import com.varcore.engine.render.Renderer;
import com.varcore.game.RoomMaker.EditorButton;

/** Start screen: Build Mode or Open World. */
public final class MainMenu
{
    private final EditorButton buildButton = new EditorButton(0, 0, 320, 58);
    private final EditorButton worldButton = new EditorButton(0, 0, 320, 58);
    private final Font titleFont = new Font("Segoe UI", Font.BOLD, 38);
    private final Font subtitleFont = new Font("Segoe UI", Font.PLAIN, 15);
    private int screenW = 1100;
    private int screenH = 700;

    public MainMenu(Runnable onBuild, Runnable onWorld)
    {
        buildButton.setText("BUILD MODE");
        buildButton.setOnClick(button -> onBuild.run());
        worldButton.setText("OPEN WORLD");
        worldButton.setOnClick(button -> onWorld.run());
    }

    public void update(InputManager input, int screenW, int screenH)
    {
        this.screenW = Math.max(1, screenW);
        this.screenH = Math.max(1, screenH);
        layout();
        buildButton.update(input);
        worldButton.update(input);
    }

    public void render(Renderer renderer)
    {
        renderer.drawRect(0, 0, screenW, screenH, new Color(8, 12, 18), true, 900);

        float panelW = Math.min(520f, screenW * 0.82f);
        float panelH = Math.min(430f, screenH * 0.78f);
        float panelX = (screenW - panelW) * 0.5f;
        float panelY = (screenH - panelH) * 0.5f;

        renderer.drawRect(panelX, panelY, panelW, panelH,
                new Color(17, 25, 33, 245), true, 901);
        renderer.drawRect(panelX, panelY, panelW, panelH,
                new Color(74, 178, 125), false, 902);

        renderer.drawTextScreen("EVERDEEP DUNGEONS",
                panelX + 46f, panelY + 70f, titleFont, new Color(224, 235, 226));
        renderer.drawTextScreen("Build rooms or explore a generated open world",
                panelX + 48f, panelY + 101f, subtitleFont, new Color(138, 163, 153));

        buildButton.render(renderer);
        worldButton.render(renderer);

        renderer.drawTextScreen(
                "F11  Fullscreen     ESC  Return to menu",
                panelX + 48f, panelY + panelH - 30f,
                subtitleFont, new Color(105, 125, 137));
    }

    private void layout()
    {
        float panelW = Math.min(520f, screenW * 0.82f);
        float panelH = Math.min(430f, screenH * 0.78f);
        float panelX = (screenW - panelW) * 0.5f;
        float panelY = (screenH - panelH) * 0.5f;

        float buttonW = Math.max(210f, panelW - 96f);
        float buttonH = Math.max(44f, Math.min(62f, screenH * 0.085f));
        float buttonX = panelX + (panelW - buttonW) * 0.5f;
        float firstY = panelY + panelH * 0.39f;
        float gap = Math.max(14f, buttonH * 0.34f);

        buildButton.setBounds(buttonX, firstY, buttonW, buttonH);
        worldButton.setBounds(buttonX, firstY + buttonH + gap, buttonW, buttonH);
    }
}
