package com.umar.taskscheduler.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 * GuiceModule configures dependencies for the Distributed Task Scheduler application.
 * 
 * This module provides an instance of CuratorFramework, which is used to interact with ZooKeeper.
 * It uses a namespace to avoid conflicts in shared ZooKeeper clusters and implements a retry policy
 * to handle connection issues.
 * 
 * Author: Umar Mohammad
 */
public class GuiceModule extends AbstractModule {

    /**
     * Provides an instance of CuratorFramework, which manages the connection to ZooKeeper.
     * 
     * The CuratorFramework instance is configured with a retry policy and a namespace to
     * handle failures and ensure task scheduler operations do not conflict with other applications.
     *
     * @return CuratorFramework instance for interacting with ZooKeeper.
     */
    @Provides
    public CuratorFramework curatorFramework() {
        CuratorFramework curatorFramework =
            CuratorFrameworkFactory.builder()
                .namespace("TaskSchedulerV0")  // Ensures isolation in shared ZooKeeper clusters
                .connectString("127.0.0.1:2181")  // Connects to local ZooKeeper instance
                .retryPolicy(new ExponentialBackoffRetry(10, 1))  // Retry policy for handling connection issues
                .build();
        
        curatorFramework.start();  // Start the CuratorFramework client
        return curatorFramework;
    }
}
