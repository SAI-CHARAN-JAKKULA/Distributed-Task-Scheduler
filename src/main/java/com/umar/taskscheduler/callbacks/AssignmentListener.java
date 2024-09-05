package com.umar.taskscheduler.callbacks;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.umar.taskscheduler.util.ZKUtils;

/**
 * AssignmentListener is responsible for listening to ZooKeeper events related to job assignments.
 * 
 * This listener detects newly created nodes representing job assignments, deserializes the job details,
 * executes the job asynchronously, and updates the job's status in ZooKeeper upon completion.
 * 
 * It leverages Curator's {@link CuratorCacheListener} to monitor node changes and uses 
 * an {@link ExecutorService} to execute jobs in separate threads to avoid blocking the watcher thread.
 * 
 * Future improvements include adding a daemon service for cleanup of completed jobs.
 * 
 * @author Umar Mohammad
 */
@Slf4j
public class AssignmentListener implements CuratorCacheListener {

    private final CuratorFramework curator;
    private final ExecutorService executorService;
    private static final Logger log = LoggerFactory.getLogger(AssignmentListener.class);

    /**
     * Constructor for AssignmentListener.
     *
     * @param curator CuratorFramework instance to interact with ZooKeeper.
     */
    public AssignmentListener(CuratorFramework curator) {
        this.curator = curator;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * Handles ZooKeeper events related to job assignments.
     * 
     * <p>When a new assignment node is created, this method deserializes the job,
     * executes it asynchronously, and updates the status of the job upon completion.
     *
     * @param type The type of event (e.g., NODE_CREATED).
     * @param oldData Data before the change (if applicable).
     * @param data The current data of the node.
     */
    @Override
    public void event(Type type, ChildData oldData, ChildData data) {
        if (type == Type.NODE_CREATED) {
            if (data.getPath().indexOf('/', 1) == data.getPath().lastIndexOf('/')) {
                // Skip root path /assignment/{worker-id}, which contains no job id
                return;
            }
            String jobId = data.getPath().substring(data.getPath().lastIndexOf('/') + 1);
            log.info("Assignment found for job id {}", jobId);

            try {
                // Deserialize job detail from ZooKeeper data
                byte[] bytes = data.getData();
                ObjectInputStream objectInputStream =
                    new ObjectInputStream(new ByteArrayInputStream(bytes));
                Runnable jobDetail = (Runnable) objectInputStream.readObject();
                log.info("Deserialized the JobId {} to {}", jobId, jobDetail);
                
                // Execute the job asynchronously
                CompletableFuture<Void> future = CompletableFuture.runAsync(jobDetail, executorService);
                log.info("Job submitted for execution");
                
                // Upon completion, update job status and perform cleanup
                future.thenAcceptAsync(__ -> asyncCreate(jobId, data.getPath()), executorService);
            } catch (Exception e) {
                log.error("Unable to fetch data for job id {}", jobId, e);
            }
        }
    }

    /**
     * Updates the job status in ZooKeeper after execution.
     * 
     * <p>This method creates a persistent node with TTL (Time To Live) in ZooKeeper
     * to mark the job as completed. It also retries in case of connection loss.
     *
     * @param jobId The ID of the job being executed.
     * @param assignmentPath The path of the assignment node in ZooKeeper.
     */
    private void asyncCreate(String jobId, String assignmentPath) {
        log.info("JobID {} has been executed, moving on to update its status", jobId);

        try {
            // Create status node with TTL in ZooKeeper
            curator
                .create()
                .withTtl(ZKUtils.STATUS_TTL_MILLIS)
                .creatingParentsIfNeeded()
                .withMode(CreateMode.PERSISTENT_WITH_TTL)
                .inBackground(
                    new BackgroundCallback() {
                        @Override
                        public void processResult(CuratorFramework client, CuratorEvent event) {
                            switch (KeeperException.Code.get(event.getResultCode())) {
                                case OK -> {
                                    log.info("Status updated successfully {}", event.getPath());
                                    log.info("Performing deletion of assignment path {}", assignmentPath);
                                    asyncDelete(assignmentPath);
                                }
                                case CONNECTIONLOSS -> {
                                    log.error("Lost connection to ZK while creating {}, retrying", event.getPath());
                                    asyncCreate(jobId, assignmentPath);
                                }
                                case NODEEXISTS -> log.warn("Node already exists for path {}", event.getPath());
                                default -> log.error("Unhandled event {}", event);
                            }
                        }
                    })
                .forPath(ZKUtils.getStatusPath(jobId), "Completed".getBytes());
        } catch (Exception e) {
            log.error("Unable to create {} due to ", ZKUtils.getStatusPath(jobId), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes the assignment node after job execution.
     * 
     * <p>This method deletes the ZooKeeper node for the job assignment
     * and retries if there is a connection loss.
     *
     * @param path The path of the assignment node to delete.
     */
    private void asyncDelete(String path) {
        try {
            // Delete assignment node in ZooKeeper
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
