package stsapi;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Optional, reflection-only bridge to SaveStateMod. */
final class SaveStateBridge {

    private static final String SAVE_STATE_CLASS = "savestate.SaveState";
    private static final Map<String, String> CHECKPOINTS =
            new ConcurrentHashMap<String, String>();

    private SaveStateBridge() {
    }

    static boolean isAvailable() {
        try {
            Class.forName(SAVE_STATE_CLASS);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Map<String, Object> unavailable() {
        return Json.obj(
                "schema_version", 2,
                "mod_version", StsApiMod.VERSION,
                "timestamp", System.currentTimeMillis(),
                "available", false,
                "captured", false,
                "source", "SaveStateMod",
                "restorable", false,
                "error", "SaveStateMod is not loaded; enable it in ModTheSpire to capture exact RNG and object state");
    }

    /** Must only be invoked by SnapshotQueue from CardCrawlGame.update. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> captureOnGameThread() {
        Map<String, Object> out = Json.obj(
                "schema_version", 2,
                "mod_version", StsApiMod.VERSION,
                "timestamp", System.currentTimeMillis(),
                "available", true,
                "captured", false,
                "source", "SaveStateMod",
                "restorable", true,
                "stable", GameState.isStableDecisionState());
        try {
            Class<?> type = Class.forName(SAVE_STATE_CLASS);
            Constructor<?> constructor = type.getConstructor();
            Object saveState = constructor.newInstance();

            Method encode = type.getMethod("encode");
            Method jsonEncode = type.getMethod("jsonEncode");
            Object encoded = encode.invoke(saveState);
            Object json = jsonEncode.invoke(saveState);
            Object parsed = JsonReader.parse(String.valueOf(json));

            out.put("captured", true);
            out.put("encoded", encoded == null ? null : String.valueOf(encoded));
            out.put("state", parsed);
            return out;
        } catch (Throwable t) {
            out.put("error", rootMessage(t));
            return out;
        }
    }

    /**
     * Saves or restores a complete SaveStateMod checkpoint. This method is
     * only called by CheckpointQueue on the game thread.
     */
    static Map<String, Object> checkpointOnGameThread(String operation, String slot) {
        Map<String, Object> out = Json.obj(
                "schema_version", 2,
                "mod_version", StsApiMod.VERSION,
                "timestamp", System.currentTimeMillis(),
                "source", "SaveStateMod",
                "operation", operation,
                "slot", slot,
                "ok", false);
        try {
            Class<?> type = Class.forName(SAVE_STATE_CLASS);
            if ("save".equals(operation)) {
                Object saveState = type.getConstructor().newInstance();
                String encoded = String.valueOf(type.getMethod("encode").invoke(saveState));
                CHECKPOINTS.put(slot, encoded);
                out.put("ok", true);
                out.put("stored", true);
                return out;
            }
            if ("restore".equals(operation)) {
                String encoded = CHECKPOINTS.get(slot);
                if (encoded == null) {
                    out.put("error", "checkpoint slot not found: " + slot);
                    return out;
                }
                Constructor<?> constructor = type.getConstructor(String.class);
                Object saveState = constructor.newInstance(encoded);
                type.getMethod("loadState").invoke(saveState);
                out.put("ok", true);
                out.put("restored", true);
                return out;
            }
            if ("clear".equals(operation)) {
                CHECKPOINTS.remove(slot);
                out.put("ok", true);
                out.put("cleared", true);
                return out;
            }
            out.put("error", "unsupported checkpoint operation: " + operation);
            return out;
        } catch (Throwable t) {
            out.put("error", rootMessage(t));
            return out;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + String.valueOf(cur.getMessage());
    }
}
