package com.dochub.workbench.chatagent.support;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ChatLatencyTrackerTest {

    @Test
    void firstTokenIsRecordedOnceAndEmptyChunksAreIgnored() {
        MutableClock clock = new MutableClock(1_000L);
        ChatLatencyTracker tracker = new ChatLatencyTracker(clock);
        tracker.accepted();

        clock.setMillis(1_100L);
        tracker.onTextChunk("  ");
        clock.setMillis(1_240L);
        tracker.onTextChunk("你");
        clock.setMillis(1_500L);
        tracker.onTextChunk("好");
        tracker.complete();

        assertThat(tracker.snapshot().timeToFirstTokenMs()).isEqualTo(240L);
        assertThat(tracker.snapshot().totalDurationMs()).isEqualTo(500L);
    }

    @Test
    void recordsProviderCallsToolsPromptAndConfigurationVersion() {
        ChatLatencyTracker tracker = new ChatLatencyTracker(
            Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));
        tracker.accepted();
        tracker.onModelRequest(321, 17L);
        tracker.onToolCall();

        assertThat(tracker.snapshot().modelCallCount()).isOne();
        assertThat(tracker.snapshot().toolCallCount()).isOne();
        assertThat(tracker.snapshot().promptCharacters()).isEqualTo(321);
        assertThat(tracker.snapshot().modelConfigVersion()).isEqualTo(17L);
    }

    private static final class MutableClock extends Clock {
        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        private void setMillis(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
