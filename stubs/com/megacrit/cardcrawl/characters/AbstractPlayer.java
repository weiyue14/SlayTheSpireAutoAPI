package com.megacrit.cardcrawl.characters;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.ui.panels.EnergyManager;

import java.util.ArrayList;

public class AbstractPlayer extends AbstractCreature {
    public enum PlayerClass { IRONCLAD, THE_SILENT, DEFECT, WATCHER }

    public CardGroup masterDeck = new CardGroup();
    public CardGroup hand = new CardGroup();
    public CardGroup drawPile = new CardGroup();
    public CardGroup discardPile = new CardGroup();
    public CardGroup exhaustPile = new CardGroup();
    public CardGroup limbo = new CardGroup();
    public AbstractCard cardInUse;
    public EnergyManager energy = new EnergyManager();
    public ArrayList<AbstractPotion> potions = new ArrayList<>();
    public ArrayList<AbstractRelic> relics = new ArrayList<>();
    public ArrayList<AbstractOrb> orbs = new ArrayList<>();
    public int gold;
    public int potionSlots;
    public int damagedThisCombat;
    public int maxOrbs;
    public int cardsPlayedThisTurn;
    public int attacksPlayedThisTurn;
    public int skillsPlayedThisTurn;
    public int powersPlayedThisCombat;
    public PlayerClass chosenClass;

    public boolean hasRelic(String id) {
        return false;
    }
}
