package com.umar.taskscheduler.callbacks;

import com.umar.taskscheduler.strategy.WorkerPickerStrategy;
import com.umar.taskscheduler.util.ZKUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JobsListener listens for newly created job nodes in ZooKeeper and assigns them to available workers.
 * 
 * It uses a separate thread pool (via ExecutorService) to ensure that job assignment is handled 
 * asynchronously and doesn't block the ZooKeeper watcher thread. The job assignment is performed 
 * by the JobAssigner class, which assigns jobs based on a specified worker-picking strategy.
 * 
 * This listener ensures that new jobs are processed efficiently without missing any events.
 * 
 * Author: Umar Mohammad
 */
@Slf4j
public class JobsListener implements CuratorCacheListener {

    private final CuratorFramework curator;
    private final CuratorCache workersCache;
    private final ExecutorService executorService;
    private final WorkerPickerStrategy workerPickerStrategy;
    private static final Logger log = LoggerFactory.getLogger(JobsListener.class);

    /**
     * Constructor for JobsListener.
     *
     * @param curator CuratorFramework instance to interact with ZooKeeper.
     * @param workersCache Cache of available workers stored in ZooKeeper.
     * @param workerPickerStrategy Strategy used to select a worker from the available workers.
     */
    public JobsListener(
        CuratorFramework curator,
        CuratorCache workersCache,
        WorkerPickerStrategy workerPickerStrategy) {
        this.curator = curator;
        this.workersCache = workersCache;
        this.executorService = Executors.newSingleThreadExecutor();  // Use a single-threaded executor for job assignment
        this.workerPickerStrategy = workerPickerStrategy;
    }

    /**
     * Handles ZooKeeper events related to jobs.
     *
     * When a new job node is created in ZooKeeper, this method extracts the job ID and submits it
     * to the ExecutorService for assignment, ensuring that job assignment runs asynchronously.
     * 
     * @param type The type of event (e.g., NODE_CREATED).
     * @param oldData Data before the change (if applicable).
     * @param data The current data of the node.
     */
    @Override
    public void event(Type type, ChildData oldData, ChildData data) {
        if (type == Type.NODE_CREATED && data.getPath().length() > ZKUtils.JOBS_ROOT.length()) {
            String jobID = ZKUtils.extractNode(data.getPath());
            log.info("Found new job {}, passing it to executor service", jobID);
            
            // Submit the job to the executor service for assignment to a worker.
            executorService.submit(
                new JobAssigner(jobID, data.getData(), curator, workersCache, workerPickerStrategy));
        }
    }
}
