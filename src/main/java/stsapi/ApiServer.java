package stsapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * Local HTTP server exposing read-only game state as JSON.
 * Runs requests on a single daemon thread so concurrent requests never race
 * each other, and every handler is wrapped in retries + a top-level catch so
 * the game can never crash because of an API request.
 */
final class ApiServer {

    private static HttpServer server;
    private static ExecutorService pool;
    private static volatile Config currentConfig;

    private ApiServer() {
    }

    static void start(Config cfg) throws IOException {
        currentConfig = cfg;
        server = HttpServer.create(new InetSocketAddress(cfg.host, cfg.port), 0);
        server.createContext("/", ApiServer::route);
        ThreadFactory tf = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "slaythespire-auto-api");
                t.setDaemon(true);
                return t;
            }
        };
        pool = Executors.newSingleThreadExecutor(tf);
        server.setExecutor(pool);
        server.start();
        StsApiMod.logger.info("SlayTheSpire Auto API: listening on http://" + cfg.host + ":" + cfg.port + "/api/state");
    }

    private static void route(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = normalize(ex.getRequestURI().getPath());
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            boolean pretty = "1".equals(q.get("pretty")) || "true".equalsIgnoreCase(q.get("pretty"));
            boolean headOnly = "HEAD".equalsIgnoreCase(method);
            boolean isGet = "GET".equalsIgnoreCase(method);
            boolean isPost = "POST".equalsIgnoreCase(method);
            if (path.equals("/api/action")) {
                if (isGet || headOnly) {
                    send(ex, 200, Json.write(ActionQueue.status(), pretty), headOnly);
                    return;
                }
                if (isPost) {
                    send(ex, 200, Json.write(handleActionPost(ex), pretty), false);
                    return;
                }
                send(ex, 405, Json.write(Json.obj("error", "use GET (queue status) or POST (submit action)"), pretty), headOnly);
                return;
            }
            if (path.equals("/api/checkpoint")) {
                if (isPost) {
                    send(ex, 200, Json.write(handleCheckpointPost(ex), pretty), false);
                    return;
                }
                send(ex, 405, Json.write(Json.obj(
                        "error", "checkpoint requires POST",
                        "usage", "{\\\"operation\\\":\\\"save\\\",\\\"slot\\\":\\\"depth-0\\\"}"), pretty), headOnly);
                return;
            }
            if (!isGet && !headOnly) {
                send(ex, 405, Json.write(Json.obj("error", "read-only API; only /api/action accepts POST"), pretty), headOnly);
                return;
            }
            Object body = dispatch(path);
            send(ex, 200, Json.write(body, pretty), headOnly);
        } catch (Throwable t) {
            try {
                send(ex, 500, Json.write(
                        Json.obj("error", String.valueOf(t), "hint", "state was changing mid-read, try again"),
                        false), false);
            } catch (Throwable ignored) {
                // nothing else we can do
            }
        } finally {
            try {
                ex.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String normalize(String p) {
        if (p == null || p.isEmpty()) return "/";
        if (p.length() > 1 && p.endsWith("/")) return p.substring(0, p.length() - 1);
        return p;
    }

    private static Object dispatch(String path) {
        switch (path) {
            case "/":
            case "/api":
                return endpoints();
            case "/api/snapshot": return SnapshotQueue.capture();
            case "/api/state": return snapshot(GameState::fullState);
            case "/api/meta": return snapshot(GameState::metaState);
            case "/api/screen": return snapshot(GameState::screenState);
            case "/api/player": return snapshot(GameState::playerState);
            case "/api/combat": return snapshot(GameState::combatState);
            case "/api/monsters": return snapshot(GameState::monstersState);
            case "/api/hand": return snapshot(GameState::handState);
            case "/api/draw_pile": return snapshot(GameState::drawPileState);
            case "/api/discard_pile": return snapshot(GameState::discardPileState);
            case "/api/exhaust_pile": return snapshot(GameState::exhaustPileState);
            case "/api/deck": return snapshot(GameState::deckState);
            case "/api/potions": return snapshot(GameState::potionsState);
            case "/api/relics": return snapshot(GameState::relicsState);
            case "/api/map": return snapshot(GameState::mapState);
            default:
                return Json.obj("error", "unknown endpoint: " + path, "endpoints", endpointList());
        }
    }

    /**
     * Runs a state snapshot with retries: the game mutates its lists on the
     * render thread, so a read can rarely hit a mid-update window. Retrying
     * a few milliseconds later almost always succeeds.
     */
    private static Object snapshot(Supplier<Object> fn) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return fn.get();
            } catch (RuntimeException e) {
                last = e;
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last;
    }

    private static Object endpoints() {
        return Json.obj(
                "mod_version", StsApiMod.VERSION,
                "endpoints", endpointList(),
                "usage", "poll /api/state for observations; GET /api/snapshot for an exact SaveStateMod checkpoint; POST /api/checkpoint for black-box save/restore; POST /api/action executes legal play/end/potion actions on the game thread");
    }

    private static Object endpointList() {
        return Json.arr(
                Json.obj("path", "/api/state", "desc", "everything below in one call"),
                Json.obj("path", "/api/snapshot", "desc", "game-thread SaveStateMod checkpoint including all RNG streams"),
                Json.obj("path", "/api/checkpoint", "desc", "POST save/restore/clear a complete SaveStateMod state slot on the game thread"),
                Json.obj("path", "/api/meta", "desc", "class, act, floor, ascension, seed, keys, gold"),
                Json.obj("path", "/api/screen", "desc", "current screen, room type/phase, action phase"),
                Json.obj("path", "/api/player", "desc", "hp, block, energy, powers, orbs, piles, potions, relics"),
                Json.obj("path", "/api/combat", "desc", "combat flag, turn, monsters, limbo, card in play"),
                Json.obj("path", "/api/monsters", "desc", "enemies: hp, block, powers, intents, move history"),
                Json.obj("path", "/api/hand", "desc", "hand cards"),
                Json.obj("path", "/api/draw_pile", "desc", "draw pile with true next-draw order + fingerprint"),
                Json.obj("path", "/api/discard_pile", "desc", "discard pile"),
                Json.obj("path", "/api/exhaust_pile", "desc", "exhaust pile"),
                Json.obj("path", "/api/deck", "desc", "master deck"),
                Json.obj("path", "/api/potions", "desc", "potions with slots"),
                Json.obj("path", "/api/relics", "desc", "relics with counters"),
                Json.obj("path", "/api/map", "desc", "map nodes, symbols, edges"),
                Json.obj("path", "/api/action", "desc", "GET: queue status; POST: submit play/end/potion_use/potion_discard (or raw \"play 1 0\")"));
    }

    private static Object handleCheckpointPost(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        String operation = "";
        String slot = "";
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && contentType.toLowerCase().contains("json")
                && body.trim().startsWith("{")) {
            Object parsed = JsonReader.parse(body);
            if (parsed instanceof Map) {
                Map<?, ?> json = (Map<?, ?>) parsed;
                operation = JsonReader.getStr(json, "operation",
                        JsonReader.getStr(json, "cmd", ""));
                slot = JsonReader.getStr(json, "slot", "");
            }
        } else {
            Map<String, String> form = parseQuery(body);
            operation = form.getOrDefault("operation", form.getOrDefault("cmd", ""));
            slot = form.getOrDefault("slot", "");
        }
        if (!operation.matches("save|restore|clear")) {
            return Json.obj("ok", false,
                    "error", "operation must be save, restore, or clear");
        }
        if (!slot.matches("[A-Za-z0-9._-]{1,64}")) {
            return Json.obj("ok", false,
                    "error", "slot must contain 1-64 ASCII letters, digits, dot, underscore, or dash");
        }
        return CheckpointQueue.request(operation, slot);
    }

    // ------------------------------------------------------------------ actions

    /**
     * POST /api/action body formats (either works):
     *   JSON:   {"cmd":"play","hand":0,"target":0} | {"cmd":"end"}
     *           {"cmd":"potion_use","slot":0,"target":0} | {"cmd":"potion_discard","slot":0}
     *           {"cmd":"raw","text":"play 1 0"}   (CommunicationMod/battle-agent wording, 1-based card)
     *   or form: cmd=play&hand=0&target=0
     * Actions run on the game thread at the next frame; results are visible
     * on GET /api/action.
     */
    private static Object handleActionPost(HttpExchange ex) throws IOException {
        Config cfg = currentConfig;
        if (cfg != null && !cfg.actions) {
            return Json.obj("error", "actions disabled by config (actions=false)", "queued", false);
        }
        String body = readBody(ex);
        Map<String, String> form = null;
        Map<?, ?> json = null;
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null && contentType.toLowerCase().contains("json") && body.trim().startsWith("{")) {
            Object parsed = JsonReader.parse(body);
            if (parsed instanceof Map) {
                json = (Map<?, ?>) parsed;
            }
        } else {
            form = parseQuery(body);
        }

        String cmd;
        String raw = null;
        int hand = -1;
        int target = -1;
        int slot = -1;
        if (json != null) {
            cmd = JsonReader.getStr(json, "cmd", "");
            raw = JsonReader.getStr(json, "text", null);
            hand = pick(json, "hand", "hand_index", -1);
            target = pick(json, "target", "target_index", -1);
            slot = pick(json, "slot", "potion_slot", -1);
        } else if (form != null) {
            cmd = form.getOrDefault("cmd", "");
            raw = form.getOrDefault("text", null);
            hand = pick(form, "hand", "hand_index", -1);
            target = pick(form, "target", "target_index", -1);
            slot = pick(form, "slot", "potion_slot", -1);
        } else {
            return Json.obj("error", "empty or unrecognized body", "queued", false);
        }

        PendingAction action = buildAction(cmd, raw, hand, target, slot);
        ActionQueue.submit(action);
        return Json.obj(
                "queued", true,
                "cmd", action.describe(),
                "pending", ActionQueue.pending(),
                "hint", "runs on the next game frame; check GET /api/action for results");
    }

    private static int pick(Map<?, ?> m, String k1, String k2, int dflt) {
        int v = JsonReader.getInt(m, k1, Integer.MIN_VALUE);
        if (v != Integer.MIN_VALUE) return v;
        return JsonReader.getInt(m, k2, dflt);
    }

    private static PendingAction buildAction(String cmd, String raw, int hand, int target, int slot) {
        if ("raw".equals(cmd) && raw != null) {
            // CommunicationMod wording: play CARD(1-based) [TARGET(0-based)] |
            // potion use|discard SLOT [TARGET] | end
            String[] t = raw.trim().toLowerCase().split("\\s+");
            if (t.length == 0) throw new IllegalArgumentException("empty raw command");
            switch (t[0]) {
                case "play": {
                    if (t.length < 2) throw new IllegalArgumentException("play needs a card index");
                    int cardIdx = Integer.parseInt(t[1]);
                    if (cardIdx == 0) cardIdx = 10; // CommunicationMod quirk
                    int tgt = t.length >= 3 ? Integer.parseInt(t[2]) : -1;
                    return new PendingAction("play", cardIdx - 1, tgt, -1);
                }
                case "end":
                    return new PendingAction("end", -1, -1, -1);
                case "potion": {
                    if (t.length < 3) throw new IllegalArgumentException("potion needs use|discard and a slot");
                    boolean use = "use".equals(t[1]);
                    int s = Integer.parseInt(t[2]);
                    int tgt = t.length >= 4 ? Integer.parseInt(t[3]) : -1;
                    return new PendingAction(use ? "potion_use" : "potion_discard", -1, tgt, s);
                }
                default:
                    throw new IllegalArgumentException("unsupported raw command: " + raw);
            }
        }
        switch (cmd) {
            case "play": return new PendingAction("play", hand, target, -1);
            case "end": return new PendingAction("end", -1, -1, -1);
            case "potion_use": return new PendingAction("potion_use", -1, target, slot);
            case "potion_discard": return new PendingAction("potion_discard", -1, -1, slot);
            default:
                throw new IllegalArgumentException(
                        "unknown cmd '" + cmd + "' (supported: play, end, potion_use, potion_discard, raw)");
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        byte[] buf = new byte[4096];
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        java.io.InputStream is = ex.getRequestBody();
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
            if (bos.size() > 65536) {
                throw new IllegalArgumentException("action body too large");
            }
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange ex, int code, String body, boolean headOnly) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (headOnly) {
            ex.sendResponseHeaders(code, -1);
        } else {
            ex.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
            OutputStream os = ex.getResponseBody();
            try {
                os.write(bytes);
            } finally {
                try {
                    os.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                String k = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                String v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                m.put(k, v);
            } catch (UnsupportedEncodingException ignored) {
            }
        }
        return m;
    }
}
