package com.umar.taskscheduler.strategy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.curator.framework.recipes.cache.ChildData;

/**
 * RoundRobinWorker is a strategy for selecting a worker node in a round-robin fashion.
 * 
 * This class uses an {@link AtomicInteger} to keep track of the current index of the worker.
 * The index is incremented each time a worker is selected, and it wraps around when the end of the list is reached.
 * The atomic nature of the index ensures that it can safely be accessed from multiple threads.
 * 
 * Author: Umar Mohammad
 */
public class RoundRobinWorker implements WorkerPickerStrategy {

    // AtomicInteger to keep track of the current worker index for thread-safe operations
    private final AtomicInteger index = new AtomicInteger(0);

    /**
     * Selects a worker in a round-robin manner from the provided list of workers.
     * 
     * The method ensures that the worker index is updated atomically to allow for safe concurrent access.
     * The index wraps around to zero when the end of the list is reached, ensuring all workers are selected evenly.
     *
     * @param workers The list of available workers.
     * @return The selected {@link ChildData} representing the chosen worker.
     */
    @Override
    public ChildData evaluate(List<ChildData> workers) {
        int chosenIndex;
        
        // Loop until a successful atomic update of the index is achieved
        while (true) {
            chosenIndex = index.get();
            int nextIndex = (chosenIndex + 1) < workers.size() ? (chosenIndex + 1) : 0;
            
            // Perform an atomic compare-and-set operation to update the index
            if (index.compareAndSet(chosenIndex, nextIndex)) {
                break;
            }
        }

        // Return the worker at the chosen index
        return workers.get(chosenIndex);
    }
}
