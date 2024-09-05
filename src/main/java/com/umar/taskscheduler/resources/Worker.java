package com.umar.taskscheduler.resources;

import com.google.inject.Inject;
import com.umar.taskscheduler.service.WorkerService;
import com.umar.taskscheduler.util.ZKUtils;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.apache.curator.framework.CuratorFramework;

/**
 * Worker resource for managing worker nodes in the Distributed Task Scheduler.
 * 
 * This resource provides endpoints to stop a worker and retrieve the leader of the worker cluster.
 * It interacts with ZooKeeper through CuratorFramework and utilizes the WorkerService to perform 
 * these actions.
 * 
 * Author: Umar Mohammad
 */
@Path("/v1/workers")
public class Worker {

    private final CuratorFramework curator;
    private WorkerService worker;

    /**
     * Constructor for Worker resource.
     * 
     * Initializes the WorkerService, which handles worker operations such as stopping a worker 
     * and retrieving the leader.
     *
     * @param curator CuratorFramework instance for interacting with ZooKeeper.
     */
    @Inject
    public Worker(CuratorFramework curator) {
        this.curator = curator;
        initWorker();
    }

    /**
     * Initializes the WorkerService with the provided CuratorFramework and leader path.
     */
    private void initWorker() {
        worker = new WorkerService(curator, ZKUtils.LEADER_ROOT);
    }

    /**
     * Stops a worker based on the provided worker ID.
     * 
     * This method calls the WorkerService to stop the worker associated with the given ID.
     *
     * @param id The ID of the worker to stop.
     */
    @DELETE
    @Path("/{id}")
    public void stopWorker(@PathParam("id") String id) {
        worker.stop();
    }

    /**
     * Retrieves the ID of the current leader in the worker cluster.
     * 
     * This method queries the WorkerService to return the leader's ID, or a message if no leader is found.
     *
     * @return The leader's ID, or "No leader found" if none is available.
     */
    @GET
    @Path("/leader")
    public String getLeaderId() {
        return worker.getLeader().orElse("No leader found");
    }
}
