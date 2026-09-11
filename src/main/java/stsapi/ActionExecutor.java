package stsapi;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;

import java.util.List;

/**
 * Executes submitted actions on the game thread. The play / end-turn /
 * potion logic mirrors CommunicationMod's CommandExecutor (battle-tested
 * against the real game), including all availability checks.
 */
final class ActionExecutor {

    private ActionExecutor() {
    }

    static String execute(PendingAction a) {
        switch (a.cmd) {
            case "play":
                return play(a);
            case "end":
                return endTurn();
            case "potion_use":
                return potion(a, true);
            case "potion_discard":
                return potion(a, false);
            default:
                throw new IllegalArgumentException(
                        "unsupported command '" + a.cmd + "' (supported: play, end, potion_use, potion_discard)");
        }
    }

    // ------------------------------------------------------------------ play

    private static String play(PendingAction a) {
        requireCombat("play");
        AbstractPlayer p = AbstractDungeon.player;
        List<AbstractCard> hand = p.hand.group;
        if (a.handIndex < 0 || a.handIndex >= hand.size()) {
            throw new IllegalArgumentException(
                    "hand index out of bounds: " + a.handIndex + " (hand size " + hand.size() + ")");
        }
        AbstractCard card = hand.get(a.handIndex);

        AbstractMonster target = null;
        if (a.targetIndex >= 0) {
            List<AbstractMonster> monsters = AbstractDungeon.getCurrRoom().monsters.monsters;
            if (a.targetIndex >= monsters.size()) {
                throw new IllegalArgumentException(
                        "target index out of bounds: " + a.targetIndex + " (monsters " + monsters.size() + ")");
            }
            target = monsters.get(a.targetIndex);
        }

        if (!card.canUse(p, target)) {
            throw new IllegalArgumentException(
                    "card cannot be played with the selected target: " + card.cardID);
        }
        if (card.target == AbstractCard.CardTarget.ENEMY
                || card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {
            if (target == null) {
                throw new IllegalArgumentException("card requires an enemy target: " + card.cardID);
            }
            AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, target));
        } else {
            AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, null));
        }
        return "queued play: " + card.cardID;
    }

    // ------------------------------------------------------------------ end

    private static String endTurn() {
        requireCombat("end turn");
        AbstractDungeon.overlayMenu.endTurnButton.disable(true);
        return "ended turn";
    }

    // ------------------------------------------------------------------ potion

    private static String potion(PendingAction a, boolean use) {
        requireInRun("potion");
        AbstractPlayer p = AbstractDungeon.player;
        if (a.potionSlot < 0 || a.potionSlot >= p.potionSlots) {
            throw new IllegalArgumentException("potion index out of bounds: " + a.potionSlot);
        }
        AbstractPotion po = p.potions.get(a.potionSlot);
        if (po instanceof PotionSlot) {
            throw new IllegalArgumentException("no potion in the selected slot");
        }
        if (use) {
            if (!po.canUse()) {
                throw new IllegalArgumentException("selected potion cannot be used");
            }
            if (po.targetRequired) {
                if (a.targetIndex < 0) {
                    throw new IllegalArgumentException("selected potion requires a target");
                }
                List<AbstractMonster> monsters = AbstractDungeon.getCurrRoom().monsters.monsters;
                if (a.targetIndex >= monsters.size()) {
                    throw new IllegalArgumentException("target index out of bounds: " + a.targetIndex);
                }
                po.use(monsters.get(a.targetIndex));
            } else {
                po.use(p);
            }
            for (AbstractRelic r : p.relics) {
                r.onUsePotion();
            }
        } else {
            if (!po.canDiscard()) {
                throw new IllegalArgumentException("selected potion cannot be discarded");
            }
        }
        AbstractDungeon.topPanel.destroyPotion(po.slot);
        return (use ? "used" : "discarded") + " potion: " + po.ID;
    }

    // ------------------------------------------------------------------ guards

    /** Same as CommunicationMod's isInDungeon(). */
    private static boolean inRun() {
        return CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY
                && AbstractDungeon.isPlayerInDungeon()
                && AbstractDungeon.currMapNode != null;
    }

    private static void requireInRun(String what) {
        if (!inRun()) {
            throw new IllegalStateException("cannot " + what + ": not in a run");
        }
    }

    private static void requireCombat(String what) {
        requireInRun(what);
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        if (room == null || room.phase != AbstractRoom.RoomPhase.COMBAT) {
            throw new IllegalStateException("cannot " + what + ": not in combat");
        }
        if (AbstractDungeon.isScreenUp) {
            throw new IllegalStateException("cannot " + what + ": a screen is up");
        }
    }
}
