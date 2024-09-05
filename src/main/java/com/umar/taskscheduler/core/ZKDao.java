package com.umar.taskscheduler.core;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZKDao is responsible for interacting with ZooKeeper to perform CRUD operations.
 * It provides methods to create, read, update, and delete nodes in ZooKeeper.
 * 
 * This class serves as a DAO (Data Access Object) for abstracting ZooKeeper operations,
 * ensuring separation of concerns and making the code more maintainable.
 * 
 * Author: Umar Mohammad
 */
public class ZKDao {

    private final CuratorFramework curator;
    private static final Logger log = LoggerFactory.getLogger(ZKDao.class);

    /**
     * Constructor for ZKDao.
     *
     * @param curator CuratorFramework instance to interact with ZooKeeper.
     */
    public ZKDao(CuratorFramework curator) {
        this.curator = curator;
    }

    /**
     * Creates a persistent node in ZooKeeper.
     *
     * @param path The ZooKeeper path where the node will be created.
     * @param data The data to store in the node.
     * @throws Exception if an error occurs during node creation.
     */
    public void createNode(String path, byte[] data) throws Exception {
        try {
            curator.create().forPath(path, data);
            log.info("Node created successfully at path {}", path);
        } catch (KeeperException.NodeExistsException e) {
            log.warn("Node already exists at path {}", path);
        } catch (Exception e) {
            log.error("Error creating node at path {}", path, e);
            throw e;
        }
    }

    /**
     * Reads data from a node in ZooKeeper.
     *
     * @param path The ZooKeeper path of the node.
     * @return The data stored in the node.
     * @throws Exception if an error occurs during data retrieval.
     */
    public byte[] getNodeData(String path) throws Exception {
        try {
            byte[] data = curator.getData().forPath(path);
            log.info("Data retrieved successfully from path {}", path);
            return data;
        } catch (KeeperException.NoNodeException e) {
            log.warn("Node not found at path {}", path);
            return null;
        } catch (Exception e) {
            log.error("Error retrieving data from path {}", path, e);
            throw e;
        }
    }

    /**
     * Updates data in a node in ZooKeeper.
     *
     * @param path The ZooKeeper path of the node.
     * @param data The new data to update in the node.
     * @throws Exception if an error occurs during the update.
     */
    public void updateNode(String path, byte[] data) throws Exception {
        try {
            curator.setData().forPath(path, data);
            log.info("Node data updated successfully at path {}", path);
        } catch (KeeperException.NoNodeException e) {
            log.warn("Node not found at path {}", path);
        } catch (Exception e) {
            log.error("Error updating node at path {}", path, e);
            throw e;
        }
    }

    /**
     * Deletes a node in ZooKeeper.
     *
     * @param path The ZooKeeper path of the node to be deleted.
     * @throws Exception if an error occurs during node deletion.
     */
    public void deleteNode(String path) throws Exception {
        try {
            curator.delete().forPath(path);
            log.info("Node deleted successfully at path {}", path);
        } catch (KeeperException.NoNodeException e) {
            log.warn("Node not found at path {}", path);
        } catch (Exception e) {
            log.error("Error deleting node at path {}", path, e);
            throw e;
        }
    }
}
