package stsapi;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Moves SaveStateMod capture work from the HTTP thread onto the game thread. */
final class SnapshotQueue {

    private static final long TIMEOUT_SECONDS = 5L;
    private static final ConcurrentLinkedQueue<Request> QUEUE = new ConcurrentLinkedQueue<Request>();

    private SnapshotQueue() {
    }

    static Map<String, Object> capture() {
        if (!SaveStateBridge.isAvailable()) return SaveStateBridge.unavailable();

        Request request = new Request();
        QUEUE.add(request);
        try {
            return request.result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            QUEUE.remove(request);
            return error("timed out waiting for the game thread; the game may be paused or not updating");
        } catch (InterruptedException e) {
            QUEUE.remove(request);
            Thread.currentThread().interrupt();
            return error("snapshot request was interrupted");
        } catch (Throwable t) {
            QUEUE.remove(request);
            return error(String.valueOf(t));
        }
    }

    static void drainOnGameThread() {
        Request request;
        while ((request = QUEUE.poll()) != null) {
            try {
                request.result.complete(SaveStateBridge.captureOnGameThread());
            } catch (Throwable t) {
                request.result.complete(error(String.valueOf(t)));
            }
        }
    }

    static int pendingCount() {
        return QUEUE.size();
    }

    private static Map<String, Object> error(String message) {
        return Json.obj(
                "schema_version", 2,
                "mod_version", StsApiMod.VERSION,
                "timestamp", System.currentTimeMillis(),
                "available", true,
                "captured", false,
                "source", "SaveStateMod",
                "restorable", true,
                "error", message);
    }

    private static final class Request {
        final CompletableFuture<Map<String, Object>> result = new CompletableFuture<Map<String, Object>>();
    }
}
