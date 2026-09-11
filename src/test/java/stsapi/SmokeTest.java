package stsapi;

import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.EnemyMoveInfo;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Offline smoke test: fakes a rich game state on top of the stub classes,
 * starts the API server and exercises every endpoint end-to-end.
 * Not packaged into the mod jar.
 */
public class SmokeTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        fakeState();

        Config cfg = Config.load();
        cfg.host = "127.0.0.1";
        cfg.port = 18080;
        ApiServer.start(cfg);

        String base = "http://127.0.0.1:18080";

        String state = get(base + "/api/state?pretty=1");
        check(state.contains("\"schema_version\": 2"), "schema version");
        check(state.contains("\"stable\": true"), "stable decision state");
        check(state.contains("\"in_game\": true"), "in_game true");
        check(state.contains("\"class\": \"IRONCLAD\""), "class IRONCLAD");
        check(state.contains("\"hp\": 68"), "player hp");
        check(state.contains("\"energy\": 3"), "energy");
        check(state.contains("\"next_draw_order\""), "draw pile next order");
        check(state.contains("\"AscendersBane\""), "draw pile card");
        check(state.contains("\"move_adjusted_damage\": 14"), "intent adjusted damage");
        check(state.contains("\"move_hits\": 2"), "intent hits");
        check(state.contains("\"move_name\": \"咬\""), "move name");
        check(state.contains("\"last_move_id\": 1"), "last move id");
        check(state.contains("\"Strength\""), "power strength");
        check(state.contains("\"Fire Potion\""), "potion fire");
        check(state.contains("\"Burning Blood\""), "relic blood");
        check(state.contains("\"room_phase\": \"COMBAT\""), "room phase combat");
        check(state.contains("\"turn\": 3"), "turn");
        check(state.contains("\"seed\": -3047511808784702860"), "seed");
        check(state.contains("\"cards_played_this_turn\": 2"), "cards played counter");
        check(state.contains("\"attacks_played_this_turn\": 1"), "attacks played counter");
        check(state.contains("\"skills_played_this_turn\": 1"), "skills played counter");
        String combatJson = state.substring(state.indexOf("\"combat\""));
        check(combatJson.contains("\"cards_played_this_turn\": 2"),
                "combat block cards played counter");
        check(combatJson.contains("\"attacks_played_this_turn\": 1"),
                "combat block attacks played counter");
        check(combatJson.contains("\"skills_played_this_turn\": 1"),
                "combat block skills played counter");
        check(combatJson.contains("\"powers_played_this_combat\": 0"),
                "combat block powers played counter");
        check(state.contains("\"orb_slots\": 3"), "orb slots");
        check(state.contains("\"is_escaping\": false"), "monster escaping flag");
        check(state.contains("\"base_damage\": 6"), "card base damage");

        get(base + "/api/meta");
        get(base + "/api/screen");
        get(base + "/api/player");
        get(base + "/api/combat");
        get(base + "/api/monsters");
        get(base + "/api/hand");
        get(base + "/api/draw_pile");
        get(base + "/api/discard_pile");
        get(base + "/api/exhaust_pile");
        get(base + "/api/deck");
        get(base + "/api/potions");
        get(base + "/api/relics");
        get(base + "/api/map");
        ExecutorService snapshotClient = Executors.newSingleThreadExecutor();
        Future<String> snapshotRequest = snapshotClient.submit(() -> get(base + "/api/snapshot"));
        long snapshotDeadline = System.currentTimeMillis() + 2000L;
        while (SnapshotQueue.pendingCount() == 0 && System.currentTimeMillis() < snapshotDeadline) {
            Thread.sleep(5L);
        }
        check(SnapshotQueue.pendingCount() == 1, "snapshot queued for game thread");
        GameUpdatePatch.Postfix(null);
        String snapshot = snapshotRequest.get(3L, TimeUnit.SECONDS);
        snapshotClient.shutdownNow();
        check(snapshot.contains("\"available\":true"), "snapshot detects SaveStateMod");
        check(snapshot.contains("\"captured\":true"), "snapshot captured");
        check(snapshot.contains("\"source\":\"SaveStateMod\""), "snapshot source");
        check(snapshot.contains("\"encoded\":\"test-checkpoint\""), "snapshot encoded checkpoint");
        check(snapshot.contains("\"shuffleRng\":7"), "snapshot includes RNG state");

        ExecutorService checkpointClient = Executors.newSingleThreadExecutor();
        Future<String> saveRequest = checkpointClient.submit(() -> post(
                base + "/api/checkpoint",
                "{\"operation\":\"save\",\"slot\":\"smoke\"}",
                "application/json"));
        long checkpointDeadline = System.currentTimeMillis() + 2000L;
        while (CheckpointQueue.pendingCount() == 0
                && System.currentTimeMillis() < checkpointDeadline) {
            Thread.sleep(5L);
        }
        check(CheckpointQueue.pendingCount() == 1, "checkpoint save queued for game thread");
        GameUpdatePatch.Postfix(null);
        String saved = saveRequest.get(3L, TimeUnit.SECONDS);
        check(saved.contains("\"ok\":true"), "checkpoint save completed");

        Future<String> restoreRequest = checkpointClient.submit(() -> post(
                base + "/api/checkpoint",
                "{\"operation\":\"restore\",\"slot\":\"smoke\"}",
                "application/json"));
        checkpointDeadline = System.currentTimeMillis() + 2000L;
        while (CheckpointQueue.pendingCount() == 0
                && System.currentTimeMillis() < checkpointDeadline) {
            Thread.sleep(5L);
        }
        check(CheckpointQueue.pendingCount() == 1, "checkpoint restore queued for game thread");
        GameUpdatePatch.Postfix(null);
        String restored = restoreRequest.get(3L, TimeUnit.SECONDS);
        checkpointClient.shutdownNow();
        check(restored.contains("\"ok\":true"), "checkpoint restore completed");
        check(savestate.SaveState.loaded == 1, "SaveStateMod loadState invoked");
        get(base + "/api");
        get(base + "/");
        get(base + "/api/nope");

        int code404 = headCode(base + "/api/nope");
        check(code404 == 200, "unknown endpoint returns JSON body with 200 (error field)");
        String unknown = get(base + "/api/nope");
        check(unknown.contains("unknown endpoint"), "unknown endpoint error message");

        // draw pile order check: tail of internal list must be next_draw_order[0]
        String draw = get(base + "/api/draw_pile");
        String tailMarker = "\"AscendersBane\"";
        int idxInternal = draw.indexOf("\"internal_order\"");
        int idxNext = draw.indexOf("\"next_draw_order\"");
        check(idxNext >= 0 && idxInternal > idxNext, "draw pile field order");

        // --- action endpoint (executes on the game thread via GameUpdatePatch) ---
        String r1 = post(base + "/api/action",
                "{\"cmd\":\"play\",\"hand\":0,\"target\":0}", "application/json");
        check(r1.contains("\"queued\":true"), "action play queued (json)");
        GameUpdatePatch.Postfix(null); // simulate one game frame
        check(AbstractDungeon.actionManager.cardQueue.size() == 1, "play added to card queue");

        String r2 = post(base + "/api/action", "cmd=end", "application/x-www-form-urlencoded");
        check(r2.contains("\"queued\":true"), "action end queued (form)");
        GameUpdatePatch.Postfix(null);
        check(AbstractDungeon.overlayMenu.endTurnButton.disabled, "end turn triggered");

        String r3 = post(base + "/api/action",
                "{\"cmd\":\"raw\",\"text\":\"potion discard 0\"}", "application/json");
        check(r3.contains("\"queued\":true"), "raw potion discard queued");
        GameUpdatePatch.Postfix(null);
        check(AbstractDungeon.topPanel.destroyedPotionSlot == 0, "potion discarded");

        String r4 = post(base + "/api/action", "{\"cmd\":\"nope\"}", "application/json");
        check(r4.contains("unknown cmd"), "unknown cmd rejected");

        String q = get(base + "/api/action");
        check(q.contains("executed_total\":3"), "executed counter");
        check(q.contains("failed_total\":0"), "no failures");

        if (failures == 0) {
            System.out.println("ALL SMOKE TESTS PASSED");
            System.exit(0);
        } else {
            System.out.println(failures + " FAILURES");
            System.exit(1);
        }
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + what);
        if (!ok) failures++;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
        String body = s.hasNext() ? s.next() : "";
        is.close();
        System.out.println("GET " + url + " -> " + code + " (" + body.length() + " bytes)");
        return body;
    }

    private static int headCode(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        return conn.getResponseCode();
    }

    private static String post(String url, String body, String contentType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(b.length);
        OutputStream os = conn.getOutputStream();
        os.write(b);
        os.close();
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
        String resp = s.hasNext() ? s.next() : "";
        is.close();
        System.out.println("POST " + url + " [" + body + "] -> " + code);
        return resp;
    }

    // ---------------------------------------------------------------- fake state

    private static void fakeState() throws Exception {
        AbstractPlayer p = new AbstractPlayer();
        p.chosenClass = AbstractPlayer.PlayerClass.IRONCLAD;
        p.currentHealth = 68;
        p.maxHealth = 75;
        p.currentBlock = 5;
        p.gold = 99;
        p.damagedThisCombat = 2;
        p.potionSlots = 2;
        p.energy.energyMaster = 3;
        p.maxOrbs = 3;
        p.cardsPlayedThisTurn = 2;
        p.attacksPlayedThisTurn = 1;
        p.skillsPlayedThisTurn = 1;
        p.powersPlayedThisCombat = 0;
        AbstractDungeon.player = p;
        CardCrawlGame.mode = CardCrawlGame.GameMode.GAMEPLAY;
        AbstractDungeon.playerInDungeon = true;
        AbstractDungeon.actionManager = new GameActionManager();
        AbstractDungeon.actionManager.phase = GameActionManager.Phase.WAITING_ON_USER;

        AbstractDungeon.actNum = 1;
        AbstractDungeon.floorNum = 5;
        AbstractDungeon.ascensionLevel = 20;
        AbstractDungeon.bossKey = "The Guardian";
        AbstractDungeon.screen = AbstractDungeon.CurrentScreen.NONE;
        AbstractDungeon.isScreenUp = false;
        AbstractDungeon.firstRoomChosen = true;
        Settings.seed = -3047511808784702860L;
        Settings.hasRubyKey = false;
        Settings.hasEmeraldKey = false;
        Settings.hasSapphireKey = false;
        EnergyPanel.totalCount = 3;
        GameActionManager.turn = 3;
        GameActionManager.totalDiscardedThisTurn = 1;

        p.hand.group.add(card("Strike_R", "打击", AbstractCard.CardType.ATTACK, 1, 6));
        p.hand.group.add(card("Bash", "重击", AbstractCard.CardType.ATTACK, 2, 8));
        p.drawPile.group.add(card("Defend_R", "防御", AbstractCard.CardType.SKILL, 1, 5));
        p.drawPile.group.add(card("Strike_R", "打击", AbstractCard.CardType.ATTACK, 1, 6));
        p.drawPile.group.add(card("AscendersBane", "登天者之祸", AbstractCard.CardType.CURSE, -2, 0));
        p.discardPile.group.add(card("Cleave", "顺劈斩", AbstractCard.CardType.ATTACK, 1, 8));
        p.masterDeck.group.addAll(p.hand.group);
        p.masterDeck.group.addAll(p.drawPile.group);
        p.masterDeck.group.addAll(p.discardPile.group);

        AbstractPower strength = new AbstractPower();
        strength.ID = "Strength";
        strength.name = "力量";
        strength.amount = 3;
        strength.type = AbstractPower.PowerType.BUFF;
        p.powers.add(strength);

        AbstractPotion fire = new AbstractPotion();
        fire.ID = "Fire Potion";
        fire.name = "火焰药水";
        fire.isThrown = false;
        fire.slot = 0;
        fire.rarity = AbstractPotion.PotionRarity.COMMON;
        fire.potency = 20;
        fire.description = "造成 20 点伤害。";
        p.potions.add(fire);
        p.potions.add(new PotionSlot());

        AbstractRelic blood = new AbstractRelic();
        blood.relicId = "Burning Blood";
        blood.name = "燃烧之血";
        blood.counter = -1;
        p.relics.add(blood);

        AbstractRoom room = new AbstractRoom();
        room.phase = AbstractRoom.RoomPhase.COMBAT;
        room.isBattleOver = false;
        MonsterGroup mg = new MonsterGroup();

        AbstractMonster worm = new AbstractMonster();
        worm.id = "JawWorm";
        worm.name = "颚虫";
        worm.currentHealth = 40;
        worm.maxHealth = 44;
        worm.currentBlock = 0;
        worm.intent = AbstractMonster.Intent.ATTACK;
        worm.halfDead = false;
        worm.isEscaping = false;
        worm.moveHistory.add((byte) 1);
        worm.moveHistory.add((byte) 2);
        EnemyMoveInfo move = new EnemyMoveInfo();
        move.nextMove = 3;
        move.baseDamage = 11;
        move.multiplier = 2;
        move.isMultiDamage = true;
        setField(worm, "move", move);
        setField(worm, "intentDmg", 14);
        setField(worm, "moveName", "咬");
        mg.monsters.add(worm);

        AbstractMonster louse = new AbstractMonster();
        louse.id = "RedLouse";
        louse.name = "红虱";
        louse.currentHealth = 10;
        louse.maxHealth = 12;
        louse.intent = AbstractMonster.Intent.DEFEND;
        mg.monsters.add(louse);

        room.monsters = mg;
        AbstractDungeon.currRoom = room;

        MapRoomNode n1 = new MapRoomNode();
        n1.x = 1;
        n1.y = 0;
        MapRoomNode n2 = new MapRoomNode();
        n2.x = 0;
        n2.y = 1;
        MapEdge e = new MapEdge();
        e.srcX = 1;
        e.srcY = 0;
        e.dstX = 0;
        e.dstY = 1;
        List<MapEdge> edges = new ArrayList<>();
        edges.add(e);
        setField(n1, "edges", edges);
        setField(n2, "edges", new ArrayList<MapEdge>());
        ArrayList<MapRoomNode> layer0 = new ArrayList<>();
        layer0.add(n1);
        ArrayList<MapRoomNode> layer1 = new ArrayList<>();
        layer1.add(n2);
        ArrayList<ArrayList<MapRoomNode>> map = new ArrayList<>();
        map.add(layer0);
        map.add(layer1);
        AbstractDungeon.map = map;
        AbstractDungeon.currMapNode = n1;
    }

    private static AbstractCard card(String id, String name,
                                     AbstractCard.CardType type, int cost, int dmg) {
        AbstractCard c = new AbstractCard();
        c.cardID = id;
        c.name = name;
        c.uuid = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
        c.type = type;
        c.rarity = AbstractCard.CardRarity.BASIC;
        c.target = type == AbstractCard.CardType.ATTACK
                ? AbstractCard.CardTarget.ENEMY : AbstractCard.CardTarget.SELF;
        c.cost = cost;
        c.costForTurn = cost;
        c.damage = dmg;
        c.baseDamage = dmg;
        c.exhaust = false;
        c.isEthereal = false;
        c.misc = 0;
        c.price = 0;
        return c;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
