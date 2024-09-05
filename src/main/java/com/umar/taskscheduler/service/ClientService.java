package com.umar.taskscheduler.service;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.umar.taskscheduler.util.ZKUtils;

/**
 * ClientService is responsible for registering jobs in ZooKeeper for the Distributed Task Scheduler.
 * 
 * This service serializes job details (Runnable) and creates a persistent ZooKeeper node to store the job.
 * It ensures each job is assigned a unique job ID, which is used as the path for the job in ZooKeeper.
 * 
 * Author: Umar Mohammad
 */
@Slf4j
public class ClientService {

    private final CuratorFramework curator;
    private static final Logger log = LoggerFactory.getLogger(ClientService.class);

    /**
     * Constructor for ClientService.
     * 
     * Initializes the service with a CuratorFramework instance for interacting with ZooKeeper.
     *
     * @param curator CuratorFramework instance to manage ZooKeeper interactions.
     */
    public ClientService(CuratorFramework curator) {
        this.curator = curator;
    }

    /**
     * Registers a job in ZooKeeper.
     * 
     * The job is assigned a unique job ID, and the job details are serialized and stored 
     * in ZooKeeper at a path corresponding to the job ID.
     *
     * @param jobDetail The Runnable job to be registered.
     * @return The unique job ID assigned to the registered job.
     */
    public String registerJob(Runnable jobDetail) {
        String jobId = UUID.randomUUID().toString();  // Generate a unique job ID
        syncCreate(ZKUtils.getJobsPath() + "/" + jobId, jobDetail);  // Create a ZNode for the job
        return jobId;
    }

    /**
     * Synchronously creates a ZooKeeper node to store the job data.
     * 
     * This method serializes the job details (Runnable) into a byte array and stores it 
     * in a persistent ZooKeeper node. It ensures the job is stored at the specified path.
     *
     * @param path The ZooKeeper path where the job will be stored.
     * @param runnable The job to be serialized and stored.
     */
    private void syncCreate(String path, Runnable runnable) {
        try {
            // Serialize the job details
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(runnable);
            
            // Create a persistent ZooKeeper node with the serialized job data
            curator.create()
                   .idempotent()
                   .withMode(CreateMode.PERSISTENT)
                   .forPath(path, byteArrayOutputStream.toByteArray());
            
            log.info("Job created successfully at path {}", path);
        } catch (Exception e) {
            log.error("Unable to create node at path {}", path, e);
            throw new RuntimeException(e);  // Rethrow the exception as a runtime exception
        }
    }
}
