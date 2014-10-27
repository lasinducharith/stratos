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

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.MemberStatsContext;
import org.apache.stratos.autoscaler.NetworkPartitionContext;
import org.apache.stratos.autoscaler.PartitionContext;
import org.apache.stratos.autoscaler.client.cloud.controller.CloudControllerClient;
import org.apache.stratos.autoscaler.deployment.policy.DeploymentPolicy;
import org.apache.stratos.autoscaler.exception.TerminationException;
import org.apache.stratos.autoscaler.policy.model.AutoscalePolicy;
import org.apache.stratos.autoscaler.rule.AutoscalerRuleEvaluator;
import org.apache.stratos.messaging.domain.topology.Cluster;
import org.apache.stratos.messaging.domain.topology.Member;
import org.apache.stratos.messaging.domain.topology.Service;
import org.apache.stratos.messaging.event.health.stat.AverageLoadAverageEvent;
import org.apache.stratos.messaging.event.health.stat.AverageMemoryConsumptionEvent;
import org.apache.stratos.messaging.event.health.stat.AverageRequestsInFlightEvent;
import org.apache.stratos.messaging.event.health.stat.GradientOfLoadAverageEvent;
import org.apache.stratos.messaging.event.health.stat.GradientOfMemoryConsumptionEvent;
import org.apache.stratos.messaging.event.health.stat.GradientOfRequestsInFlightEvent;
import org.apache.stratos.messaging.event.health.stat.MemberAverageLoadAverageEvent;
import org.apache.stratos.messaging.event.health.stat.MemberAverageMemoryConsumptionEvent;
import org.apache.stratos.messaging.event.health.stat.MemberFaultEvent;
import org.apache.stratos.messaging.event.health.stat.MemberGradientOfLoadAverageEvent;
import org.apache.stratos.messaging.event.health.stat.MemberGradientOfMemoryConsumptionEvent;
import org.apache.stratos.messaging.event.health.stat.MemberSecondDerivativeOfLoadAverageEvent;
import org.apache.stratos.messaging.event.health.stat.MemberSecondDerivativeOfMemoryConsumptionEvent;
import org.apache.stratos.messaging.event.health.stat.SecondDerivativeOfLoadAverageEvent;
import org.apache.stratos.messaging.event.health.stat.SecondDerivativeOfMemoryConsumptionEvent;
import org.apache.stratos.messaging.event.health.stat.SecondDerivativeOfRequestsInFlightEvent;
import org.apache.stratos.messaging.event.topology.ClusterRemovedEvent;
import org.apache.stratos.messaging.event.topology.MemberActivatedEvent;
import org.apache.stratos.messaging.event.topology.MemberMaintenanceModeEvent;
import org.apache.stratos.messaging.event.topology.MemberReadyToShutdownEvent;
import org.apache.stratos.messaging.event.topology.MemberStartedEvent;
import org.apache.stratos.messaging.event.topology.MemberTerminatedEvent;
import org.apache.stratos.messaging.message.receiver.topology.TopologyManager;

/**
 * Is responsible for monitoring a service cluster. This runs periodically
 * and perform minimum instance check and scaling check using the underlying
 * rules engine.
 */
abstract public class VMClusterMonitor extends AbstractClusterMonitor {

    private static final Log log = LogFactory.getLog(VMClusterMonitor.class);
    // Map<NetworkpartitionId, Network Partition Context>
    protected Map<String, NetworkPartitionContext> networkPartitionCtxts;
    protected DeploymentPolicy deploymentPolicy;
    protected AutoscalePolicy autoscalePolicy;

    protected VMClusterMonitor(int tenantId, String clusterId, String serviceId,
                               AutoscalerRuleEvaluator autoscalerRuleEvaluator,
                               DeploymentPolicy deploymentPolicy, AutoscalePolicy autoscalePolicy,
                               Map<String, NetworkPartitionContext> networkPartitionCtxts) {
        super(tenantId, clusterId, serviceId, autoscalerRuleEvaluator);
        this.deploymentPolicy = deploymentPolicy;
        this.autoscalePolicy = autoscalePolicy;
        this.networkPartitionCtxts = networkPartitionCtxts;
    }

    @Override
    public void handleAverageLoadAverageEvent(
            AverageLoadAverageEvent averageLoadAverageEvent) {

        String networkPartitionId = averageLoadAverageEvent.getNetworkPartitionId();
        String clusterId = averageLoadAverageEvent.getClusterId();
        float value = averageLoadAverageEvent.getValue();

        if (log.isDebugEnabled()) {
            log.debug(String.format("Avg load avg event: [cluster] %s [network-partition] %s [value] %s",
                                    clusterId, networkPartitionId, value));
        }

        NetworkPartitionContext networkPartitionContext = getNetworkPartitionCtxt(networkPartitionId);
        if (null != networkPartitionContext) {
            networkPartitionContext.setAverageLoadAverage(value);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Network partition context is not available for :" +
                                        " [network partition] %s", networkPartitionId));
            }
        }

    }

    @Override
    public void handleGradientOfLoadAverageEvent(
            GradientOfLoadAverageEvent gradientOfLoadAverageEvent) {

        String networkPartitionId = gradientOfLoadAverageEvent.getNetworkPartitionId();
        String clusterId = gradientOfLoadAverageEvent.getClusterId();
        float value = gradientOfLoadAverageEvent.getValue();
        if (log.isDebugEnabled()) {
            log.debug(String.format("Grad of load avg event: [cluster] %s [network-partition] %s [value] %s",
                                    clusterId, networkPartitionId, value));
        }
        NetworkPartitionContext networkPartitionContext = getNetworkPartitionCtxt(networkPartitionId);
        if (null != networkPartitionContext) {
            networkPartitionContext.setLoadAverageGradient(value);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Network partition context is not available for :" +
                                        " [network partition] %s", networkPartitionId));
            }
        }
    }

    @Override
    public void handleSecondDerivativeOfLoadAverageEvent(
            SecondDerivativeOfLoadAverageEvent secondDerivativeOfLoadAverageEvent) {

        String networkPartitionId = secondDerivativeOfLoadAverageEvent.getNetworkPartitionId();
        String clusterId = secondDerivativeOfLoadAverageEvent.getClusterId();
        float value = secondDerivativeOfLoadAverageEvent.getValue();
        if (log.isDebugEnabled()) {
            log.debug(String.format("Second Derivation of load avg event: [cluster] %s "
                                    + "[network-partition] %s [value] %s", clusterId, networkPartitionId, value));
        }
        NetworkPartitionContext networkPartitionContext = getNetworkPartitionCtxt(networkPartitionId);
        if (null != networkPartitionContext) {
            networkPartitionContext.setLoadAverageSecondDerivative(value);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Network partition context is not available for :" +
                                        " [network partition] %s", networkPartitionId));
            }
        }
    }

    @Override
    public void handleAverageMemoryConsumptionEvent(
            AverageMemoryConsumptionEvent averageMemoryConsumptionEvent) {

        String networkPartitionId = averageMemoryConsumptionEvent.getNetworkPartitionId();
        String clusterId = averageMemoryConsumptionEvent.getClusterId();
        float value = averageMemoryConsumptionEvent.getValue();
        if (log.isDebugEnabled()) {
            log.debug(String.format("Avg Memory Consumption event: [cluster] %s [network-partition] %s "
                                    + "[value] %s", clusterId, networkPartitionId, value));
        }
        NetworkPartitionContext networkPartitionContext = getNetworkPartitionCtxt(networkPartitionId);
        if (null != networkPartitionContext) {
            networkPartitionContext.setAverageMemoryConsumption(value);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String
                                  .format("Network partition context is not available for :"
                                          + " [network partition] %s", networkPartitionId));
            }
        }
    }

    @Override
    public void handleGradientOfMemoryConsumptionEvent(
            GradientOfMemoryConsumptionEvent gradientOfMemoryConsumptionEvent) {

        String networkPartitionId = gradientOfMemoryConsumptionEvent.getNetworkPartitionId();
        String clusterId = gradientOfMemoryConsumptionEvent.getClusterId();
        float value = gradientOfMemoryConsumptionEvent.getValue();
        if (log.isDebugEnabled()) {
            log.debug(String.format("Grad of Memory Consumption event: [cluster] %s "
                                    + "[network-partition] %s [value] %s", clusterId, networkPartitionId, value));
        }
        NetworkPartitionContext networkPartitionContext = getNetworkPartitionCtxt(networkPartitionId);
        if (null != networkPartitionContext) {
            networkPartitionContext.setMemoryConsumptionGradient(value);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Network partition context is not available for :" +
                                        " [network partition] %s", networkPartitionId));
            }
        }
    }

    @Override
    public void handleSecondDerivativeOfMemoryConsumptionEvent(
            SecondDerivativeOfMemoryConsumptionEvent secondDerivativeOfMemoryConsumptionEvent) {

        String networkPartitionId = secondDerivativeOfMemoryConsumptionEvent.getNetworkPartitionId();
        String clusterId = secondDerivativeOfMemoryConsumptionEvent.getClusterId();
        float value = secondDerivativeOfMemoryConsumptionEvent.getValue();
        if (log.isDebugEnabled()) {
            log.debug(String.format("Second Derivation of Memory Consumption event: [cluster] %s "
                                    + "[network-partition] %s [value] %s", clusterId, networkPartitionId, value));
        }
        NetworkPartitionContext networkPartitionContext = getNetworkPartitionCtxt(networkPartitionId);
        if (null != networkPartitionContext) {
            networkPartitionContext.setMemoryConsumptionSecondDerivative(value);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Network partition context is not available for :" +
                                        " [network partition] %s", networkPartitionId));
            }
        }
    }

    @Override
    public void handleAverageRequestsInFlightEvent(
            AverageRequestsInFlightEvent averageRequestsInFlightEvent) {

        String networkPartitionId = averageRequestsInFlightEvent.getNetworkPartitionId();
        String clusterId = averageRequestsInFlightEvent.getClusterId();
        float value = averageRequestsInFlightEvent.getValue();
        if (log.isDebugEnabled()) {
            log.debug(String.format("Average Rif event: [cluster] %s [network-partition] %s [value] %s",
                                    clusterId, networkPartitionId, value));
        }
        NetworkPartitionContext networkPartitionContext = getNetworkPartitionCtxt(networkPartitionId);
        if (null != networkPartitionContext) {
            networkPartitionContext.setAverageRequestsInFlight(value);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Network partition context is not available for :" +
                                        " [network partition] %s", networkPartitionId));
            }
        }
    }

    @Override
    public void handleGradientOfRequestsInFlightEvent(
            GradientOfRequestsInFlightEvent gradientOfRequestsInFlightEvent) {

        String networkPartitionId = gradientOfRequestsInFlightEvent.getNetworkPartitionId();
        String clusterId = gradientOfRequestsInFlightEvent.getClusterId();
        float value = gradientOfRequestsInFlightEvent.getValue();
        if (log.isDebugEnabled()) {
            log.debug(String.format("Gradient of Rif event: [cluster] %s [network-partition] %s [value] %s",
                                    clusterId, networkPartitionId, value));
        }
        NetworkPartitionContext networkPartitionContext = getNetworkPartitionCtxt(networkPartitionId);
        if (null != networkPartitionContext) {
            networkPartitionContext.setRequestsInFlightGradient(value);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Network partition context is not available for :" +
                                        " [network partition] %s", networkPartitionId));
            }
        }
    }

    @Override
    public void handleSecondDerivativeOfRequestsInFlightEvent(
            SecondDerivativeOfRequestsInFlightEvent secondDerivativeOfRequestsInFlightEvent) {

        String networkPartitionId = secondDerivativeOfRequestsInFlightEvent.getNetworkPartitionId();
        String clusterId = secondDerivativeOfRequestsInFlightEvent.getClusterId();
        float value = secondDerivativeOfRequestsInFlightEvent.getValue();
        if (log.isDebugEnabled()) {
            log.debug(String.format("Second derivative of Rif event: [cluster] %s "
                                    + "[network-partition] %s [value] %s", clusterId, networkPartitionId, value));
        }
        NetworkPartitionContext networkPartitionContext = getNetworkPartitionCtxt(networkPartitionId);
        if (null != networkPartitionContext) {
            networkPartitionContext.setRequestsInFlightSecondDerivative(value);
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Network partition context is not available for :" +
                                        " [network partition] %s", networkPartitionId));
            }
        }
    }

    @Override
    public void handleMemberAverageMemoryConsumptionEvent(
            MemberAverageMemoryConsumptionEvent memberAverageMemoryConsumptionEvent) {

        int tenantId = memberAverageMemoryConsumptionEvent.getTenantId();
        String memberId = memberAverageMemoryConsumptionEvent.getMemberId();
        Member member = getMemberByMemberId(tenantId, memberId);
        String networkPartitionId = getNetworkPartitionIdByMemberId(tenantId, memberId);
        NetworkPartitionContext networkPartitionCtxt = getNetworkPartitionCtxt(networkPartitionId);
        PartitionContext partitionCtxt = networkPartitionCtxt.getPartitionCtxt(member.getPartitionId());
        MemberStatsContext memberStatsContext = partitionCtxt.getMemberStatsContext(memberId);
        if (null == memberStatsContext) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Member context is not available for : [member] %s", memberId));
            }
            return;
        }
        float value = memberAverageMemoryConsumptionEvent.getValue();
        memberStatsContext.setAverageMemoryConsumption(value);
    }

    @Override
    public void handleMemberGradientOfMemoryConsumptionEvent(
            MemberGradientOfMemoryConsumptionEvent memberGradientOfMemoryConsumptionEvent) {

        int tenantId = memberGradientOfMemoryConsumptionEvent.getTenantId();
        String memberId = memberGradientOfMemoryConsumptionEvent.getMemberId();
        Member member = getMemberByMemberId(tenantId, memberId);
        String networkPartitionId = getNetworkPartitionIdByMemberId(tenantId, memberId);
        NetworkPartitionContext networkPartitionCtxt = getNetworkPartitionCtxt(networkPartitionId);
        PartitionContext partitionCtxt = networkPartitionCtxt.getPartitionCtxt(member.getPartitionId());
        MemberStatsContext memberStatsContext = partitionCtxt.getMemberStatsContext(memberId);
        if (null == memberStatsContext) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Member context is not available for : [member] %s", memberId));
            }
            return;
        }
        float value = memberGradientOfMemoryConsumptionEvent.getValue();
        memberStatsContext.setGradientOfMemoryConsumption(value);
    }

    @Override
    public void handleMemberSecondDerivativeOfMemoryConsumptionEvent(
            MemberSecondDerivativeOfMemoryConsumptionEvent memberSecondDerivativeOfMemoryConsumptionEvent) {

    }

    @Override
    public void handleMemberAverageLoadAverageEvent(
            MemberAverageLoadAverageEvent memberAverageLoadAverageEvent) {

        int tenantId = memberAverageLoadAverageEvent.getTenantId();
        String memberId = memberAverageLoadAverageEvent.getMemberId();
        Member member = getMemberByMemberId(tenantId, memberId);
        String networkPartitionId = getNetworkPartitionIdByMemberId(tenantId, memberId);
        NetworkPartitionContext networkPartitionCtxt = getNetworkPartitionCtxt(networkPartitionId);
        PartitionContext partitionCtxt = networkPartitionCtxt.getPartitionCtxt(member.getPartitionId());
        MemberStatsContext memberStatsContext = partitionCtxt.getMemberStatsContext(memberId);
        if (null == memberStatsContext) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Member context is not available for : [member] %s", memberId));
            }
            return;
        }
        float value = memberAverageLoadAverageEvent.getValue();
        memberStatsContext.setAverageLoadAverage(value);
    }

    @Override
    public void handleMemberGradientOfLoadAverageEvent(
            MemberGradientOfLoadAverageEvent memberGradientOfLoadAverageEvent) {

        int tenantId = memberGradientOfLoadAverageEvent.getTenantId();
        String memberId = memberGradientOfLoadAverageEvent.getMemberId();
        Member member = getMemberByMemberId(tenantId, memberId);
        String networkPartitionId = getNetworkPartitionIdByMemberId(tenantId, memberId);
        NetworkPartitionContext networkPartitionCtxt = getNetworkPartitionCtxt(networkPartitionId);
        PartitionContext partitionCtxt = networkPartitionCtxt.getPartitionCtxt(member.getPartitionId());
        MemberStatsContext memberStatsContext = partitionCtxt.getMemberStatsContext(memberId);
        if (null == memberStatsContext) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Member context is not available for : [member] %s", memberId));
            }
            return;
        }
        float value = memberGradientOfLoadAverageEvent.getValue();
        memberStatsContext.setGradientOfLoadAverage(value);
    }

    @Override
    public void handleMemberSecondDerivativeOfLoadAverageEvent(
            MemberSecondDerivativeOfLoadAverageEvent memberSecondDerivativeOfLoadAverageEvent) {
        int tenantId = memberSecondDerivativeOfLoadAverageEvent.getTenantId();
        String memberId = memberSecondDerivativeOfLoadAverageEvent.getMemberId();
        Member member = getMemberByMemberId(tenantId, memberId);
        String networkPartitionId = getNetworkPartitionIdByMemberId(tenantId, memberId);
        NetworkPartitionContext networkPartitionCtxt = getNetworkPartitionCtxt(networkPartitionId);
        PartitionContext partitionCtxt = networkPartitionCtxt.getPartitionCtxt(member.getPartitionId());
        MemberStatsContext memberStatsContext = partitionCtxt.getMemberStatsContext(memberId);
        if (null == memberStatsContext) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Member context is not available for : [member] %s", memberId));
            }
            return;
        }
        float value = memberSecondDerivativeOfLoadAverageEvent.getValue();
        memberStatsContext.setSecondDerivativeOfLoadAverage(value);
    }

    @Override
    public void handleMemberFaultEvent(MemberFaultEvent memberFaultEvent) {

        int tenantId = memberFaultEvent.getTenantId();
        String memberId = memberFaultEvent.getMemberId();
        Member member = getMemberByMemberId(tenantId, memberId);
        if (null == member) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Member not found in the Topology: [member] %s", memberId));
            }
            return;
        }
        if (!member.isActive()) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Member activated event has not received for the member %s. "
                                        + "Therefore ignoring" + " the member fault health stat", memberId));
            }
            return;
        }

        NetworkPartitionContext nwPartitionCtxt;
        nwPartitionCtxt = getNetworkPartitionCtxt(member);
        String partitionId = getPartitionOfMember(tenantId, memberId);
        PartitionContext partitionCtxt = nwPartitionCtxt.getPartitionCtxt(partitionId);
        if (!partitionCtxt.activeMemberExist(memberId)) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Could not find the active member in partition context, "
                                        + "[member] %s ", memberId));
            }
            return;
        }
        // terminate the faulty member
        CloudControllerClient ccClient = CloudControllerClient.getInstance();
        try {
            ccClient.terminate(memberFaultEvent.getTenantId(), memberId);
        } catch (TerminationException e) {
            String msg = "TerminationException " + e.getLocalizedMessage();
            log.error(msg, e);
        }
        // remove from active member list
        partitionCtxt.removeActiveMemberById(memberId);
        if (log.isInfoEnabled()) {
            String clusterId = memberFaultEvent.getClusterId();
            log.info(String.format("Faulty member is terminated and removed from the active members list: "
                                   + "[member] %s [partition] %s [cluster] %s ", memberId, partitionId, clusterId));
        }
    }

    @Override
    public void handleMemberStartedEvent(
            MemberStartedEvent memberStartedEvent) {

    }

    @Override
    public void handleMemberActivatedEvent(
            MemberActivatedEvent memberActivatedEvent) {

        String networkPartitionId = memberActivatedEvent.getNetworkPartitionId();
        String partitionId = memberActivatedEvent.getPartitionId();
        String memberId = memberActivatedEvent.getMemberId();
        NetworkPartitionContext networkPartitionCtxt = getNetworkPartitionCtxt(networkPartitionId);
        PartitionContext partitionContext;
        partitionContext = networkPartitionCtxt.getPartitionCtxt(partitionId);
        partitionContext.addMemberStatsContext(new MemberStatsContext(memberId));
        if (log.isInfoEnabled()) {
            log.info(String.format("Member stat context has been added successfully: "
                                   + "[member] %s", memberId));
        }
        partitionContext.movePendingMemberToActiveMembers(memberId);
    }

    @Override
    public void handleMemberMaintenanceModeEvent(
            MemberMaintenanceModeEvent maintenanceModeEvent) {

        String networkPartitionId = maintenanceModeEvent.getNetworkPartitionId();
        String partitionId = maintenanceModeEvent.getPartitionId();
        String memberId = maintenanceModeEvent.getMemberId();
        NetworkPartitionContext networkPartitionCtxt = getNetworkPartitionCtxt(networkPartitionId);
        PartitionContext partitionContext = networkPartitionCtxt.getPartitionCtxt(partitionId);
        partitionContext.addMemberStatsContext(new MemberStatsContext(memberId));
        if (log.isDebugEnabled()) {
            log.debug(String.format("Member has been moved as pending termination: "
                                    + "[member] %s", memberId));
        }
        partitionContext.moveActiveMemberToTerminationPendingMembers(memberId);
    }

    @Override
    public void handleMemberReadyToShutdownEvent(
            MemberReadyToShutdownEvent memberReadyToShutdownEvent) {

        NetworkPartitionContext nwPartitionCtxt;
        String networkPartitionId = memberReadyToShutdownEvent.getNetworkPartitionId();
        nwPartitionCtxt = getNetworkPartitionCtxt(networkPartitionId);

        // start a new member in the same Partition
        String memberId = memberReadyToShutdownEvent.getMemberId();
        String partitionId = getPartitionOfMember(memberReadyToShutdownEvent.getTenantId(), memberId);
        PartitionContext partitionCtxt = nwPartitionCtxt.getPartitionCtxt(partitionId);
        // terminate the shutdown ready member
        CloudControllerClient ccClient = CloudControllerClient.getInstance();
        try {
            ccClient.terminate(memberReadyToShutdownEvent.getTenantId(), memberId);
            // remove from active member list
            partitionCtxt.removeActiveMemberById(memberId);

            String clusterId = memberReadyToShutdownEvent.getClusterId();
            log.info(String.format("Member is terminated and removed from the active members list: "
                                   + "[member] %s [partition] %s [cluster] %s ", memberId, partitionId, clusterId));
        } catch (TerminationException e) {
            String msg = "TerminationException" + e.getLocalizedMessage();
            log.error(msg, e);
        }
    }

    @Override
    public void handleMemberTerminatedEvent(
            MemberTerminatedEvent memberTerminatedEvent) {

        String networkPartitionId = memberTerminatedEvent.getNetworkPartitionId();
        String memberId = memberTerminatedEvent.getMemberId();
        String partitionId = memberTerminatedEvent.getPartitionId();
        NetworkPartitionContext networkPartitionContext = getNetworkPartitionCtxt(networkPartitionId);
        PartitionContext partitionContext = networkPartitionContext.getPartitionCtxt(partitionId);
        partitionContext.removeMemberStatsContext(memberId);

        if (partitionContext.removeTerminationPendingMember(memberId)) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Member is removed from termination pending members list: "
                                        + "[member] %s", memberId));
            }
        } else if (partitionContext.removePendingMember(memberId)) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Member is removed from pending members list: "
                                        + "[member] %s", memberId));
            }
        } else if (partitionContext.removeActiveMemberById(memberId)) {
            log.warn(String.format("Member is in the wrong list and it is removed from "
                                   + "active members list", memberId));
        } else if (partitionContext.removeObsoleteMember(memberId)) {
            log.warn(String.format("Member's obsolated timeout has been expired and "
                                   + "it is removed from obsolated members list", memberId));
        } else {
            log.warn(String.format("Member is not available in any of the list active, "
                                   + "pending and termination pending", memberId));
        }

        if (log.isInfoEnabled()) {
            log.info(String.format("Member stat context has been removed successfully: "
                                   + "[member] %s", memberId));
        }
    }

    @Override
    public void handleClusterRemovedEvent(
            ClusterRemovedEvent clusterRemovedEvent) {

    }

    private String getNetworkPartitionIdByMemberId(int tenantId, String memberId) {
        for (Service service : TopologyManager.getTopology(tenantId).getServices()) {
            for (Cluster cluster : service.getClusters()) {
                if (cluster.memberExists(memberId)) {
                    return cluster.getMember(memberId).getNetworkPartitionId();
                }
            }
        }
        return null;
    }

    private Member getMemberByMemberId(int tenantId, String memberId) {
        try {
            TopologyManager.acquireReadLock();
            for (Service service : TopologyManager.getTopology(tenantId).getServices()) {
                for (Cluster cluster : service.getClusters()) {
                    if (cluster.memberExists(memberId)) {
                        return cluster.getMember(memberId);
                    }
                }
            }
            return null;
        } finally {
            TopologyManager.releaseReadLock();
        }
    }

    public NetworkPartitionContext getNetworkPartitionCtxt(Member member) {
        log.info("***** getNetworkPartitionCtxt " + member.getNetworkPartitionId());
        String networkPartitionId = member.getNetworkPartitionId();
        if (networkPartitionCtxts.containsKey(networkPartitionId)) {
            log.info("returnnig network partition context " + networkPartitionCtxts.get(networkPartitionId));
            return networkPartitionCtxts.get(networkPartitionId);
        }
        log.info("returning null getNetworkPartitionCtxt");
        return null;
    }

    public String getPartitionOfMember(int tenantId, String memberId) {
        for (Service service : TopologyManager.getTopology(tenantId).getServices()) {
            for (Cluster cluster : service.getClusters()) {
                if (cluster.memberExists(memberId)) {
                    return cluster.getMember(memberId).getPartitionId();
                }
            }
        }
        return null;
    }

    public DeploymentPolicy getDeploymentPolicy() {
        return deploymentPolicy;
    }

    public void setDeploymentPolicy(DeploymentPolicy deploymentPolicy) {
        this.deploymentPolicy = deploymentPolicy;
    }

    public AutoscalePolicy getAutoscalePolicy() {
        return autoscalePolicy;
    }

    public void setAutoscalePolicy(AutoscalePolicy autoscalePolicy) {
        this.autoscalePolicy = autoscalePolicy;
    }

    public Map<String, NetworkPartitionContext> getNetworkPartitionCtxts() {
        return networkPartitionCtxts;
    }

    public NetworkPartitionContext getNetworkPartitionCtxt(String networkPartitionId) {
        return networkPartitionCtxts.get(networkPartitionId);
    }

    public void setPartitionCtxt(Map<String, NetworkPartitionContext> partitionCtxt) {
        this.networkPartitionCtxts = partitionCtxt;
    }

    public boolean partitionCtxtAvailable(String partitionId) {
        return networkPartitionCtxts.containsKey(partitionId);
    }

    public void addNetworkPartitionCtxt(NetworkPartitionContext ctxt) {
        this.networkPartitionCtxts.put(ctxt.getId(), ctxt);
    }

    public NetworkPartitionContext getPartitionCtxt(String id) {
        return this.networkPartitionCtxts.get(id);
    }
}