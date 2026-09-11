package com.megacrit.cardcrawl.monsters;

import com.megacrit.cardcrawl.core.AbstractCreature;

import java.util.ArrayList;

public class AbstractMonster extends AbstractCreature {
    public enum Intent {
        ATTACK, ATTACK_BUFF, ATTACK_DEBUFF, ATTACK_DEFEND,
        DEFEND, DEFEND_BUFF, DEFEND_DEBUFF,
        DEBUFF, STRONG_DEBUFF, WEAK_DEBUFF,
        ESCAPE, SLEEP, STUN, UNKNOWN, NONE, DEBUG
    }

    public String id;
    public String name;
    public Intent intent;
    public boolean halfDead;
    public boolean isEscaping;
    public ArrayList<Byte> moveHistory = new ArrayList<>();

    // mimics the real game's private fields (read via reflection by the mod)
    private Object move;
    private int intentDmg;
    private String moveName;

    public boolean isDeadOrEscaped() {
        return false;
    }
}
