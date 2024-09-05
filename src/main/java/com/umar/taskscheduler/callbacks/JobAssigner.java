package com.umar.taskscheduler.callbacks;

import com.umar.taskscheduler.strategy.WorkerPickerStrategy;
import com.umar.taskscheduler.util.ZKUtils;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JobAssigner is responsible for assigning jobs to available workers in the distributed task scheduler.
 * 
 * It uses a {@link WorkerPickerStrategy} to select a worker from the list of available workers
 * and assigns the job to the chosen worker. This class also handles creating the assignment in 
 * ZooKeeper and cleaning up job-related data once the assignment is created.
 * 
 * The assignments and job details are stored in ZooKeeper nodes, and any ZooKeeper-related
 * operations (such as node creation and deletion) are performed asynchronously.
 * 
 * Error handling is provided for connection loss and retries in case of failure during ZooKeeper interactions.
 * 
 * Future improvements include atomic operations for assignment and deletion using MultiOp.
 * 
 * @author Umar Mohammad
 */
@Slf4j
public class JobAssigner implements Runnable {

    private final CuratorFramework curator;
    private final String jobID;
    private final CuratorCache workersCache;
    private final WorkerPickerStrategy workerPickerStrategy;
    private final byte[] jobData;
    private String workerName;
    private static final Logger log = LoggerFactory.getLogger(JobAssigner.class);

    /**
     * Constructor for JobAssigner.
     *
     * @param jobID The unique identifier of the job to be assigned.
     * @param jobData The serialized data representing the job details.
     * @param curator CuratorFramework instance to interact with ZooKeeper.
     * @param workersCache Cache of available workers stored in ZooKeeper.
     * @param workerPickerStrategy Strategy used to select a worker from the available workers.
     */
    public JobAssigner(
        String jobID,
        byte[] jobData,
        CuratorFramework curator,
        CuratorCache workersCache,
        WorkerPickerStrategy workerPickerStrategy) {
        this.jobID = jobID;
        this.curator = curator;
        this.workersCache = workersCache;
        this.workerPickerStrategy = workerPickerStrategy;
        this.jobData = jobData;
    }

    /**
     * Run method that is executed when the job assigner is run in a separate thread.
     * 
     * <p>This method selects a worker from the available workers using the provided strategy
     * and assigns the incoming job to the selected worker. After assigning, it triggers the
     * asynchronous creation of the assignment node in ZooKeeper.
     */
    @Override
    public void run() {
        // Filter and collect available workers from the ZooKeeper cache
        List<ChildData> workers =
            workersCache.stream()
                .filter(childData -> (childData.getPath().length() > ZKUtils.WORKERS_ROOT.length()))
                .toList();
        
        // Pick a worker using the provided strategy
        ChildData chosenWorker = workerPickerStrategy.evaluate(workers);
        workerName = ZKUtils.extractNode(chosenWorker.getPath());
        
        log.info(
            "Found total workers {}, Chosen worker index {}, worker name {}",
            workers.size(),
            chosenWorker,
            workerName);
        
        // Asynchronously create the assignment for the chosen worker
        asyncCreateAssignment();
    }

    /**
     * Asynchronously creates an assignment for the selected worker in ZooKeeper.
     * 
     * <p>This method creates a new persistent node in ZooKeeper representing the assignment
     * of the job to the worker. It stores the job data directly in the assignment node to
     * avoid additional network calls by the worker. The assignment node is deleted after
     * successful job execution.
     */
    private void asyncCreateAssignment() {
        try {
            curator
                .create()
                .idempotent()
                .withMode(CreateMode.PERSISTENT)
                .inBackground(
                    new BackgroundCallback() {
                        @Override
                        public void processResult(CuratorFramework client, CuratorEvent event) {
                            switch (KeeperException.Code.get(event.getResultCode())) {
                                case OK -> {
                                    log.info(
                                        "Assignment created successfully for JobID {} with WorkerID {}",
                                        jobID,
                                        workerName);
                                    
                                    log.info("Performing async deletion of {}", ZKUtils.getJobsPath() + "/" + jobID);
                                    asyncDelete(ZKUtils.getJobsPath() + "/" + jobID);
                                }
                                case CONNECTIONLOSS -> {
                                    log.error("Lost connection to ZK while creating {}, retrying", event.getPath());
                                    asyncCreateAssignment();
                                }
                                case NODEEXISTS -> log.warn("Assignment already exists for path {}", event.getPath());
                                case NONODE -> log.error("Trying to create an assignment for a worker that does not exist {}", event);
                                default -> log.error("Unhandled event {} ", event);
                            }
                        }
                    })
                .forPath(ZKUtils.ASSIGNMENT_ROOT + "/" + workerName + "/" + jobID, jobData);
            // Store the job data along with the assignment to avoid additional calls for job details.
            // This simplifies recovery since unassigned jobs remain under /jobs.
        } catch (Exception e) {
            log.error("Error while creating assignment for {} with {}", jobID, workerName, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Asynchronously deletes the ZooKeeper node representing the job after assignment.
     * 
     * <p>This method deletes the job node in ZooKeeper and retries in case of connection loss.
     *
     * @param path The path of the job node to be deleted in ZooKeeper.
     */
    private void asyncDelete(String path) {
        try {
            curator
                .delete()
                .idempotent()
                .guaranteed()
                .inBackground(
                    new BackgroundCallback() {
                        @Override
                        public void processResult(CuratorFramework client, CuratorEvent event) {
                            switch (KeeperException.Code.get(event.getResultCode())) {
                                case OK -> log.info("Path deleted successfully {}", event.getPath());
                                case CONNECTIONLOSS -> {
                                    log.info("Lost connection to ZK while deleting {}, retrying", event.getPath());
                                    asyncDelete(event.getPath());
                                }
                                default -> log.error("Unhandled event {}", event);
                            }
                        }
                    })
                .forPath(path);
        } catch (Exception e) {
            log.error("Unable to delete {} due to ", path, e);
            throw new RuntimeException(e);
        }
    }
}
