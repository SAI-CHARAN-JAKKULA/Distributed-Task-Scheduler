package com.umar.taskscheduler.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the RoundRobinWorker class.
 * 
 * These tests validate the round-robin worker selection strategy, ensuring that workers are selected
 * in sequence both serially and concurrently.
 * 
 * Author: Umar Mohammad
 */
public class RoundRobinWorkerTest {

    private List<ChildData> workers;

    /**
     * Setup method to initialize the list of workers before each test.
     */
    @BeforeEach
    public void setup() {
        workers = List.of(
            new ChildData("/a", new Stat(), new byte[0]),
            new ChildData("/b", new Stat(), new byte[0]),
            new ChildData("/c", new Stat(), new byte[0]),
            new ChildData("/d", new Stat(), new byte[0]),
            new ChildData("/e", new Stat(), new byte[0])
        );
    }

    /**
     * Test the round-robin evaluation in a serial fashion.
     * 
     * This test ensures that workers are selected in sequence, wrapping around once the list is exhausted.
     */
    @Test
    public void testEvaluateSerially() {
        RoundRobinWorker roundRobinWorker = new RoundRobinWorker();
        for (int i = 0; i < 6; i++) {
            ChildData result = roundRobinWorker.evaluate(workers);
            Assertions.assertEquals(workers.get(i % workers.size()), result);
        }
    }

    /**
     * Test the round-robin evaluation in a concurrent setting.
     * 
     * This test ensures that the worker selection strategy works correctly even when accessed concurrently.
     * The test uses multiple threads to call the evaluate method simultaneously and validates that the correct
     * workers are selected in sequence.
     */
    @Test
    public void testEvaluateConcurrently() throws ExecutionException, InterruptedException {
        RoundRobinWorker roundRobinWorker = new RoundRobinWorker();
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<CompletableFuture<ChildData>> futures = new ArrayList<>();

        // Create concurrent tasks for worker selection
        for (int i = 0; i < 6; i++) {
            CompletableFuture<ChildData> future = CompletableFuture.supplyAsync(() -> roundRobinWorker.evaluate(workers), executorService);
            futures.add(future);
        }

        // Aggregate the results from the futures
        CompletableFuture<List<ChildData>> aggregate = CompletableFuture.completedFuture(new ArrayList<>());
        for (CompletableFuture<ChildData> future : futures) {
            aggregate = aggregate.thenCompose(list -> {
                try {
                    list.add(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
                return CompletableFuture.completedFuture(list);
            });
        }

        // Validate that workers were selected correctly in sequence
        List<ChildData> results = aggregate.join();
        for (int i = 0; i < 6; i++) {
            Assertions.assertEquals(workers.get(i % workers.size()), results.get(i));
        }
    }
}
