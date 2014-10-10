/*
 * Licensed to the Apache Software Foundation (ASF) under one 
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY 
 * KIND, either express or implied.  See the License for the 
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.stratos.autoscaler.monitor;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.NetworkPartitionContext;
import org.apache.stratos.autoscaler.NetworkPartitionLbHolder;
import org.apache.stratos.autoscaler.PartitionContext;
import org.apache.stratos.autoscaler.deployment.policy.DeploymentPolicy;
import org.apache.stratos.autoscaler.partition.PartitionManager;
import org.apache.stratos.autoscaler.policy.PolicyManager;
import org.apache.stratos.autoscaler.policy.model.AutoscalePolicy;
import org.apache.stratos.autoscaler.rule.AutoscalerRuleEvaluator;
import org.apache.stratos.autoscaler.util.AutoScalerConstants;
import org.apache.stratos.autoscaler.util.ConfUtil;
import org.apache.stratos.messaging.domain.topology.ClusterStatus;
import org.apache.stratos.messaging.event.topology.ClusterRemovedEvent;

/**
 * Is responsible for monitoring a service cluster. This runs periodically
 * and perform minimum instance check and scaling check using the underlying
 * rules engine.
 */
public class VMLbClusterMonitor extends VMClusterMonitor {

    private static final Log log = LogFactory.getLog(VMLbClusterMonitor.class);

    public VMLbClusterMonitor(String clusterId, String serviceId, DeploymentPolicy deploymentPolicy,
                              AutoscalePolicy autoscalePolicy) {
        super(clusterId, serviceId, new AutoscalerRuleEvaluator(),
              deploymentPolicy, autoscalePolicy,
              new ConcurrentHashMap<String, NetworkPartitionContext>());
        readConfigurations();
    }

    @Override
    public void run() {

        if (log.isDebugEnabled()) {
            log.debug("VMLbClusterMonitor is running.. " + this.toString());
        }
        try {
            if (!ClusterStatus.In_Maintenance.equals(getStatus())) {
                monitor();
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("VMLbClusterMonitor is suspended as the cluster is in " +
                              ClusterStatus.In_Maintenance + " mode......");
                }
            }
        } catch (Exception e) {
            log.error("VMLbClusterMonitor : Monitor failed. " + this.toString(), e);
        }
    }

    @Override
    protected void monitor() {
        // TODO make this concurrent
        for (NetworkPartitionContext networkPartitionContext : networkPartitionCtxts.values()) {

            // minimum check per partition
            for (PartitionContext partitionContext : networkPartitionContext.getPartitionCtxts()
                    .values()) {

                if (partitionContext != null) {
                    getMinCheckKnowledgeSession().setGlobal("clusterId", getClusterId());
                    getMinCheckKnowledgeSession().setGlobal("isPrimary", false);

                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Running minimum check for partition %s ",
                                                partitionContext.getPartitionId()));
                    }

                    minCheckFactHandle =
                            AutoscalerRuleEvaluator.evaluateMinCheck(getMinCheckKnowledgeSession(),
                                                                     minCheckFactHandle,
                                                                     partitionContext);
                    // start only in the first partition context
                    break;
                }

            }

        }
    }

    @Override
    public void destroy() {
        getMinCheckKnowledgeSession().dispose();
        getMinCheckKnowledgeSession().dispose();
        setDestroyed(true);
        stopScheduler();
        if (log.isDebugEnabled()) {
            log.debug("VMLbClusterMonitor Drools session has been disposed. " + this.toString());
        }
    }

    @Override
    protected void readConfigurations() {
        XMLConfiguration conf = ConfUtil.getInstance(null).getConfiguration();
        int monitorInterval = conf.getInt(AutoScalerConstants.AUTOSCALER_MONITOR_INTERVAL, 90000);
        setMonitorIntervalMilliseconds(monitorInterval);
        if (log.isDebugEnabled()) {
            log.debug("VMLbClusterMonitor task interval: " + getMonitorIntervalMilliseconds());
        }
    }

    @Override
    public void handleClusterRemovedEvent(
            ClusterRemovedEvent clusterRemovedEvent) {

        String deploymentPolicy = clusterRemovedEvent.getDeploymentPolicy();
        String clusterId = clusterRemovedEvent.getClusterId();
        DeploymentPolicy depPolicy = PolicyManager.getInstance().getDeploymentPolicy(deploymentPolicy);
        if (depPolicy != null) {
            List<NetworkPartitionLbHolder> lbHolders = PartitionManager.getInstance()
                    .getNetworkPartitionLbHolders(depPolicy);

            for (NetworkPartitionLbHolder networkPartitionLbHolder : lbHolders) {
                // removes lb cluster ids
                boolean isRemoved = networkPartitionLbHolder.removeLbClusterId(clusterId);
                if (isRemoved) {
                    log.info("Removed the lb cluster [id]:"
                             + clusterId
                             + " reference from Network Partition [id]: "
                             + networkPartitionLbHolder
                            .getNetworkPartitionId());

                }
                if (log.isDebugEnabled()) {
                    log.debug(networkPartitionLbHolder);
                }

            }
        }
    }

    @Override
    public String toString() {
        return "VMLbClusterMonitor [clusterId=" + getClusterId() + ", serviceId=" + getServiceId() + "]";
    }
}
