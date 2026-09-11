package stsapi;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Queues SaveStateMod save/restore work onto the game thread. */
final class CheckpointQueue {

    private static final long TIMEOUT_SECONDS = 30L;
    private static final ConcurrentLinkedQueue<Request> QUEUE =
            new ConcurrentLinkedQueue<Request>();

    private CheckpointQueue() {
    }

    static Map<String, Object> request(String operation, String slot) {
        if (!SaveStateBridge.isAvailable()) return SaveStateBridge.unavailable();
        Request request = new Request(operation, slot);
        QUEUE.add(request);
        try {
            return request.result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            QUEUE.remove(request);
            return Json.obj("ok", false, "error",
                    "timed out waiting for the game thread; enable SaveStateMod and keep the game updating");
        } catch (InterruptedException e) {
            QUEUE.remove(request);
            Thread.currentThread().interrupt();
            return Json.obj("ok", false, "error", "checkpoint request interrupted");
        } catch (Throwable t) {
            QUEUE.remove(request);
            return Json.obj("ok", false, "error", String.valueOf(t));
        }
    }

    static void drainOnGameThread() {
        Request request;
        while ((request = QUEUE.poll()) != null) {
            try {
                request.result.complete(
                        SaveStateBridge.checkpointOnGameThread(request.operation, request.slot));
            } catch (Throwable t) {
                request.result.complete(Json.obj("ok", false, "error", String.valueOf(t)));
            }
        }
    }

    static int pendingCount() {
        return QUEUE.size();
    }

    private static final class Request {
        final String operation;
        final String slot;
        final CompletableFuture<Map<String, Object>> result =
                new CompletableFuture<Map<String, Object>>();

        Request(String operation, String slot) {
            this.operation = operation;
            this.slot = slot;
        }
    }
}
