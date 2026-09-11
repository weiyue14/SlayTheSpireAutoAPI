package com.megacrit.cardcrawl.ui.buttons;

public class EndTurnButton {
    public boolean disabled = false;

    public void disable(boolean triggerEndTurn) {
        disabled = triggerEndTurn;
    }
}
