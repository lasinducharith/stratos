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
package org.apache.stratos.autoscaler.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.NetworkPartitionLbHolder;
import org.apache.stratos.autoscaler.deployment.policy.DeploymentPolicy;
import org.apache.stratos.autoscaler.exception.AutoScalerException;
import org.apache.stratos.autoscaler.kubernetes.KubernetesManager;
import org.apache.stratos.autoscaler.message.receiver.health.AutoscalerHealthStatEventReceiver;
import org.apache.stratos.autoscaler.message.receiver.topology.AutoscalerTopologyEventReceiver;
import org.apache.stratos.autoscaler.partition.PartitionManager;
import org.apache.stratos.autoscaler.policy.PolicyManager;
import org.apache.stratos.autoscaler.policy.model.AutoscalePolicy;
import org.apache.stratos.autoscaler.registry.RegistryManager;
import org.apache.stratos.autoscaler.util.ServiceReferenceHolder;
import org.apache.stratos.cloud.controller.stub.deployment.partition.Partition;
import org.apache.stratos.common.kubernetes.KubernetesGroup;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.registry.api.RegistryException;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.util.Iterator;
import java.util.List;

/**
 * @scr.component name=org.apache.stratos.autoscaler.internal.AutoscalerServerComponent"
 * immediate="true"
 * @scr.reference name="registry.service"
 * interface=
 * "org.wso2.carbon.registry.core.service.RegistryService"
 * cardinality="1..1" policy="dynamic" bind="setRegistryService"
 * unbind="unsetRegistryService"
 */

public class AutoscalerServerComponent {

    private static final Log log = LogFactory.getLog(AutoscalerServerComponent.class);
    AutoscalerTopologyEventReceiver asTopologyReceiver;
//    TopicSubscriber healthStatTopicSubscriber;
    AutoscalerHealthStatEventReceiver autoscalerHealthStatEventReceiver;
    private static final int SUPER_TENANT_ID = MultitenantConstants.SUPER_TENANT_ID;

    protected void activate(ComponentContext componentContext) throws Exception {
        try {
            // Start topology receiver
        	asTopologyReceiver = new AutoscalerTopologyEventReceiver();
            Thread topologyTopicSubscriberThread = new Thread(asTopologyReceiver);
            topologyTopicSubscriberThread.start();
            if (log.isDebugEnabled()) {
                log.debug("Topology receiver thread started");
            }
//            healthStatTopicSubscriber = new TopicSubscriber(Constants.HEALTH_STAT_TOPIC);
//            healthStatTopicSubscriber.setMessageListener(new HealthEventMessageReceiver());
//            Thread healthStatTopicSubscriberThread = new Thread(healthStatTopicSubscriber);
//            healthStatTopicSubscriberThread.start();
//            if (log.isDebugEnabled()) {
//                log.debug("Health event message receiver thread started");
//            }


            // Start health stat receiver
            autoscalerHealthStatEventReceiver = new AutoscalerHealthStatEventReceiver();
            Thread healthDelegatorThread = new Thread(autoscalerHealthStatEventReceiver);
            healthDelegatorThread.start();
            if (log.isDebugEnabled()) {
                log.debug("Health message processor thread started");
            }

            // Adding the registry stored partitions to the information model
            List<Partition> partitions = RegistryManager.getInstance().retrievePartitions(SUPER_TENANT_ID);
            Iterator<Partition> partitionIterator = partitions.iterator();
            while (partitionIterator.hasNext()) {
                Partition partition = partitionIterator.next();
                PartitionManager.getInstance().addPartitionToInformationModel(SUPER_TENANT_ID, partition);
            }
            
            // Adding the network partitions stored in registry to the information model
            List<NetworkPartitionLbHolder> nwPartitionHolders = RegistryManager.getInstance().retrieveNetworkPartitionLbHolders();
            Iterator<NetworkPartitionLbHolder> nwPartitionIterator = nwPartitionHolders.iterator();
            while (nwPartitionIterator.hasNext()) {
                NetworkPartitionLbHolder nwPartition = nwPartitionIterator.next();
                PartitionManager.getInstance().addNetworkPartitionLbHolder(nwPartition);
            }
            
            List<AutoscalePolicy> asPolicies = RegistryManager.getInstance().retrieveASPolicies(SUPER_TENANT_ID);
            Iterator<AutoscalePolicy> asPolicyIterator = asPolicies.iterator();
            while (asPolicyIterator.hasNext()) {
                AutoscalePolicy asPolicy = asPolicyIterator.next();
                PolicyManager.getInstance().addASPolicyToInformationModel(SUPER_TENANT_ID, asPolicy);
            }

            List<DeploymentPolicy> depPolicies = RegistryManager.getInstance().retrieveDeploymentPolicies(SUPER_TENANT_ID);
            Iterator<DeploymentPolicy> depPolicyIterator = depPolicies.iterator();
            while (depPolicyIterator.hasNext()) {
                DeploymentPolicy depPolicy = depPolicyIterator.next();
                PolicyManager.getInstance().addDeploymentPolicyToInformationModel(SUPER_TENANT_ID, depPolicy);
            }

            // Adding KubernetesGroups stored in registry to the information model
            List<KubernetesGroup> kubernetesGroupList = RegistryManager.getInstance().retrieveKubernetesGroups(SUPER_TENANT_ID);
            Iterator<KubernetesGroup> kubernetesGroupIterator = kubernetesGroupList.iterator();
            while (kubernetesGroupIterator.hasNext()) {
                KubernetesGroup kubernetesGroup = kubernetesGroupIterator.next();
                KubernetesManager.getInstance().addNewKubernetesGroup(SUPER_TENANT_ID, kubernetesGroup);
            }

            if (log.isInfoEnabled()) {
                log.info("Autoscaler Server Component activated");
            }
        } catch (Throwable e) {
            log.error("Error in activating the autoscaler component ", e);
        }
    }

    protected void deactivate(ComponentContext context) {
    	asTopologyReceiver.terminate();
    	autoscalerHealthStatEventReceiver.terminate();
    }
    
    protected void setRegistryService(RegistryService registryService) {
        if (log.isDebugEnabled()) {
            log.debug("Setting the Registry Service");
        }
        try {
            ServiceReferenceHolder.getInstance().setRegistry(registryService.getGovernanceSystemRegistry());
        } catch (RegistryException e) {
            String msg = "Failed when retrieving Governance System Registry.";
            log.error(msg, e);
            throw new AutoScalerException(msg, e);
        }
    }

    protected void unsetRegistryService(RegistryService registryService) {
        if (log.isDebugEnabled()) {
            log.debug("Unsetting the Registry Service");
        }
        ServiceReferenceHolder.getInstance().setRegistry(null);
    }
}