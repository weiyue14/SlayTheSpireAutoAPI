package stsapi;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.EnemyMoveInfo;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Extracts a JSON-ready snapshot of the live game state.
 *
 * Field access policy:
 *  - Fields/methods also used by the well-known CommunicationMod
 *    (ForgottenArbiter) are accessed directly; they are verified against the
 *    real game.
 *  - Everything else (fields that vary between game versions or that are
 *    private, e.g. the monster "move"/"intentDmg" fields) is read defensively
 *    via reflection so a mismatch degrades to null instead of crashing.
 */
final class GameState {

    private GameState() {
    }

    // ------------------------------------------------------------------
    // endpoints
    // ------------------------------------------------------------------

    static Map<String, Object> fullState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p == null) return m;
        m.put("meta", metaBlock(p));
        m.put("screen", screenBlock());
        m.put("player", playerBlock(p));
        m.put("combat", combatBlock(p));
        m.put("map", mapPayload());
        return m;
    }

    static Map<String, Object> metaState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) m.put("meta", metaBlock(p));
        return m;
    }

    static Map<String, Object> screenState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) m.put("screen", screenBlock());
        return m;
    }

    static Map<String, Object> playerState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) m.put("player", playerBlock(p));
        return m;
    }

    static Map<String, Object> combatState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) {
            m.put("combat", combatBlock(p));
        } else {
            m.put("combat", Json.obj("active", false, "monsters", Json.arr()));
        }
        return m;
    }

    static Map<String, Object> monstersState() {
        Map<String, Object> m = envelope();
        m.put("in_combat", inCombat());
        m.put("monsters", monstersList());
        return m;
    }

    static Map<String, Object> handState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) m.put("hand", handBlock(p));
        return m;
    }

    static Map<String, Object> drawPileState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) m.put("draw_pile", drawPileBlock(p));
        return m;
    }

    static Map<String, Object> discardPileState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) m.put("discard_pile", pileBlock(p.discardPile));
        return m;
    }

    static Map<String, Object> exhaustPileState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) m.put("exhaust_pile", pileBlock(p.exhaustPile));
        return m;
    }

    static Map<String, Object> deckState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) m.put("deck", cardsList(safeCopy(p.masterDeck.group)));
        return m;
    }

    static Map<String, Object> potionsState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) {
            m.put("potion_slots", getField(p, "potionSlots"));
            m.put("potions", potionsList(p));
        }
        return m;
    }

    static Map<String, Object> relicsState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) m.put("relics", relicsList(p));
        return m;
    }

    static Map<String, Object> mapState() {
        Map<String, Object> m = envelope();
        AbstractPlayer p = player();
        if (p != null) m.put("map", mapPayload());
        return m;
    }

    // ------------------------------------------------------------------
    // blocks
    // ------------------------------------------------------------------

    private static Map<String, Object> envelope() {
        Map<String, Object> m = Json.obj();
        m.put("schema_version", 2);
        m.put("mod_version", StsApiMod.VERSION);
        m.put("timestamp", System.currentTimeMillis());
        m.put("in_game", player() != null);
        m.put("stable", isStableDecisionState());
        return m;
    }

    private static Map<String, Object> metaBlock(AbstractPlayer p) {
        Map<String, Object> j = Json.obj();
        j.put("class", p.chosenClass.name());
        j.put("act", AbstractDungeon.actNum);
        j.put("floor", AbstractDungeon.floorNum);
        j.put("ascension", AbstractDungeon.ascensionLevel);
        j.put("act_boss", AbstractDungeon.bossKey);
        j.put("seed", Settings.seed);
        j.put("keys", Json.obj(
                "ruby", Settings.hasRubyKey,
                "emerald", Settings.hasEmeraldKey,
                "sapphire", Settings.hasSapphireKey));
        return j;
    }

    private static Map<String, Object> screenBlock() {
        Map<String, Object> j = Json.obj();
        try {
            j.put("name", AbstractDungeon.screen.name());
        } catch (Throwable t) {
            j.put("name", null);
        }
        try {
            j.put("is_up", AbstractDungeon.isScreenUp);
        } catch (Throwable t) {
            j.put("is_up", null);
        }
        AbstractRoom room = currentRoom();
        if (room != null) {
            j.put("room_type", room.getClass().getSimpleName());
            try {
                j.put("room_phase", room.phase.name());
            } catch (Throwable t) {
                j.put("room_phase", null);
            }
            try {
                j.put("battle_over", room.isBattleOver);
            } catch (Throwable t) {
                j.put("battle_over", null);
            }
        } else {
            j.put("room_type", null);
            j.put("room_phase", null);
            j.put("battle_over", null);
        }
        j.put("action_phase", actionPhase());
        return j;
    }

    private static Map<String, Object> playerBlock(AbstractPlayer p) {
        Map<String, Object> j = Json.obj();
        j.put("class", p.chosenClass.name());
        j.put("hp", p.currentHealth);
        j.put("max_hp", p.maxHealth);
        j.put("block", p.currentBlock);
        j.put("gold", p.gold);
        try {
            j.put("energy", EnergyPanel.totalCount);
        } catch (Throwable t) {
            j.put("energy", null);
        }
        j.put("energy_master", reflectPath(p, "energy", "energyMaster"));
        j.put("potion_slots", getField(p, "potionSlots"));
        // Some game versions store counters on the player, while the real
        // action manager stores played cards as lists. Always expose numbers.
        j.put("orb_slots", getField(p, "maxOrbs"));
        Object am = null;
        try {
            am = AbstractDungeon.actionManager;
        } catch (Throwable ignored) {
        }
        j.put("cards_played_this_turn", numericCounter(
                getField(p, "cardsPlayedThisTurn"), getField(am, "cardsPlayedThisTurn"), null));
        j.put("attacks_played_this_turn", numericCounter(
                getField(p, "attacksPlayedThisTurn"), null,
                countCardsByType(getField(am, "cardsPlayedThisTurn"), AbstractCard.CardType.ATTACK)));
        j.put("skills_played_this_turn", numericCounter(
                getField(p, "skillsPlayedThisTurn"), null,
                countCardsByType(getField(am, "cardsPlayedThisTurn"), AbstractCard.CardType.SKILL)));
        j.put("powers_played_this_combat", numericCounter(
                getField(p, "powersPlayedThisCombat"), null,
                countCardsByType(getField(am, "cardsPlayedThisCombat"), AbstractCard.CardType.POWER)));
        j.put("lightning_channeled_this_combat",
                firstFound(am, p, "lightningChanneledThisCombat", "totalLightningStrikesThisCombat"));
        j.put("frost_channeled_this_combat",
                firstFound(am, p, "frostChanneledThisCombat", "totalBlizzardsThisCombat"));
        j.put("powers", powersList(p));
        j.put("orbs", orbsList(p));
        j.put("potions", potionsList(p));
        j.put("relics", relicsList(p));
        j.put("hand", handBlock(p));
        j.put("draw_pile", drawPileBlock(p));
        j.put("discard_pile", pileBlock(p.discardPile));
        j.put("exhaust_pile", pileBlock(p.exhaustPile));
        j.put("master_deck", cardsList(safeCopy(p.masterDeck.group)));
        return j;
    }

    private static Map<String, Object> combatBlock(AbstractPlayer p) {
        Map<String, Object> j = Json.obj();
        j.put("active", inCombat());
        Object am = null;
        try {
            am = AbstractDungeon.actionManager;
        } catch (Throwable ignored) {
        }
        j.put("cards_played_this_turn", numericCounter(
                getField(p, "cardsPlayedThisTurn"), getField(am, "cardsPlayedThisTurn"), null));
        j.put("attacks_played_this_turn", numericCounter(
                getField(p, "attacksPlayedThisTurn"), null,
                countCardsByType(getField(am, "cardsPlayedThisTurn"), AbstractCard.CardType.ATTACK)));
        j.put("skills_played_this_turn", numericCounter(
                getField(p, "skillsPlayedThisTurn"), null,
                countCardsByType(getField(am, "cardsPlayedThisTurn"), AbstractCard.CardType.SKILL)));
        j.put("powers_played_this_combat", numericCounter(
                getField(p, "powersPlayedThisCombat"), null,
                countCardsByType(getField(am, "cardsPlayedThisCombat"), AbstractCard.CardType.POWER)));
        j.put("lightning_channeled_this_combat",
                firstFound(am, p, "lightningChanneledThisCombat", "totalLightningStrikesThisCombat"));
        j.put("frost_channeled_this_combat",
                firstFound(am, p, "frostChanneledThisCombat", "totalBlizzardsThisCombat"));
        j.put("emotion_chip_pending",
                firstFound(am, p, "emotionChipPending", "emotionChipTriggered"));
        j.put("rng", combatRngBlock());
        try {
            j.put("turn", GameActionManager.turn);
        } catch (Throwable t) {
            j.put("turn", null);
        }
        try {
            j.put("cards_discarded_this_turn", GameActionManager.totalDiscardedThisTurn);
        } catch (Throwable t) {
            j.put("cards_discarded_this_turn", null);
        }
        try {
            j.put("times_damaged", p.damagedThisCombat);
        } catch (Throwable t) {
            j.put("times_damaged", null);
        }
        try {
            if (AbstractDungeon.actionManager != null
                    && AbstractDungeon.actionManager.currentAction != null) {
                j.put("current_action", AbstractDungeon.actionManager.currentAction.getClass().getSimpleName());
            } else {
                j.put("current_action", null);
            }
        } catch (Throwable t) {
            j.put("current_action", null);
        }
        j.put("monsters", monstersList());
        j.put("limbo", cardsList(safeCopy(p.limbo.group)));
        j.put("card_in_play", p.cardInUse == null ? null : cardJson(p.cardInUse));
        return j;
    }

    /** RNG streams that can affect future combat outcomes from this decision point. */
    private static Map<String, Object> combatRngBlock() {
        Map<String, Object> j = Json.obj();
        j.put("ai", rngCounter("aiRng"));
        j.put("shuffle", rngCounter("shuffleRng"));
        j.put("card_random", rngCounter("cardRandomRng"));
        j.put("misc", rngCounter("miscRng"));
        j.put("potion", rngCounter("potionRng"));
        return j;
    }

    private static Object rngCounter(String fieldName) {
        Object rng = getStaticField(AbstractDungeon.class, fieldName);
        Object counter = getField(rng, "counter");
        return counter instanceof Number ? ((Number) counter).intValue() : null;
    }

    private static Map<String, Object> handBlock(AbstractPlayer p) {
        Map<String, Object> j = Json.obj();
        List<AbstractCard> cards = safeCopy(p.hand.group);
        j.put("count", cards.size());
        // group order is roughly left-to-right on screen once animations settle
        j.put("cards", cardsList(cards));
        j.put("fingerprint", pileFingerprint(cards));
        return j;
    }

    /**
     * Draw pile. The game draws from the TAIL of the internal list, so:
     *  - next_draw_order[0] is the next card that will be drawn;
     *  - internal_order is the raw group order (same order CommunicationMod
     *    reports), whose LAST element is drawn next.
     * fingerprint changes whenever the order changes (i.e. a shuffle happened).
     */
    private static Map<String, Object> drawPileBlock(AbstractPlayer p) {
        Map<String, Object> j = Json.obj();
        List<AbstractCard> cards = safeCopy(p.drawPile.group);
        j.put("count", cards.size());
        List<Object> nextOrder = Json.arr();
        for (int i = cards.size() - 1; i >= 0; i--) {
            nextOrder.add(cardJson(cards.get(i)));
        }
        j.put("next_draw_order", nextOrder);
        List<Object> internal = Json.arr();
        for (AbstractCard c : cards) {
            internal.add(c.uuid.toString());
        }
        j.put("internal_order", internal);
        j.put("fingerprint", pileFingerprint(cards));
        return j;
    }

    /** discard/exhaust pile: raw internal order, tail = most recently added. */
    private static Map<String, Object> pileBlock(CardGroup g) {
        Map<String, Object> j = Json.obj();
        List<AbstractCard> cards = g == null ? new ArrayList<AbstractCard>() : safeCopy(g.group);
        j.put("count", cards.size());
        j.put("cards", cardsList(cards));
        j.put("fingerprint", pileFingerprint(cards));
        return j;
    }

    // ------------------------------------------------------------------
    // converters
    // ------------------------------------------------------------------

    private static Map<String, Object> cardJson(AbstractCard c) {
        Map<String, Object> j = Json.obj();
        j.put("id", c.cardID);
        j.put("name", c.name == null || c.name.trim().isEmpty() ? c.cardID : c.name);
        j.put("uuid", c.uuid.toString());
        j.put("type", c.type.name());
        j.put("rarity", c.rarity.name());
        j.put("cost", c.costForTurn); // -1 = X cost, -2 = unplayable
        j.put("base_cost", getField(c, "cost"));
        j.put("upgrades", c.timesUpgraded);
        j.put("upgraded", getField(c, "upgraded"));
        j.put("has_target", c.target == AbstractCard.CardTarget.ENEMY
                || c.target == AbstractCard.CardTarget.SELF_AND_ENEMY);
        j.put("exhausts", c.exhaust);
        j.put("ethereal", c.isEthereal);
        j.put("misc", c.misc);
        j.put("price", c.price); // non-zero only in shops
        j.put("is_playable", isPlayable(c));
        // computed display values (applyPowers-updated in hand; base elsewhere)
        j.put("damage", getField(c, "damage"));
        j.put("block", getField(c, "block"));
        j.put("magic_number", getField(c, "magicNumber"));
        // base values, needed by battle-agent for Claw / Steam Barrier reconstruction
        j.put("base_damage", getField(c, "baseDamage"));
        j.put("base_block", getField(c, "baseBlock"));
        Object v;
        if ((v = getField(c, "isInnate")) != null) j.put("innate", v);
        if ((v = getField(c, "selfRetain")) != null) j.put("retain", v);
        if ((v = getField(c, "freeToPlayOnce")) != null) j.put("free_to_play_once", v);
        return j;
    }

    private static Map<String, Object> monsterJson(AbstractMonster m) {
        Map<String, Object> j = Json.obj();
        j.put("id", m.id);
        j.put("name", m.name);
        j.put("hp", m.currentHealth);
        j.put("max_hp", m.maxHealth);
        j.put("block", m.currentBlock);
        j.put("half_dead", m.halfDead);
        j.put("is_gone", m.isDeadOrEscaped());
        j.put("is_escaping", getField(m, "isEscaping"));
        j.put("powers", powersList(m));
        j.put("intent", intentBlock(m));
        if (m.moveHistory != null) {
            List<Object> hist = Json.arr();
            for (byte b : m.moveHistory) hist.add((int) b);
            j.put("move_history", hist);
            if (m.moveHistory.size() >= 2) {
                j.put("last_move_id", (int) m.moveHistory.get(m.moveHistory.size() - 2));
                if (m.moveHistory.size() >= 3) {
                    j.put("second_last_move_id", (int) m.moveHistory.get(m.moveHistory.size() - 3));
                }
            }
        }
        return j;
    }

    /**
     * Intent block, mirroring CommunicationMod's battle-tested logic:
     *  - Runic Dome hides intents (reported as NONE + hidden=true);
     *  - private fields "move" (EnemyMoveInfo) and "intentDmg" are read via
     *    reflection: move_base_damage = raw, move_adjusted_damage = what the
     *    intent icon actually shows (after monster powers like Strength),
     *    move_hits = hits per turn (multiplier only when isMultiDamage).
     */
    private static Map<String, Object> intentBlock(AbstractMonster m) {
        Map<String, Object> j = Json.obj();
        boolean runicDome = false;
        try {
            AbstractPlayer p = player();
            runicDome = p != null && p.hasRelic("Runic Dome");
        } catch (Throwable ignored) {
        }
        if (runicDome) {
            j.put("intent", "NONE");
            j.put("hidden", true);
            return j;
        }
        try {
            j.put("intent", m.intent.name());
        } catch (Throwable t) {
            j.put("intent", null);
        }
        j.put("hidden", false);
        Object move = getField(m, "move");
        if (move instanceof EnemyMoveInfo) {
            EnemyMoveInfo info = (EnemyMoveInfo) move;
            j.put("move_id", (int) info.nextMove);
            j.put("move_base_damage", info.baseDamage);
            Object intentDmg = getField(m, "intentDmg");
            int adjusted = info.baseDamage;
            if (info.baseDamage > 0 && intentDmg instanceof Integer) {
                adjusted = (Integer) intentDmg;
            }
            j.put("move_adjusted_damage", adjusted);
            j.put("move_hits", info.isMultiDamage ? info.multiplier : 1);
        }
        Object moveName = getField(m, "moveName");
        if (moveName != null) j.put("move_name", String.valueOf(moveName));
        return j;
    }

    private static List<Object> powersList(AbstractCreature c) {
        List<Object> out = Json.arr();
        if (c == null || c.powers == null) return out;
        for (AbstractPower pw : c.powers) {
            Map<String, Object> j = Json.obj();
            j.put("id", pw.ID);
            j.put("name", pw.name);
            j.put("amount", pw.amount);
            Object type = getField(pw, "type");
            j.put("type", type == null ? null : String.valueOf(type));
            Object justApplied = getField(pw, "justApplied");
            if (justApplied != null) j.put("just_applied", justApplied);
            Object damage = getField(pw, "damage");
            if (damage != null) j.put("damage", damage);
            Object misc = firstFound(
                    pw, null, "hpLoss", "energyGainAmount", "cardsDoubledThisTurn");
            if (misc != null) j.put("misc", misc);
            out.add(j);
        }
        return out;
    }

    private static List<Object> relicsList(AbstractPlayer p) {
        List<Object> out = Json.arr();
        if (p.relics == null) return out;
        for (AbstractRelic r : p.relics) {
            out.add(Json.obj("id", r.relicId, "name", r.name, "counter", r.counter));
        }
        return out;
    }

    private static List<Object> potionsList(AbstractPlayer p) {
        List<Object> out = Json.arr();
        if (p.potions == null) return out;
        for (int i = 0; i < p.potions.size(); i++) {
            AbstractPotion po = p.potions.get(i);
            out.add(potionJson(po, i));
        }
        return out;
    }

    private static Map<String, Object> potionJson(AbstractPotion po, int slot) {
        Map<String, Object> j = Json.obj();
        boolean isSlot = po instanceof PotionSlot;
        j.put("slot", slot);
        j.put("is_slot", isSlot);
        j.put("id", po.ID);
        j.put("name", po.name);
        boolean canUse = false;
        boolean canDiscard = false;
        if (!isSlot) {
            try {
                canUse = po.canUse();
            } catch (Throwable ignored) {
            }
            try {
                canDiscard = po.canDiscard();
            } catch (Throwable ignored) {
            }
        }
        j.put("can_use", canUse);
        j.put("can_discard", canDiscard);
        j.put("requires_target", po.isThrown); // thrown potions need a target
        Object v;
        if ((v = getField(po, "rarity")) != null) j.put("rarity", String.valueOf(v));
        if ((v = getField(po, "potency")) != null) j.put("potency", v);
        if ((v = getField(po, "description")) != null) j.put("description", String.valueOf(v));
        return j;
    }

    private static List<Object> orbsList(AbstractPlayer p) {
        List<Object> out = Json.arr();
        if (p.orbs == null) return out;
        for (AbstractOrb o : p.orbs) {
            out.add(Json.obj(
                    "id", o.ID,
                    "name", o.name,
                    "evoke_amount", o.evokeAmount,
                    "passive_amount", o.passiveAmount));
        }
        return out;
    }

    private static List<Object> monstersList() {
        List<Object> out = Json.arr();
        AbstractRoom room = currentRoom();
        if (room == null || room.monsters == null || room.monsters.monsters == null) return out;
        for (AbstractMonster m : room.monsters.monsters) {
            out.add(monsterJson(m));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // map
    // ------------------------------------------------------------------

    private static Object mapPayload() {
        Map<String, Object> j = Json.obj();
        MapRoomNode cur = null;
        try {
            cur = AbstractDungeon.getCurrMapNode();
        } catch (Throwable ignored) {
        }
        j.put("current_node", cur == null ? null : mapNodeJson(cur, false));
        try {
            j.put("first_node_chosen", AbstractDungeon.firstRoomChosen);
        } catch (Throwable t) {
            j.put("first_node_chosen", null);
        }
        j.put("nodes", mapNodeList());
        return j;
    }

    private static List<Object> mapNodeList() {
        List<Object> out = Json.arr();
        try {
            if (AbstractDungeon.map == null) return out;
            for (List<MapRoomNode> layer : AbstractDungeon.map) {
                if (layer == null) continue;
                for (MapRoomNode node : layer) {
                    if (node == null || !node.hasEdges()) continue;
                    out.add(mapNodeJson(node, true));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Map<String, Object> mapNodeJson(MapRoomNode node, boolean withChildren) {
        Map<String, Object> j = Json.obj();
        j.put("x", node.x);
        j.put("y", node.y);
        try {
            j.put("symbol", node.getRoomSymbol(true));
        } catch (Throwable t) {
            j.put("symbol", null);
        }
        if (withChildren) {
            List<Object> children = Json.arr();
            try {
                for (MapEdge edge : node.getEdges()) {
                    if (edge.srcX == node.x && edge.srcY == node.y) {
                        children.add(Json.obj("x", edge.dstX, "y", edge.dstY));
                    }
                }
            } catch (Throwable ignored) {
            }
            j.put("children", children); // nodes reachable from here
        }
        return j;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static AbstractPlayer player() {
        try {
            return AbstractDungeon.player;
        } catch (Throwable t) {
            return null;
        }
    }

    private static AbstractRoom currentRoom() {
        try {
            return AbstractDungeon.getCurrRoom();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean inCombat() {
        AbstractRoom room = currentRoom();
        try {
            return room != null && room.phase == AbstractRoom.RoomPhase.COMBAT;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String actionPhase() {
        try {
            if (AbstractDungeon.actionManager != null) {
                return String.valueOf(AbstractDungeon.actionManager.phase);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** True only after queued game actions have settled at a player decision point. */
    static boolean isStableDecisionState() {
        Object am;
        try {
            am = AbstractDungeon.actionManager;
        } catch (Throwable t) {
            return false;
        }
        if (am == null || !"WAITING_ON_USER".equals(String.valueOf(getField(am, "phase")))) {
            return false;
        }
        if (getField(am, "currentAction") != null) return false;
        return collectionIsEmpty(getField(am, "actions"))
                && collectionIsEmpty(getField(am, "preTurnActions"))
                && collectionIsEmpty(getField(am, "cardQueue"));
    }

    private static boolean isPlayable(AbstractCard c) {
        try {
            if (AbstractDungeon.getMonsters() == null) return false;
            return c.canUse(AbstractDungeon.player, null);
        } catch (Throwable t) {
            return false;
        }
    }

    private static List<AbstractCard> safeCopy(List<AbstractCard> src) {
        if (src == null) return new ArrayList<AbstractCard>();
        return new ArrayList<AbstractCard>(src); // may throw CME mid-copy; caller retries
    }

    private static Object numericCounter(Object preferred, Object fallback, Integer derived) {
        if (preferred instanceof Number) return ((Number) preferred).intValue();
        if (fallback instanceof Number) return ((Number) fallback).intValue();
        if (fallback instanceof Collection<?>) return ((Collection<?>) fallback).size();
        return derived;
    }

    private static Integer countCardsByType(Object value, AbstractCard.CardType type) {
        if (!(value instanceof Iterable<?>)) return null;
        int count = 0;
        for (Object item : (Iterable<?>) value) {
            if (item instanceof AbstractCard && ((AbstractCard) item).type == type) count++;
        }
        return count;
    }

    private static boolean collectionIsEmpty(Object value) {
        return !(value instanceof Collection<?>) || ((Collection<?>) value).isEmpty();
    }

    private static List<Object> cardsList(List<AbstractCard> cards) {
        List<Object> out = Json.arr();
        for (AbstractCard c : cards) {
            out.add(cardJson(c));
        }
        return out;
    }

    /** Stable hash of pile order (uuids) so clients can detect shuffles. */
    private static String pileFingerprint(List<AbstractCard> cards) {
        long h = 1125899906842597L;
        for (AbstractCard c : cards) {
            h = 31L * h + c.uuid.hashCode();
        }
        return Long.toHexString(h);
    }

    /** Reflectively reads a field (searching superclasses); null on any failure. */
    private static Object getField(Object target, String name) {
        if (target == null) return null;
        try {
            Class<?> c = target.getClass();
            Field f = null;
            while (c != null) {
                try {
                    f = c.getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Reflectively reads a static field (searching superclasses). */
    private static Object getStaticField(Class<?> type, String name) {
        try {
            Class<?> c = type;
            Field f = null;
            while (c != null) {
                try {
                    f = c.getDeclaredField(name);
                    break;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Walks a reflective path like player -> "energy" -> "energyMaster". */
    private static Object reflectPath(Object root, String... names) {
        Object cur = root;
        for (String n : names) {
            cur = getField(cur, n);
            if (cur == null) return null;
        }
        return cur;
    }

    /** First non-null reflective hit of the given field names on either object. */
    private static Object firstFound(Object a, Object b, String... names) {
        for (String n : names) {
            Object v = getField(a, n);
            if (v != null) return v;
            v = getField(b, n);
            if (v != null) return v;
        }
        return null;
    }
}
