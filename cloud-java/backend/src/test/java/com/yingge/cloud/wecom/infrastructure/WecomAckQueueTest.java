package com.yingge.cloud.wecom.infrastructure;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class WecomAckQueueTest {
    @Test void waitsForAckBeforeNextFrameUsingSameCallback() {
        var timer=Executors.newSingleThreadScheduledExecutor();
        try {
            var queue=new WecomAckQueue(timer,Duration.ofSeconds(5));
            var sent=new AtomicInteger();
            var first=queue.enqueue("same",sent::incrementAndGet);
            var second=queue.enqueue("same",sent::incrementAndGet);
            assertThat(sent).hasValue(1);
            assertThat(first).isNotDone();
            queue.ack("other",0);
            assertThat(sent).hasValue(1);
            queue.ack("same",0);
            assertThat(first).isCompleted();
            assertThat(sent).hasValue(2);
            assertThat(second).isNotDone();
            queue.ack("same",0);
            assertThat(second).isCompleted();
        } finally { timer.shutdownNow(); }
    }

    @Test void timeoutRejectsPendingAndQueuedFramesRatherThanAmbiguousRetry() {
        var timer=Executors.newSingleThreadScheduledExecutor();
        try {
            var queue=new WecomAckQueue(timer,Duration.ofMillis(30));
            var sent=new AtomicInteger();
            var first=queue.enqueue("same",sent::incrementAndGet);
            var second=queue.enqueue("same",sent::incrementAndGet);
            assertThatThrownBy(()->first.get(1,TimeUnit.SECONDS)).hasRootCauseMessage("WECOM_ACK_UNCERTAIN");
            assertThatThrownBy(()->second.get(1,TimeUnit.SECONDS)).hasRootCauseMessage("WECOM_ACK_UNCERTAIN");
            assertThat(sent).hasValue(1);
        } finally { timer.shutdownNow(); }
    }

    @Test void rejectsBusinessErrorAndBoundsPerCallbackQueue() {
        var timer=Executors.newSingleThreadScheduledExecutor();
        try {
            var queue=new WecomAckQueue(timer,Duration.ofSeconds(5));
            var failed=queue.enqueue("id",()->{});
            queue.ack("id",400);
            assertThatThrownBy(failed::join).hasRootCauseMessage("WECOM_SEND_REJECTED_400");
            for(int i=0;i<16;i++) queue.enqueue("bounded",()->{});
            assertThatThrownBy(()->queue.enqueue("bounded",()->{}).join()).hasRootCauseMessage("WECOM_SEND_QUEUE_FULL");
            queue.clear("WECOM_CONNECTION_CHANGED");
        } finally { timer.shutdownNow(); }
    }
}
