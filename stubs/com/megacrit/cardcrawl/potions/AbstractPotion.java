package com.megacrit.cardcrawl.potions;

import com.megacrit.cardcrawl.core.AbstractCreature;

public class AbstractPotion {
    public enum PotionRarity { COMMON, UNCOMMON, RARE, SPECIAL }

    public String ID;
    public String name;
    public boolean isThrown;
    public boolean targetRequired;
    public int slot;
    public PotionRarity rarity;
    public int potency;
    public String description;
    public AbstractCreature usedOn;

    public boolean canUse() {
        return true;
    }

    public boolean canDiscard() {
        return true;
    }

    public void use(AbstractCreature target) {
        usedOn = target;
    }
}
