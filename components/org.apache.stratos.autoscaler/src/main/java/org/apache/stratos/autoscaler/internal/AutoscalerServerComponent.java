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
import org.apache.stratos.autoscaler.kubernetes.KubernetesManager;
import org.apache.stratos.autoscaler.message.receiver.health.AutoscalerHealthStatEventReceiver;
import org.apache.stratos.autoscaler.message.receiver.topology.AutoscalerTopologyEventReceiver;
import org.apache.stratos.autoscaler.partition.PartitionManager;
import org.apache.stratos.autoscaler.policy.PolicyManager;
import org.apache.stratos.autoscaler.registry.RegistryManager;
import org.apache.stratos.autoscaler.util.ServiceReferenceHolder;
import org.apache.stratos.common.kubernetes.KubernetesGroup;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.registry.core.service.RegistryService;

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
            PartitionManager.getInstance().loadPartitionsToInformationModel();
            
            // Adding the network partitions stored in registry to the information model
            PartitionManager.getInstance().loadNetworkPartitionsToInformationModel();
            
            // Adding the registry stored autoscaling policies to the information model
            PolicyManager.getInstance().loadASPoliciesToInformationModel();
            
            // Adding the registry stored deployment policies to the information model
            PolicyManager.getInstance().loadDeploymentPoliciesToInformationModel();

            // Adding KubernetesGroups stored in registry to the information model
            List<KubernetesGroup> kubernetesGroupList = RegistryManager.getInstance(CarbonContext.getThreadLocalCarbonContext().getTenantId()).retrieveKubernetesGroups();
            Iterator<KubernetesGroup> kubernetesGroupIterator = kubernetesGroupList.iterator();
            while (kubernetesGroupIterator.hasNext()) {
                KubernetesGroup kubernetesGroup = kubernetesGroupIterator.next();
                KubernetesManager.getInstance().addNewKubernetesGroup(kubernetesGroup);
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
        ServiceReferenceHolder.getInstance().setRegistryService(registryService);
    }

    protected void unsetRegistryService(RegistryService registryService) {
        if (log.isDebugEnabled()) {
            log.debug("Unsetting the Registry Service");
        }
        ServiceReferenceHolder.getInstance().setRegistryService(null);
    }
}