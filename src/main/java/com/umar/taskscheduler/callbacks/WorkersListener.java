package com.umar.taskscheduler.callbacks;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.umar.taskscheduler.util.ZKUtils;

/**
 * WorkersListener listens for changes in worker nodes within ZooKeeper. It handles events such as the addition
 * and removal of workers. In the event of a worker being lost (NODE_DELETED), the listener identifies jobs 
 * assigned to the lost worker and reassigns them by recreating job entries in ZooKeeper.
 * 
 * Author: Umar Mohammad
 */
@Slf4j
public class WorkersListener implements CuratorCacheListener {

    private final CuratorCache assignmentCache;
    private final CuratorFramework curator;
    private static final Logger log = LoggerFactory.getLogger(WorkersListener.class);

    /**
     * Constructor for WorkersListener.
     *
     * @param assignmentCache CuratorCache instance containing job assignments.
     * @param curator CuratorFramework instance to interact with ZooKeeper.
     */
    public WorkersListener(CuratorCache assignmentCache, CuratorFramework curator) {
        this.assignmentCache = assignmentCache;
        this.curator = curator;
    }

    /**
     * Handles ZooKeeper events related to workers.
     *
     * When a worker node is created, logs the event. When a worker node is deleted, it identifies jobs
     * assigned to the lost worker and attempts to recreate them under the /jobs path for reassignment.
     *
     * @param type The type of event (e.g., NODE_CREATED, NODE_DELETED).
     * @param oldData Data before the change (for NODE_DELETED).
     * @param data The current data of the node (for NODE_CREATED).
     */
    @Override
    public void event(Type type, ChildData oldData, ChildData data) {
        if (type == Type.NODE_CREATED) {
            log.info("New worker found {} ", data.getPath());
        } else if (type == Type.NODE_DELETED) {
            // Handle the event where a worker is lost
            log.info("Lost worker {}", oldData.getPath());
            String lostWorkerID = oldData.getPath().substring(oldData.getPath().lastIndexOf('/') + 1);
            Map<String, byte[]> assignableJobIds = new HashMap<>();
            
            // Find jobs assigned to the lost worker
            assignmentCache.stream()
                .forEach(childData -> {
                    String path = childData.getPath();
                    int begin = path.indexOf('/') + 1;
                    int end = path.indexOf('/', begin);
                    String pathWorkerID = path.substring(begin, end);
                    if (pathWorkerID.equals(lostWorkerID)) {
                        String jobID = path.substring(end + 1);
                        log.info("Found job {} assigned to lost worker {}", jobID, lostWorkerID);
                        assignableJobIds.put(jobID, childData.getData());
                    }
                });
            
            // Recreate job entries in the /jobs path for reassignment
            assignableJobIds.forEach(
                (jobId, jobData) -> asyncCreateJob(ZKUtils.getJobsPath() + "/" + jobId, jobData));
        }
    }

    /**
     * Asynchronously recreates a job in ZooKeeper under the /jobs path.
     *
     * This method is used to recover jobs that were assigned to a lost worker, allowing the job to be reassigned
     * to another worker. It handles retries in case of connection loss.
     *
     * @param path The path in ZooKeeper where the job will be recreated.
     * @param data The serialized job data.
     */
    private void asyncCreateJob(String path, byte[] data) {
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
                                case OK -> log.info("Job repaired successfully for {}", event.getPath());
                                case CONNECTIONLOSS -> {
                                    log.error("Lost connection to ZK while repairing job {}, retrying", event.getPath());
                                    asyncCreateJob(event.getPath(), (byte[]) event.getContext());
                                }
                                case NODEEXISTS -> log.warn("Job already exists for path {}", event.getPath());
                                default -> log.error("Unhandled event {}", event);
                            }
                        }
                    },
                    data)
                .forPath(path, data);
        } catch (Exception e) {
            log.error("Error while repairing job {}", path, e);
            throw new RuntimeException(e);
        }
    }
}
