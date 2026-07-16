package com.varcore.game.TilesAndGrids;

public enum Corner
{
    TOP_LEFT(Direction.UP_LEFT, Direction.UP, Direction.LEFT),
    TOP_RIGHT(Direction.UP_RIGHT, Direction.UP, Direction.RIGHT),
    BOTTOM_LEFT(Direction.DOWN_LEFT, Direction.DOWN, Direction.LEFT),
    BOTTOM_RIGHT(Direction.DOWN_RIGHT, Direction.DOWN, Direction.RIGHT);

    public final Direction diagonal;
    public final Direction vertical;
    public final Direction horizontal;

    Corner(Direction diagonal, Direction vertical, Direction horizontal)
    {
        this.diagonal = diagonal;
        this.vertical = vertical;
        this.horizontal = horizontal;
    }
}
