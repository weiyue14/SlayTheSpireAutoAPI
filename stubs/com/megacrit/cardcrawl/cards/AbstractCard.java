package com.megacrit.cardcrawl.cards;

import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.monsters.AbstractMonster;

import java.util.UUID;

public class AbstractCard {
    public enum CardType { ATTACK, SKILL, POWER, STATUS, CURSE }
    public enum CardRarity { BASIC, SPECIAL, COMMON, UNCOMMON, RARE, CURSE }
    public enum CardTarget { ENEMY, SELF_AND_ENEMY, ALL_ENEMY, NONE, SELF }

    public String cardID;
    public String name;
    public UUID uuid;
    public int cost;
    public int costForTurn;
    public int timesUpgraded;
    public boolean upgraded;
    public int misc;
    public int price;
    public boolean exhaust;
    public boolean isEthereal;
    public boolean isInnate;
    public boolean selfRetain;
    public boolean freeToPlayOnce;
    public int damage;
    public int block;
    public int magicNumber;
    public int baseDamage;
    public int baseBlock;
    public CardType type;
    public CardRarity rarity;
    public CardTarget target;

    public boolean canUse(AbstractPlayer p, AbstractMonster m) {
        return true;
    }
}
