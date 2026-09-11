package com.megacrit.cardcrawl.core;

import com.megacrit.cardcrawl.powers.AbstractPower;

import java.util.ArrayList;

public class AbstractCreature {
    public int currentHealth;
    public int maxHealth;
    public int currentBlock;
    public ArrayList<AbstractPower> powers = new ArrayList<>();
}
