package com.varcore.game.TilesAndGrids;

/** Optional material behavior tags used by cross-material transitions. */
public enum MaterialTrait
{
    /** This material can visually char when it expands into a burnt material. */
    BURNABLE,
    /** This material chars neighbouring burnable expansion fringes. */
    BURNT
}
