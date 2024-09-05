package com.umar.taskscheduler;

import java.io.Serializable;

/**
 * JobDetail interface that represents the details of a job to be executed in the Distributed Task Scheduler.
 * 
 * This interface extends {@link Runnable} for task execution and {@link Serializable}
 * to allow job details to be transmitted or stored as serialized objects.
 * 
 * Any class implementing this interface should define the logic for the job's execution
 * in the {@link Runnable#run()} method and ensure the job details can be serialized.
 * 
 * Implementations of this interface are expected to be used for scheduling and executing
 * tasks within the distributed system.
 * 
 * @author Umar Mohammad
 */
public interface JobDetail extends Runnable, Serializable {
    // No additional methods; combines Runnable and Serializable for job execution and serialization.
}
