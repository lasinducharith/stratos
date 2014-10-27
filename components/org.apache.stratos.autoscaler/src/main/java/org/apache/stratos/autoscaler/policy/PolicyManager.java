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

package org.apache.stratos.autoscaler.policy;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.deployment.policy.DeploymentPolicy;
import org.apache.stratos.autoscaler.exception.AutoScalerException;
import org.apache.stratos.autoscaler.exception.InvalidPartitionException;
import org.apache.stratos.autoscaler.exception.InvalidPolicyException;
import org.apache.stratos.autoscaler.partition.PartitionManager;
import org.apache.stratos.autoscaler.policy.model.AutoscalePolicy;
import org.apache.stratos.autoscaler.registry.RegistryManager;
import org.apache.stratos.cloud.controller.stub.deployment.partition.Partition;

/**
 * Manager class for the purpose of managing Autoscale/Deployment policy definitions.
 */
public class PolicyManager {

    private static final Log log = LogFactory.getLog(PolicyManager.class);

    private static Map<Integer, Map<String, AutoscalePolicy>> tenantIdToAutoscalePolicyListMap = new HashMap<Integer, Map<String, AutoscalePolicy>>();

    private static Map<Integer, Map<String, DeploymentPolicy>> tenantIdToDeploymentPolicyListMap = new HashMap<Integer, Map<String, DeploymentPolicy>>();
    
    /* An instance of a PolicyManager is created when the class is loaded. 
     * Since the class is loaded only once, it is guaranteed that an object of 
     * PolicyManager is created only once. Hence it is singleton.
     */

    private static class InstanceHolder {
        private static final PolicyManager INSTANCE = new PolicyManager();
    }

    public static PolicyManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private PolicyManager() {
    }

    // Add the policy to information model and persist.
    public boolean deployAutoscalePolicy(int tenantId, AutoscalePolicy policy) throws InvalidPolicyException {
        if (StringUtils.isEmpty(policy.getId())) {
            throw new AutoScalerException("AutoScaling policy id can not be empty");
        }
        this.addASPolicyToInformationModel(tenantId, policy);
        RegistryManager.getInstance().persistAutoscalerPolicy(tenantId, policy);
        if (log.isInfoEnabled()) {
            log.info(String.format("AutoScaling policy is deployed successfully: [id] %s", policy.getId()));
        }
        return true;
    }

    // Add the deployment policy to information model and persist.
    public boolean deployDeploymentPolicy(int tenantId, DeploymentPolicy policy) throws InvalidPolicyException {
        if (StringUtils.isEmpty(policy.getId())) {
            throw new AutoScalerException("Deploying policy id can not be empty");
        }
        try {
            if (log.isInfoEnabled()) {
                log.info(String.format("Deploying deployment policy: [id] %s", policy.getId()));
            }
            fillPartitions(tenantId, policy);
        } catch (InvalidPartitionException e) {
            log.error(e);
            throw new InvalidPolicyException(String.format("Deployment policy is invalid: [id] %s", policy.getId()), e);
        }

        this.addDeploymentPolicyToInformationModel(tenantId, policy);
        RegistryManager.getInstance().persistDeploymentPolicy(tenantId, policy);

        if (log.isInfoEnabled()) {
            log.info(String.format("Deployment policy is deployed successfully: [id] %s", policy.getId()));
        }
        return true;
    }

    private void fillPartitions(int tenantId, DeploymentPolicy deploymentPolicy) throws InvalidPartitionException {
        PartitionManager partitionMgr = PartitionManager.getInstance();
        for (Partition partition : deploymentPolicy.getAllPartitions()) {
            String partitionId = partition.getId();
            if ((partitionId == null) || (!partitionMgr.partitionExist(tenantId, partitionId))) {
                String msg = "Could not find partition: [id] " + partitionId + ". " +
                        "Please deploy the partitions before deploying the deployment policies.";
                throw new InvalidPartitionException(msg);
            }

            fillPartition(partition, PartitionManager.getInstance().getPartitionById(tenantId, partitionId));
        }
    }

    private static void fillPartition(Partition destPartition, Partition srcPartition) {
        if (srcPartition.getProvider() == null)
            throw new RuntimeException("Provider is not set in the deployed partition");

        if (log.isDebugEnabled()) {
            log.debug(String.format("Setting provider for partition: [id] %s [provider] %s", destPartition.getId(), srcPartition.getProvider()));
        }
        destPartition.setProvider(srcPartition.getProvider());

        if (log.isDebugEnabled()) {
            log.debug(String.format("Setting properties for partition: [id] %s [properties] %s", destPartition.getId(), srcPartition.getProperties()));
        }
        destPartition.setProperties(srcPartition.getProperties());
    }

    public void addASPolicyToInformationModel(int tenantId, AutoscalePolicy asPolicy) throws InvalidPolicyException {

        Map<String, AutoscalePolicy> autoscalePolicyListMap;

        if (!tenantIdToAutoscalePolicyListMap.containsKey(tenantId)) {
            autoscalePolicyListMap = new HashMap<String, AutoscalePolicy>();
        } else {
            autoscalePolicyListMap = tenantIdToAutoscalePolicyListMap.get(tenantId);
        }

        if (!autoscalePolicyListMap.containsKey(asPolicy.getId())) {
            if (log.isDebugEnabled()) {
                log.debug("Adding policy :" + asPolicy.getId() + " for tenant :" + tenantId);
            }
            autoscalePolicyListMap.put(asPolicy.getId(), asPolicy);
            tenantIdToAutoscalePolicyListMap.put(tenantId, autoscalePolicyListMap);
        } else {
            String errMsg = "Specified policy [" + asPolicy.getId() + "] already exists for tenant [" + tenantId + "]";
            log.error(errMsg);
            throw new InvalidPolicyException(errMsg);
        }
    }

    /**
     * Removes the specified policy
     *
     * @param policy
     * @throws InvalidPolicyException
     */
    public void undeployAutoscalePolicy(int tenantId, String policy) throws InvalidPolicyException {

        Map<String, AutoscalePolicy> autoscalePolicyListMap;
        if (!tenantIdToAutoscalePolicyListMap.containsKey(tenantId)) {
            autoscalePolicyListMap = tenantIdToAutoscalePolicyListMap.get(tenantId);
        if (autoscalePolicyListMap.containsKey(policy)) {
            if (log.isDebugEnabled()) {
                log.debug("Removing policy :" + policy + " for tenant :" + tenantId);
            }
            autoscalePolicyListMap.remove(policy);
            tenantIdToAutoscalePolicyListMap.put(tenantId, autoscalePolicyListMap);
            RegistryManager.getInstance().removeAutoscalerPolicy(tenantId, this.getAutoscalePolicy(tenantId, policy));
        } else {
            throw new InvalidPolicyException("No such policy [" + policy + "] exists for tenant [" + tenantId + "]");
        }
       }
    }

    /**
     * Returns an array of the Autoscale policies contained in this manager.
     *
     * @return
     */
    public AutoscalePolicy[] getAutoscalePolicyList(int tenantId) {
        if (tenantIdToAutoscalePolicyListMap.containsKey(tenantId)){
            return tenantIdToAutoscalePolicyListMap.get(tenantId).values().toArray(new AutoscalePolicy[0]);
        }
        return null;
    }

    /**
     * Returns the autoscale policy to which the specified id is mapped or null
     *
     * @param id
     * @return
     */
    public AutoscalePolicy getAutoscalePolicy(int tenantId, String id) {
        if (tenantIdToAutoscalePolicyListMap.containsKey(tenantId)) {
            return tenantIdToAutoscalePolicyListMap.get(tenantId).get(id);
        }
        return null;
    }

    // Add the deployment policy to As in memory information model. Does not persist.
    public void addDeploymentPolicyToInformationModel(int tenantId, DeploymentPolicy deploymentPolicy) throws InvalidPolicyException {

        Map<String, DeploymentPolicy> deploymentPolicyListMap;

        if (!tenantIdToDeploymentPolicyListMap.containsKey(tenantId)) {
            deploymentPolicyListMap = new HashMap<String, DeploymentPolicy>();
        } else {
            deploymentPolicyListMap = tenantIdToDeploymentPolicyListMap.get(tenantId);
        }

        if (!deploymentPolicyListMap.containsKey(deploymentPolicy.getId())) {
            if (log.isDebugEnabled()) {
                log.debug("Adding policy :" + deploymentPolicy.getId() + " for tenant :" + tenantId);
            }
            deploymentPolicyListMap.put(deploymentPolicy.getId(), deploymentPolicy);
            tenantIdToDeploymentPolicyListMap.put(tenantId, deploymentPolicyListMap);
        } else {
            String errMsg = "Specified policy [" + deploymentPolicy.getId() + "] already exists already exists for tenant [" + tenantId + "]";
            log.error(errMsg);
            throw new InvalidPolicyException(errMsg);
        }
    }

    /**
     * Removes the specified policy
     *
     * @param deploymentPolicy
     * @throws InvalidPolicyException
     */
    public void undeployDeploymentPolicy(int tenantId, String deploymentPolicy) throws InvalidPolicyException {
        Map<String, DeploymentPolicy> deploymentPolicyListMap;
        if (!tenantIdToDeploymentPolicyListMap.containsKey(tenantId)) {
            deploymentPolicyListMap = tenantIdToDeploymentPolicyListMap.get(tenantId);
            if (tenantIdToDeploymentPolicyListMap.containsKey(deploymentPolicy)) {
                if (log.isDebugEnabled()) {
                    log.debug("Removing deployment policy :" + deploymentPolicy + " for tenant :" + tenantId);
                }
                DeploymentPolicy depPolicy = this.getDeploymentPolicy(tenantId, deploymentPolicy);
                // undeploy network partitions this deployment policy.
                PartitionManager.getInstance().undeployNetworkPartitions(depPolicy);
                // undeploy the deployment policy.
                RegistryManager.getInstance().removeDeploymentPolicy(tenantId, depPolicy);
                // remove from the infromation model.
                deploymentPolicyListMap.remove(deploymentPolicy);
                tenantIdToDeploymentPolicyListMap.put(tenantId, deploymentPolicyListMap);
            } else {
                throw new InvalidPolicyException("No such policy [" + deploymentPolicy + "] exists for tenant [" + tenantId + "]");
            }
        }
    }
    /**
     * Returns an array of the Deployment policies contained in this manager.
     *
     * @return
     */
    public DeploymentPolicy[] getDeploymentPolicyList(int tenantId) {
        if(tenantIdToDeploymentPolicyListMap.containsKey(tenantId)) {
            return tenantIdToDeploymentPolicyListMap.get(tenantId).values().toArray(new DeploymentPolicy[0]);
        }
        return null;
    }

    /**
     * Returns the deployment policy to which the specified id is mapped or null
     *
     * @param id
     * @return
     */
    public DeploymentPolicy getDeploymentPolicy(int tenantId, String id) {
        if (tenantIdToDeploymentPolicyListMap.containsKey(tenantId)) {
            return tenantIdToDeploymentPolicyListMap.get(tenantId).get(id);
        }
        return  null;
    }

}
