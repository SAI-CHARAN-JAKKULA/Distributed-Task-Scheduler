package com.umar.taskscheduler.resources;

import com.google.inject.Inject;
import com.umar.taskscheduler.service.ClientService;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import java.io.Serializable;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

/**
 * Client resource for handling client-related tasks in the Distributed Task Scheduler.
 * 
 * This resource allows clients to create tasks via HTTP POST requests. 
 * It provides an endpoint to register a sum task, where two integers are added, and the result is logged.
 * 
 * Author: Umar Mohammad
 */
@Slf4j
@Path("/v1/client")
public class Client {
    
    private final ClientService clientService;

    /**
     * Constructor for Client resource, which initializes the ClientService using dependency injection.
     * 
     * @param curator CuratorFramework instance used by the ClientService to interact with ZooKeeper.
     */
    @Inject
    public Client(CuratorFramework curator) {
        this.clientService = new ClientService(curator);
    }

    /**
     * Registers a sum task as a job.
     * 
     * This endpoint allows a client to submit two integers as query parameters, 
     * and a job is created to compute their sum. The job is registered using the ClientService.
     *
     * @param a The first integer.
     * @param b The second integer.
     * @return A response containing the job registration status or ID.
     */
    @POST
    public String createSumTask(@QueryParam("first") int a, @QueryParam("second") int b) {
        // Create a job to compute the sum of two integers
        Runnable jobDetail = (Runnable & Serializable)
            (() -> System.out.println("Sum of " + a + " and " + b + " is " + (a + b)));
        
        // Register the job using ClientService and return the job status
        return clientService.registerJob(jobDetail);
    }
}
