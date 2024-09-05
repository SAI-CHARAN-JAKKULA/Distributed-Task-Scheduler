package com.umar.taskscheduler.resources;

import com.google.inject.Inject;
import com.umar.taskscheduler.util.ZKUtils;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Job resource for handling job creation in the Distributed Task Scheduler.
 * 
 * This resource provides an endpoint for clients to submit job data via HTTP POST requests.
 * The job is then registered in ZooKeeper using CuratorFramework, and a unique job ID is generated.
 * 
 * Author: Umar Mohammad
 */
@Path("/v1/jobs")
@Slf4j
public class Job {

    private final CuratorFramework curator;
    private static final Logger log = LoggerFactory.getLogger(Job.class);

    /**
     * Constructor for the Job resource.
     *
     * @param curator CuratorFramework instance used for interacting with ZooKeeper.
     */
    @Inject
    public Job(CuratorFramework curator) {
        this.curator = curator;
    }

    /**
     * Handles job creation requests.
     *
     * This method accepts job data in the form of a string, generates a unique job ID,
     * and stores the job in ZooKeeper under the /jobs path.
     * 
     * @param jobData The data associated with the job being submitted.
     */
    @POST
    public void createJob(String jobData) {
        log.info("Received job data: {}", jobData);
        try {
            // Create a persistent node in ZooKeeper with a unique job ID
            String jobPath = ZKUtils.getJobsPath() + "/" + UUID.randomUUID();
            curator.create()
                   .withMode(CreateMode.PERSISTENT)
                   .forPath(jobPath, jobData.getBytes());
            
            log.info("Job created successfully at path {}", jobPath);
        } catch (Exception e) {
            log.error("Unable to submit job", e);
            throw new RuntimeException("Job creation failed", e);
        }
    }
}
