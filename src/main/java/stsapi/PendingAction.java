package stsapi;

/** One submitted game action, executed later on the game thread. */
final class PendingAction {

    /** play | end | potion_use | potion_discard */
    final String cmd;
    /** 0-based index into the hand; -1 when not applicable. */
    final int handIndex;
    /** 0-based index into the room's monster list; -1 = no target. */
    final int targetIndex;
    /** 0-based potion slot; -1 when not applicable. */
    final int potionSlot;
    final long submittedAt;

    PendingAction(String cmd, int handIndex, int targetIndex, int potionSlot) {
        this.cmd = cmd;
        this.handIndex = handIndex;
        this.targetIndex = targetIndex;
        this.potionSlot = potionSlot;
        this.submittedAt = System.currentTimeMillis();
    }

    String describe() {
        switch (cmd) {
            case "play":
                return "play hand[" + handIndex + "]"
                        + (targetIndex >= 0 ? " target[" + targetIndex + "]" : "");
            case "end":
                return "end";
            case "potion_use":
                return "potion use slot[" + potionSlot + "]"
                        + (targetIndex >= 0 ? " target[" + targetIndex + "]" : "");
            case "potion_discard":
                return "potion discard slot[" + potionSlot + "]";
            default:
                return cmd;
        }
    }
}
