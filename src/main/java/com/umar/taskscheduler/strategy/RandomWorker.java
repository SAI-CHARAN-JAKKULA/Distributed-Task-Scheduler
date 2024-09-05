package com.umar.taskscheduler.strategy;

import java.util.List;
import org.apache.curator.framework.recipes.cache.ChildData;

/**
 * RandomWorker is a strategy for selecting a worker node at random from the available workers.
 * 
 * It implements the {@link WorkerPickerStrategy} interface and uses Java's {@link Math#random()}
 * to randomly pick a worker from the list of workers.
 * 
 * Author: Umar Mohammad
 */
public class RandomWorker implements WorkerPickerStrategy {

    /**
     * Selects a random worker from the provided list of workers.
     * 
     * This method uses {@link Math#random()} to generate a random index and selects the corresponding
     * worker from the list. The chosen worker is returned for task assignment.
     *
     * @param workers The list of available workers.
     * @return The randomly selected {@link ChildData} representing the chosen worker.
     */
    @Override
    public ChildData evaluate(List<ChildData> workers) {
        int chosenWorker = (int) (Math.random() * workers.size());
        return workers.get(chosenWorker);
    }
}
