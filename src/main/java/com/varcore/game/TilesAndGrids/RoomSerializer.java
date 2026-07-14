package com.varcore.game.TilesAndGrids;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class RoomSerializer
{
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public static Path defaultExportPath(String roomName)
    {
        String safe = sanitizeFileName(roomName);
        return Path.of("assets", "rooms", safe + ".json");
    }

    public static boolean saveRoom(GridManager grid, String roomName, Path path)
    {
        RoomData data = new RoomData();
        data.name = roomName == null || roomName.isBlank() ? "untitled" : roomName.trim();
        data.width = grid.getWidth();
        data.height = grid.getHeight();
        data.tileSize = grid.getTilesize();
        data.worldX = grid.getWorldX();
        data.worldY = grid.getWorldY();
        data.tiles = grid.getGridCopy();

        try
        {
            Path parent = path.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
            Files.writeString(path, gson.toJson(data));
            return true;
        }
        catch (IOException e)
        {
            System.err.println("Failed to save room: " + e.getMessage());
            return false;
        }
    }

    public static boolean saveRoom(GridManager grid, String roomName)
    {
        return saveRoom(grid, roomName, defaultExportPath(roomName));
    }

    public static GridManager loadRoom(Path path)
    {
        try
        {
            RoomData data = gson.fromJson(Files.readString(path), RoomData.class);
            if (data == null)
            {
                return null;
            }

            GridManager grid = new GridManager(
                    data.width,
                    data.height,
                    data.tileSize,
                    data.worldX,
                    data.worldY
            );

            if (data.tiles == null)
            {
                return grid;
            }

            for (int y = 0; y < data.height; y++)
            {
                for (int x = 0; x < data.width; x++)
                {
                    if (data.tiles[y] == null || data.tiles[y][x] == null)
                    {
                        continue;
                    }
                    CellData cell = data.tiles[y][x];
                    grid.setTileId(x, y, cell.getTileID());
                    grid.setOverlayTileId(x, y, cell.getOverlayTileID());
                    grid.setEntrance(x, y, cell.isEntrance());
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

    /** Convenience overload kept for older call sites. */
    public static void saveRoom(GridManager grid, String roomName, String path)
    {
        saveRoom(grid, roomName, Path.of(path));
    }

    public static GridManager loadRoom(String path)
    {
        return loadRoom(Path.of(path));
    }

    public static String sanitizeFileName(String name)
    {
        if (name == null || name.isBlank())
        {
            return "untitled";
        }
        String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        cleaned = cleaned.replaceAll("\\s+", "_");
        return cleaned.isEmpty() ? "untitled" : cleaned;
    }
}
