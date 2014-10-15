/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.stratos.cloud.controller.topology;

import com.google.gson.Gson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.cloud.controller.util.CloudControllerUtil;
import org.apache.stratos.messaging.domain.topology.Topology;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Persistence and retrieval of Topology from Registry
 */
public class TopologyManager {
    private static final Log log = LogFactory.getLog(TopologyManager.class);

    private static volatile ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private static volatile ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static volatile ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
    private static volatile Map<Integer, Topology> tIdToTopologyMap = new HashMap<Integer, Topology>();

    private TopologyManager() {
    }

    public static void acquireReadLock() {
        if(log.isDebugEnabled()) {
            log.debug("Read lock acquired");
        }
        readLock.lock();
    }

    public static void releaseReadLock() {
        if(log.isDebugEnabled()) {
            log.debug("Read lock released");
        }
        readLock.unlock();
    }

    public static void acquireWriteLock() {
        if(log.isDebugEnabled()) {
            log.debug("Write lock acquired");
        }
        writeLock.lock();
    }

    public static void releaseWriteLock() {
        if(log.isDebugEnabled()) {
            log.debug("Write lock released");
        }
        writeLock.unlock();
    }

    public static Topology getTopology(int tenantId) {
        Topology topology = getTenantTopology(tenantId);
        if (topology == null) {
            synchronized (TopologyManager.class) {
                if (topology == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Trying to retrieve topology from registry");
                    }
                    topology = CloudControllerUtil.retrieveTopology(tenantId);
                    if (topology == null) {
                        if (log.isDebugEnabled()) {
                            log.debug("Topology not found in registry, creating new");
                        }
                        topology = new Topology();
                        updateTenantTopology(tenantId, topology);
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Topology initialized");
                    }
                }
            }
        }
        return topology;
    }

    /**
     * Update in-memory topology and persist it in registry.
     * @param topology_
     */
    public static void updateTopology(int tenantId, Topology topology_) {
        synchronized (TopologyManager.class) {
            if (log.isDebugEnabled()) {
                log.debug("Updating topology");
            }
            updateTenantTopology(tenantId, topology_);
            CloudControllerUtil.persistTopology(topology_);
            if (log.isDebugEnabled()) {
                log.debug(String.format("Topology updated: %s", toJson(topology_)));
            }
        }

    }

    private static String toJson(Object object) {
        Gson gson = new Gson();
        return gson.toJson(object);
    }

    private static Topology getTenantTopology(int tenantId){
        if(tIdToTopologyMap.containsKey(tenantId)){
        return tIdToTopologyMap.get(tenantId);

        }
        return null;
    }

    private static void updateTenantTopology(int tenantId, Topology topology){
        if(tIdToTopologyMap.containsKey(tenantId)){
            tIdToTopologyMap.put(tenantId, topology);
        }
    }
}

