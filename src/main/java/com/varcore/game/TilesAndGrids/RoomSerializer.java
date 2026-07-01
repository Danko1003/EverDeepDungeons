package com.varcore.game.TilesAndGrids;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class RoomSerializer 
{
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static void saveRoom(GridManager grid, String roomName, String path)
    {
        RoomData data = new RoomData();

        data.name = roomName;
        data.width = grid.getWidth();
        data.height = grid.getHeight();
        data.tileSize = grid.getTilesize();
        data.worldX = grid.getWorldX();
        data.worldY = grid.getWorldY();
        data.tiles = grid.getGridCopy();

        try (FileWriter writer = new FileWriter(path))
        {
            gson.toJson(data, writer);
        }
        catch (IOException e)
        {
            System.err.println("Failed to save room: " + e.getMessage());
        }
    }

    public static GridManager loadRoom(String path)
    {
        try (FileReader reader = new FileReader(path))
        {
            RoomData data = gson.fromJson(reader, RoomData.class);

            GridManager grid = new GridManager(
                    data.width,
                    data.height,
                    data.tileSize,
                    data.worldX,
                    data.worldY
            );

            for (int y = 0; y < data.height; y++)
            {
                for (int x = 0; x < data.width; x++)
                {
                    grid.setTileId(x, y, data.tiles[y][x].getTileID());
                }
            }

            return grid;
        }
        catch (IOException e)
        {
            System.err.println("Failed to load room: " + e.getMessage());
            return null;
        }
    }
}