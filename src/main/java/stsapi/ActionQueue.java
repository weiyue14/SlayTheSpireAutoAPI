package stsapi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe action queue. HTTP threads submit actions; the game thread
 * (via GameUpdatePatch, once per frame) drains and executes them, so game
 * state is only ever mutated on the game thread.
 */
final class ActionQueue {

    private static final ConcurrentLinkedQueue<PendingAction> QUEUE =
            new ConcurrentLinkedQueue<>();
    private static final ArrayDeque<Map<String, Object>> RESULTS = new ArrayDeque<>();
    private static final int MAX_RESULTS = 50;
    private static volatile long executedCount = 0;
    private static volatile long failedCount = 0;

    private ActionQueue() {
    }

    static void submit(PendingAction a) {
        QUEUE.add(a);
    }

    static int pending() {
        return QUEUE.size();
    }

    /** Called once per frame on the game thread. Never throws. */
    static void drainOnGameThread() {
        PendingAction a;
        while ((a = QUEUE.poll()) != null) {
            Map<String, Object> r = Json.obj(
                    "cmd", a.describe(),
                    "time", System.currentTimeMillis());
            try {
                String msg = ActionExecutor.execute(a);
                executedCount++;
                r.put("ok", true);
                r.put("message", msg);
            } catch (Throwable t) {
                failedCount++;
                r.put("ok", false);
                r.put("message", String.valueOf(t));
            }
            synchronized (RESULTS) {
                RESULTS.addFirst(r);
                while (RESULTS.size() > MAX_RESULTS) {
                    RESULTS.removeLast();
                }
            }
        }
    }

    static Map<String, Object> status() {
        List<Object> recent;
        synchronized (RESULTS) {
            recent = new ArrayList<>(RESULTS);
        }
        return Json.obj(
                "pending", QUEUE.size(),
                "executed_total", executedCount,
                "failed_total", failedCount,
                "recent_results", recent);
    }
}
