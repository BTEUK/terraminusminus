package net.buildtheearth.terraminusminus.util;

import lombok.NonNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.Timer;
import java.util.TimerTask;

public class PerformanceTracker {
    private static final Map<String, MetricsCollector> collectors = new ConcurrentHashMap<>();
    private static final Timer statsTimer;

    static {
        statsTimer = new Timer("Performance-Monitor", true);
    }

    public static void trackOperation(@NonNull String operationName, long durationNanos) {
        collectors.computeIfAbsent(operationName, k -> new MetricsCollector())
                .record(durationNanos);
    }

    public static void trackOperationMillis(@NonNull String operationName, long durationMillis) {
        trackOperation(operationName, durationMillis * 1_000_000);
    }

    public static void printAllStats() {
        System.out.println("\nPerformance Statistics:");
        System.out.println("=======================");

        collectors.forEach((operation, collector) -> {
            collector.printStats(operation);
        });

        System.out.println("=======================\n");
    }

    private static class MetricsCollector {
        private final LongAdder totalTime = new LongAdder();
        private final LongAdder invocationCount = new LongAdder();
        private final AtomicLong lastPrintTime = new AtomicLong(System.currentTimeMillis());

        void record(long durationNanos) {
            totalTime.add(durationNanos);
            invocationCount.increment();
        }

        void printStats(String operationName) {
            long currentTime = System.currentTimeMillis();
            long totalTimeNanos = totalTime.sum();
            long count = invocationCount.sum();

            if (count > 0) {
                double avgTimeMs = (totalTimeNanos / count) / 1_000_000.0;
                double totalTimeMs = totalTimeNanos / 1_000_000.0;

                System.out.printf("""
                    %s:
                    - Total executions: %d
                    - Average time: %.2f ms
                    - Cumulative time: %.2f ms
                    - Time window: %d seconds
                    ----------------------------------------
                    """,
                        operationName,
                        count,
                        avgTimeMs,
                        totalTimeMs,
                        (currentTime - lastPrintTime.get()) / 1000
                );
            }

            // Reset counters
            totalTime.reset();
            invocationCount.reset();
            lastPrintTime.set(currentTime);
        }
    }

    // Optional: Method to cleanup resources
    public static void shutdown() {
        if (statsTimer != null) {
            statsTimer.cancel();
        }
    }
}