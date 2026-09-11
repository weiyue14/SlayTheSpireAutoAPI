package stsapi;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.core.CardCrawlGame;

/**
 * Runs once per frame on the game thread (post CardCrawlGame.update), which
 * is the only safe place to mutate game state. HTTP threads only enqueue
 * actions; they are drained here. Same patch shape CommunicationMod uses.
 */
@SpirePatch(clz = CardCrawlGame.class, method = "update")
public class GameUpdatePatch {

    public static void Postfix(CardCrawlGame _instance) {
        try {
            SnapshotQueue.drainOnGameThread();
        } catch (Throwable ignored) {
            // never crash the game from snapshot capture
        }
        try {
            CheckpointQueue.drainOnGameThread();
        } catch (Throwable ignored) {
            // never crash the game from checkpoint capture/restore
        }
        try {
            ActionQueue.drainOnGameThread();
        } catch (Throwable ignored) {
            // never crash the game from the action queue
        }
    }
}
