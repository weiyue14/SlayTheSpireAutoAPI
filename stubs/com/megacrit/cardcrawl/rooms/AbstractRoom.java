package com.megacrit.cardcrawl.rooms;

import com.megacrit.cardcrawl.monsters.MonsterGroup;

public class AbstractRoom {
    public enum RoomPhase { COMBAT, EVENT, COMPLETE, INCOMPLETE }

    public RoomPhase phase;
    public MonsterGroup monsters;
    public boolean isBattleOver;
}
