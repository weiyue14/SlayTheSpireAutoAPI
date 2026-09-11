package com.megacrit.cardcrawl.powers;

public class AbstractPower {
    public enum PowerType { BUFF, DEBUFF }

    public String ID;
    public String name;
    public int amount;
    public PowerType type;
}
