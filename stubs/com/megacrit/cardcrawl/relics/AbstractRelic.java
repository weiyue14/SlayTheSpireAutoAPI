package com.megacrit.cardcrawl.relics;

public class AbstractRelic {
    public String relicId;
    public String name;
    public int counter;
    public int onUsePotionCalls;

    public void onUsePotion() {
        onUsePotionCalls++;
    }
}
