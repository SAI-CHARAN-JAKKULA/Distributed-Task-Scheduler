package com.umar.taskscheduler.util;

import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * ZKUtils is a utility class that provides constants and helper methods for managing ZooKeeper paths 
 * and Time-To-Live (TTL) configurations for the Distributed Task Scheduler.
 * 
 * This class includes predefined paths for workers, jobs, assignments, and status, 
 * along with methods to construct paths dynamically.
 * 
 * Author: Umar Mohammad
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)  // Private constructor to prevent instantiation
public class ZKUtils {

    // ZooKeeper root paths for workers, jobs, assignments, and status
    public static final String WORKERS_ROOT = "/workers";
    public static final String JOBS_ROOT = "/jobs";
    public static final String ASSIGNMENT_ROOT = "/assignments";
    public static final String STATUS_ROOT = "/status";
    public static final String LEADER_ROOT = "/leader";

    // Time-to-Live (TTL) for status nodes in milliseconds
    public static final long STATUS_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);

    /**
     * Constructs the ZooKeeper path for a specific worker.
     * 
     * @param name The name or ID of the worker.
     * @return The full ZooKeeper path for the worker node.
     */
    public static String getWorkerPath(String name) {
        return WORKERS_ROOT + "/" + name;
    }

    /**
     * Constructs the ZooKeeper path for a specific assignment.
     * 
     * @param name The name or ID of the worker.
     * @return The full ZooKeeper path for the assignment node.
     */
    public static String getAssignmentPath(String name) {
        return ASSIGNMENT_ROOT + "/" + name;
    }

    /**
     * Returns the root path for jobs in ZooKeeper.
     * 
     * @return The root path for jobs.
     */
    public static String getJobsPath() {
        return JOBS_ROOT;
    }

    /**
     * Extracts the node name from a full ZooKeeper path.
     * 
     * This method is useful for retrieving the worker ID or job ID from a full path.
     *
     * @param workerPath The full ZooKeeper path.
     * @return The extracted node name (e.g., worker ID or job ID).
     */
    public static String extractNode(String workerPath) {
        int start = workerPath.lastIndexOf('/');
        return workerPath.substring(start + 1);
    }

    /**
     * Constructs the ZooKeeper path for a job's status.
     * 
     * @param jobId The ID of the job.
     * @return The full ZooKeeper path for the job's status.
     */
    public static String getStatusPath(String jobId) {
        return STATUS_ROOT + "/" + jobId;
    }
}
