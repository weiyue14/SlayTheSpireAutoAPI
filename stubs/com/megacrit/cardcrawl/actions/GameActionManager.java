package com.megacrit.cardcrawl.actions;

import com.megacrit.cardcrawl.cards.CardQueueItem;

import java.util.ArrayList;

public class GameActionManager {
    public enum Phase { WAITING_ON_USER, EXECUTING_ACTIONS }

    public static int turn;
    public static int totalDiscardedThisTurn;
    public Phase phase;
    public AbstractGameAction currentAction;
    public ArrayList<CardQueueItem> cardQueue = new ArrayList<>();
}
