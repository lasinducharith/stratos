/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.stratos.messaging.message.processor.topology;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.messaging.domain.topology.Cluster;
import org.apache.stratos.messaging.domain.topology.Member;
import org.apache.stratos.messaging.domain.topology.MemberStatus;
import org.apache.stratos.messaging.domain.topology.Service;
import org.apache.stratos.messaging.domain.topology.Topology;
import org.apache.stratos.messaging.event.topology.MemberStartedEvent;
import org.apache.stratos.messaging.message.filter.topology.TopologyClusterFilter;
import org.apache.stratos.messaging.message.filter.topology.TopologyMemberFilter;
import org.apache.stratos.messaging.message.filter.topology.TopologyServiceFilter;
import org.apache.stratos.messaging.message.processor.MessageProcessor;
import org.apache.stratos.messaging.message.receiver.topology.TopologyManager;
import org.apache.stratos.messaging.util.Util;

public class MemberStartedMessageProcessor extends MessageProcessor {

    private static final Log log = LogFactory.getLog(MemberStartedMessageProcessor.class);
    private MessageProcessor nextProcessor;

    @Override
    public void setNext(MessageProcessor nextProcessor) {
        this.nextProcessor = nextProcessor;
    }

    @Override
    public boolean process(String type, String message, Object object) {

        if (MemberStartedEvent.class.getName().equals(type)) {
            // Parse complete message and build event
            MemberStartedEvent event = (MemberStartedEvent) Util.jsonToObject(message, MemberStartedEvent.class);
            Topology topology = TopologyManager.getTopology(event.getTenantId());
            // Return if topology has not been initialized
            if (!topology.isInitialized())
                return false;

            // Apply service filter
            if (TopologyServiceFilter.getInstance().isActive()) {
                if (TopologyServiceFilter.getInstance().serviceNameExcluded(event.getServiceName())) {
                    // Service is excluded, do not update topology or fire event
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Service is excluded: [service] %s", event.getServiceName()));
                    }
                    return false;
                }
            }

            // Apply cluster filter
            if (TopologyClusterFilter.getInstance().isActive()) {
                if (TopologyClusterFilter.getInstance().clusterIdExcluded(event.getClusterId())) {
                    // Cluster is excluded, do not update topology or fire event
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Cluster is excluded: [cluster] %s", event.getClusterId()));
                    }
                    return false;
                }
            }

            // Validate event against the existing topology
            Service service = topology.getService(event.getServiceName());
            if (service == null) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Service does not exist: [service] %s",
                            event.getServiceName()));
                }
                return false;
            }
            Cluster cluster = service.getCluster(event.getClusterId());
            if (cluster == null) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Cluster does not exist: [service] %s [cluster] %s",
                            event.getServiceName(), event.getClusterId()));
                }
                return false;
            }
            Member member = cluster.getMember(event.getMemberId());
            if (member == null) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Member does not exist: [service] %s [cluster] %s [member] %s",
                            event.getServiceName(),
                            event.getClusterId(),
                            event.getMemberId()));
                }
                return false;
            }

            // Apply member filter
            if(TopologyMemberFilter.getInstance().isActive()) {
                if(TopologyMemberFilter.getInstance().lbClusterIdExcluded(member.getLbClusterId())) {
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Member is excluded: [lb-cluster-id] %s", member.getLbClusterId()));
                    }
                    return false;
                }
            }

            if (member.getStatus() == MemberStatus.Starting) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Member already started: [service] %s [cluster] %s [member] %s",
                            event.getServiceName(),
                            event.getClusterId(),
                            event.getMemberId()));
                }
            } else {
            	
            	// Apply changes to the topology
            	member.setStatus(MemberStatus.Starting);
            	
            	if (log.isInfoEnabled()) {
            		log.info(String.format("Member started: [service] %s [cluster] %s [member] %s",
            				event.getServiceName(),
            				event.getClusterId(),
            				event.getMemberId()));
            	}
            }


            // Notify event listeners
            notifyEventListeners(event);
            return true;
        } else {
            if (nextProcessor != null) {
                // ask the next processor to take care of the message.
                return nextProcessor.process(type, message, null);
            } else {
                throw new RuntimeException(String.format("Failed to process message using available message processors: [type] %s [body] %s", type, message));
            }
        }
    }
}
