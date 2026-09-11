package com.megacrit.cardcrawl.dungeons;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.OverlayMenu;
import com.megacrit.cardcrawl.ui.panels.TopPanel;

import java.util.ArrayList;

public class AbstractDungeon {
    public enum CurrentScreen { NONE, MENU, MAP, SHOP, SETTING, DEATH, VICTORY }

    public static AbstractPlayer player;
    public static GameActionManager actionManager;
    public static int floorNum;
    public static int actNum;
    public static int ascensionLevel;
    public static String bossKey;
    public static CurrentScreen screen;
    public static boolean isScreenUp;
    public static boolean firstRoomChosen;
    public static ArrayList<ArrayList<MapRoomNode>> map;
    public static OverlayMenu overlayMenu = new OverlayMenu();
    public static TopPanel topPanel = new TopPanel();

    // offline smoke-test hooks (never packaged into the mod jar)
    public static AbstractRoom currRoom;
    public static MapRoomNode currMapNode;
    public static boolean playerInDungeon;

    public static AbstractRoom getCurrRoom() {
        return currRoom;
    }

    public static MonsterGroup getMonsters() {
        return currRoom == null ? null : currRoom.monsters;
    }

    public static MapRoomNode getCurrMapNode() {
        return currMapNode;
    }

    public static boolean isPlayerInDungeon() {
        return playerInDungeon;
    }
}
