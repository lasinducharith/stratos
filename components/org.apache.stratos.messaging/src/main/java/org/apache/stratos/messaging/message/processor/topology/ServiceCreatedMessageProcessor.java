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
import org.apache.stratos.messaging.domain.topology.Service;
import org.apache.stratos.messaging.domain.topology.Topology;
import org.apache.stratos.messaging.event.topology.ServiceCreatedEvent;
import org.apache.stratos.messaging.message.filter.topology.TopologyServiceFilter;
import org.apache.stratos.messaging.message.processor.MessageProcessor;
import org.apache.stratos.messaging.message.receiver.topology.TopologyManager;
import org.apache.stratos.messaging.util.Util;

public class ServiceCreatedMessageProcessor extends MessageProcessor {

    private static final Log log = LogFactory.getLog(ServiceCreatedMessageProcessor.class);
    private MessageProcessor nextProcessor;

    @Override
    public void setNext(MessageProcessor nextProcessor) {
        this.nextProcessor = nextProcessor;
    }

    @Override
    public boolean process(String type, String message, Object object) {

        if (ServiceCreatedEvent.class.getName().equals(type)) {
            // Parse complete message and build event
            ServiceCreatedEvent event = (ServiceCreatedEvent) Util.jsonToObject(message, ServiceCreatedEvent.class);
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

            // Validate event against the existing topology
            if (topology.serviceExists(event.getServiceName())) {
                if (log.isWarnEnabled()) {
                    log.warn(String.format("Service already created: [service] %s", event.getServiceName()));
                }
            } else {
            	
            	// Apply changes to the topology
            	Service service = new Service(event.getServiceName(), event.getServiceType());
            	service.addPorts(event.getPorts());
            	topology.addService(service);
            	
            	if (log.isInfoEnabled()) {
            		log.info(String.format("Service created: [service] %s", event.getServiceName()));
            	}
            }


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
