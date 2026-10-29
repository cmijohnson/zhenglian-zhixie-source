package com.yingge.cloud.wecom.infrastructure;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Protocol baseline: @wecom/aibot-node-sdk 1.0.6, src/ws.ts (5s ACK, serial per req_id). */
final class WecomAckQueue {
    private final Map<String, ArrayDeque<Item>> queues = new HashMap<>();
    private final ScheduledExecutorService timers;
    private final Duration timeout;
    private int size;

    WecomAckQueue(ScheduledExecutorService timers, Duration timeout) {
        this.timers = timers;
        this.timeout = timeout;
    }

    synchronized CompletableFuture<Void> enqueue(String requestId, Runnable send) {
        if (size >= 256) return CompletableFuture.failedFuture(new IllegalStateException("WECOM_SEND_QUEUE_FULL"));
        ArrayDeque<Item> queue = queues.computeIfAbsent(requestId, ignored -> new ArrayDeque<>());
        if (queue.size() >= 16) return CompletableFuture.failedFuture(new IllegalStateException("WECOM_SEND_QUEUE_FULL"));
        Item item = new Item(send);
        queue.add(item);
        size++;
        if (queue.size() == 1) start(requestId, item);
        return item.result;
    }

    synchronized void ack(String requestId, int errorCode) {
        ArrayDeque<Item> queue = queues.get(requestId);
        if (queue == null || queue.isEmpty()) return;
        complete(requestId, queue.peek(), errorCode == 0 ? null : new IllegalStateException("WECOM_SEND_REJECTED_" + errorCode));
    }

    synchronized void clear(String code) {
        var pending = queues.values().stream().flatMap(ArrayDeque::stream).toList();
        queues.clear();
        size = 0;
        pending.forEach(item -> item.result.completeExceptionally(new IllegalStateException(code)));
    }

    private void start(String requestId, Item item) {
        timers.schedule(() -> complete(requestId, item, new IllegalStateException("WECOM_ACK_UNCERTAIN")),
                timeout.toMillis(), TimeUnit.MILLISECONDS);
        try { item.send.run(); }
        catch (RuntimeException failure) { complete(requestId, item, failure); }
    }

    private synchronized void complete(String requestId, Item item, Throwable failure) {
        ArrayDeque<Item> queue = queues.get(requestId);
        if (queue == null || queue.peek() != item) return;
        queue.remove();
        size--;
        if (queue.isEmpty()) queues.remove(requestId);
        // An ambiguous ACK must not accidentally satisfy the next send sharing the same request ID.
        if (failure != null && "WECOM_ACK_UNCERTAIN".equals(failure.getMessage())) {
            var abandoned = java.util.List.copyOf(queue);
            size -= queue.size();
            queue.clear();
            queues.remove(requestId);
            item.result.completeExceptionally(failure);
            abandoned.forEach(pending -> pending.result.completeExceptionally(failure));
        } else {
            if (failure == null) item.result.complete(null);
            else item.result.completeExceptionally(failure);
            if (queues.get(requestId) == queue && !queue.isEmpty()) start(requestId, queue.peek());
        }
    }

    private static final class Item {
        final Runnable send;
        final CompletableFuture<Void> result = new CompletableFuture<>();
        Item(Runnable send) { this.send = send; }
    }
}
