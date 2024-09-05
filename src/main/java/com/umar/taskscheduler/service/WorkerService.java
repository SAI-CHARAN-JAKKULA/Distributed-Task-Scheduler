package com.umar.taskscheduler.service;

import com.umar.taskscheduler.callbacks.AssignmentListener;
import com.umar.taskscheduler.callbacks.JobsListener;
import com.umar.taskscheduler.callbacks.WorkersListener;
import com.umar.taskscheduler.strategy.RoundRobinWorker;
import com.umar.taskscheduler.strategy.WorkerPickerStrategy;
import com.umar.taskscheduler.util.ZKUtils;

import java.io.Closeable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.framework.recipes.leader.CancelLeadershipException;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WorkerService manages the lifecycle of a worker node in the Distributed Task Scheduler.
 * It handles the worker's registration, leader election, assignment watching, and interaction with ZooKeeper.
 * The class implements {@link LeaderSelectorListener} for leader election events and {@link Closeable}
 * to manage resource cleanup when a worker stops.
 * 
 * Author: Umar Mohammad
 */
@Slf4j
@Getter
public class WorkerService implements LeaderSelectorListener, Closeable {

    private final LeaderSelector leaderSelector;
    private final AtomicBoolean shouldStop = new AtomicBoolean(false);
    private final CuratorFramework curator;
    private final AtomicBoolean registrationRequired = new AtomicBoolean(true);
    private final WorkerPickerStrategy workerPickerStrategy;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private volatile String name;
    private CuratorCache workersCache;
    private CuratorCache jobsCache;
    private CuratorCache assignmentCache;
    private CuratorCacheListener workersListener;
    private CuratorCacheListener jobsListener;
    private CuratorCacheListener assignmentListener;
    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    /**
     * Constructor for WorkerService.
     * 
     * Initializes the worker node and starts leader election through the {@link LeaderSelector}.
     * The worker node is registered in ZooKeeper, and background watches are set up.
     * 
     * @param curator CuratorFramework instance for interacting with ZooKeeper.
     * @param path The path for leader election in ZooKeeper.
     */
    public WorkerService(CuratorFramework curator, String path) {
        this.curator = curator;
        leaderSelector = new LeaderSelector(curator, path, this);
        leaderSelector.start();
        leaderSelector.autoRequeue();
        setup();
        workerPickerStrategy = new RoundRobinWorker();
    }

    /**
     * Initializes worker registration and sets up ZooKeeper paths for jobs, assignments, and statuses.
     */
    private void setup() {
        registerWorker();
        asyncCreate(ZKUtils.getJobsPath(), CreateMode.PERSISTENT, null);
        asyncCreate(ZKUtils.getAssignmentPath(name), CreateMode.PERSISTENT, null);
        asyncCreate(ZKUtils.STATUS_ROOT, CreateMode.PERSISTENT, null);
    }

    /**
     * Registers the worker by creating an ephemeral node in ZooKeeper.
     * 
     * The worker is assigned a unique name, and the assignment path is watched for job assignments.
     */
    private void registerWorker() {
        if (registrationRequired.get()) {
            log.info("Attempting worker registration");
            name = UUID.randomUUID().toString();
            log.info("Generated a new random name for the worker: {}", name);
            asyncCreate(ZKUtils.getWorkerPath(name), CreateMode.EPHEMERAL, registrationRequired);
            asyncCreate(ZKUtils.getAssignmentPath(name), CreateMode.PERSISTENT, null);
            watchAssignmentPath();
        }
    }

    /**
     * Asynchronously creates a ZooKeeper node with the specified path and mode.
     * 
     * @param path The path of the ZooKeeper node.
     * @param mode The mode for node creation (e.g., PERSISTENT, EPHEMERAL).
     * @param context Optional context used for setting additional state.
     */
    private void asyncCreate(String path, CreateMode mode, Object context) {
        try {
            curator.create()
                .idempotent()
                .creatingParentsIfNeeded()
                .withMode(mode)
                .inBackground((client, event) -> {
                    switch (KeeperException.Code.get(event.getResultCode())) {
                        case OK -> {
                            log.info("Path created successfully: {}", event.getPath());
                            if (context != null) {
                                log.info("Setting the registration required field to false");
                                ((AtomicBoolean) context).set(false);
                            }
                        }
                        case CONNECTIONLOSS -> {
                            log.error("Lost connection to ZooKeeper while creating {}, retrying", event.getPath());
                            asyncCreate(event.getPath(), mode, context);
                        }
                        case NODEEXISTS -> log.warn("Node already exists at path: {}", event.getPath());
                        default -> log.error("Unhandled event: {}", event);
                    }
                }, context)
                .forPath(path);
        } catch (Exception e) {
            log.error("Unable to create node at path: {}", path, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Watches the jobs and workers paths in ZooKeeper. Only the leader worker will perform this action.
     */
    private void watchJobsAndWorkersPath() {
        workersCache = CuratorCache.build(curator, ZKUtils.WORKERS_ROOT);
        workersCache.start();
        log.info("Watching workers root path: {}", ZKUtils.WORKERS_ROOT);
        workersListener = new WorkersListener(assignmentCache, curator);
        workersCache.listenable().addListener(workersListener);

        jobsCache = CuratorCache.build(curator, ZKUtils.JOBS_ROOT);
        log.info("Watching jobs root path: {}", ZKUtils.getJobsPath());
        jobsCache.start();
        jobsListener = new JobsListener(curator, workersCache, workerPickerStrategy);
        jobsCache.listenable().addListener(jobsListener);
    }

    /**
     * Watches the assignment path for job assignments.
     */
    private void watchAssignmentPath() {
        assignmentCache = CuratorCache.build(curator, ZKUtils.getAssignmentPath(name));
        log.info("Watching assignment path: {}", ZKUtils.getAssignmentPath(name));
        assignmentCache.start();
        assignmentListener = new AssignmentListener(curator);
        assignmentCache.listenable().addListener(assignmentListener);
    }

    /**
     * Deletes the worker node and removes the listeners for jobs and workers.
     */
    private void destroy() {
        log.info("Deleting worker path: {}", ZKUtils.getWorkerPath(name));
        try {
            curator.delete().forPath(ZKUtils.getWorkerPath(name));
        } catch (Exception e) {
            log.error("Unable to delete worker path: {}", ZKUtils.getWorkerPath(name), e);
        }
        log.info("Removing workers listener");
        workersCache.listenable().removeListener(workersListener);
        workersCache.close();
        log.info("Removing jobs listener");
        jobsCache.listenable().removeListener(jobsListener);
        jobsCache.close();
    }

    /**
     * Closes the LeaderSelector and relinquishes leadership.
     */
    @Override
    public void close() {
        leaderSelector.close();
    }

    /**
     * Takes leadership and watches the job and worker paths.
     * This method does not return until leadership is relinquished.
     */
    @Override
    public void takeLeadership(CuratorFramework client) {
        log.info("{} is now the leader", name);
        watchJobsAndWorkersPath();
        lock.lock();
        try {
            while (!shouldStop.get()) {
                condition.await();
            }
            if (shouldStop.get()) {
                log.warn("{} is signaled to stop!", name);
                leaderSelector.close();
            }
        } catch (InterruptedException e) {
            log.error("Thread interrupted, exiting leadership", e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles connection state changes in ZooKeeper.
     * 
     * Re-registers the worker on reconnection and manages the leadership state on connection loss.
     */
    @Override
    public void stateChanged(CuratorFramework client, ConnectionState newState) {
        if (newState == ConnectionState.RECONNECTED) {
            log.info("Reconnected to ZooKeeper, state: {}", newState);
            registerWorker();
        } else if (newState == ConnectionState.LOST) {
            log.error("Connection lost to ZooKeeper, giving up leadership: {}", newState);
            registrationRequired.set(true);
            assignmentCache.listenable().removeListener(assignmentListener);
            assignmentCache.close();
            if (workersCache != null) {
                workersCache.listenable().removeListener(workersListener);
                workersCache.close();
            }
            if (jobsCache != null) {
                jobsCache.listenable().removeListener(jobsListener);
                jobsCache.close();
            }
            throw new CancelLeadershipException();
        } else if (newState == ConnectionState.SUSPENDED) {
            log.error("Connection suspended to ZooKeeper: {}", newState);
        }
    }

    /**
     * Stops the worker and relinquishes leadership if held.
     */
    public void stop() {
        log.warn("Sending stop signal to {}", name);
        destroy();
        shouldStop.compareAndSet(false, true);
        if (leaderSelector.hasLeadership()) {
            log.warn("Giving up leadership: {}", name);
            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        } else {
            leaderSelector.close();
        }
    }

    /**
     * Returns the ID of the current leader in the worker cluster.
     *
     * @return The leader's ID, or an empty Optional if no leader is available.
     */
    public Optional<String> getLeader() {
        try {
            return Optional.of(leaderSelector.getLeader().getId());
        } catch (Exception e) {
            log.error("Unable to retrieve leader information", e);
            return Optional.empty();
        }
    }
}
