package com.sajad.knowledge.config;

import com.sajad.knowledge.api.ApiException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import java.util.function.Function;

/** One admitted AI operation; timeout never releases a still-running worker's slot. */
@Component
public class WorkGate {
    private final Semaphore slot = new Semaphore(1);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final long seconds;
    public WorkGate(@Value("${app.deadline-seconds:120}") long seconds) { this.seconds = seconds; }
    public record Deadline(long end) {
        public void check() {
            if (System.nanoTime() >= end || Thread.currentThread().isInterrupted())
                throw new ApiException(504, "AI_TIMEOUT", "Processing exceeded its deadline. Refresh the document list before retrying an upload.");
        }
    }
    public <T> T run(Function<Deadline,T> operation) {
        if (!slot.tryAcquire()) throw new ApiException(429, "AI_BUSY", "Another AI operation is still running. Please wait and try again.");
        Future<T> future;
        try {
            future = executor.submit(() -> {
                try { return operation.apply(new Deadline(System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds))); }
                finally { slot.release(); }
            });
        } catch (RejectedExecutionException e) {
            slot.release(); throw new ApiException(503, "STOPPING", "The application is shutting down.");
        }
        try { return future.get(seconds, TimeUnit.SECONDS); }
        catch (TimeoutException e) { throw new ApiException(504, "AI_TIMEOUT", "Processing exceeded its deadline. Wait, then refresh the document list before retrying."); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ApiException(503, "INTERRUPTED", "Request interrupted.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new ApiException(502, "AI_FAILED", "AI processing failed.");
        }
    }
    public boolean busy() { return slot.availablePermits() == 0; }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
