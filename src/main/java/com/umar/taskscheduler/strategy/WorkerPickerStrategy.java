package com.umar.taskscheduler.strategy;

import java.util.List;
import org.apache.curator.framework.recipes.cache.ChildData;

/**
 * WorkerPickerStrategy defines the contract for selecting a worker from a list of available workers.
 * 
 * Implementing classes will provide specific strategies (e.g., random, round-robin) for worker selection
 * in a distributed task scheduler. The strategy determines which worker should be assigned a task.
 * 
 * Author: Umar Mohammad
 */
public interface WorkerPickerStrategy {

    /**
     * Evaluates the list of available workers and selects one based on the specific strategy.
     *
     * @param workers The list of available workers.
     * @return The selected {@link ChildData} representing the chosen worker.
     */
    ChildData evaluate(List<ChildData> workers);
}
