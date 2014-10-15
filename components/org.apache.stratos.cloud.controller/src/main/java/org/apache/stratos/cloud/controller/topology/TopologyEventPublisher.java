package org.apache.stratos.cloud.controller.topology;

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
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.cloud.controller.pojo.Cartridge;
import org.apache.stratos.cloud.controller.pojo.ClusterContext;
import org.apache.stratos.cloud.controller.pojo.MemberContext;
import org.apache.stratos.cloud.controller.pojo.PortMapping;
import org.apache.stratos.cloud.controller.util.CloudControllerUtil;
import org.apache.stratos.messaging.broker.publish.EventPublisher;
import org.apache.stratos.messaging.broker.publish.EventPublisherPool;
import org.apache.stratos.messaging.domain.topology.Cluster;
import org.apache.stratos.messaging.domain.topology.ClusterStatus;
import org.apache.stratos.messaging.domain.topology.Port;
import org.apache.stratos.messaging.domain.topology.ServiceType;
import org.apache.stratos.messaging.domain.topology.Topology;
import org.apache.stratos.messaging.event.Event;
import org.apache.stratos.messaging.event.instance.status.InstanceStartedEvent;
import org.apache.stratos.messaging.event.topology.ClusterCreatedEvent;
import org.apache.stratos.messaging.event.topology.ClusterMaintenanceModeEvent;
import org.apache.stratos.messaging.event.topology.ClusterRemovedEvent;
import org.apache.stratos.messaging.event.topology.CompleteTopologyEvent;
import org.apache.stratos.messaging.event.topology.InstanceSpawnedEvent;
import org.apache.stratos.messaging.event.topology.MemberActivatedEvent;
import org.apache.stratos.messaging.event.topology.MemberMaintenanceModeEvent;
import org.apache.stratos.messaging.event.topology.MemberReadyToShutdownEvent;
import org.apache.stratos.messaging.event.topology.MemberStartedEvent;
import org.apache.stratos.messaging.event.topology.MemberTerminatedEvent;
import org.apache.stratos.messaging.event.topology.ServiceCreatedEvent;
import org.apache.stratos.messaging.event.topology.ServiceRemovedEvent;
import org.apache.stratos.messaging.util.Util;

/**
 * this is to send the relevant events from cloud controller to topology topic
 */
public class TopologyEventPublisher {
	private static final Log log = LogFactory
			.getLog(TopologyEventPublisher.class);

	public static void sendServiceCreateEvent(int tenantId, List<Cartridge> cartridgeList) {
		ServiceCreatedEvent serviceCreatedEvent;
		for (Cartridge cartridge : cartridgeList) {
			serviceCreatedEvent = new ServiceCreatedEvent(cartridge.getType(),
					(cartridge.isMultiTenant() ? ServiceType.MultiTenant
							: ServiceType.SingleTenant));

			// Add ports to the event
			Port port;
			List<PortMapping> portMappings = cartridge.getPortMappings();
			for (PortMapping portMapping : portMappings) {
				port = new Port(portMapping.getProtocol(),
						Integer.parseInt(portMapping.getPort()),
						Integer.parseInt(portMapping.getProxyPort()));
				serviceCreatedEvent.addPort(port);
			}

			if (log.isInfoEnabled()) {
				log.info(String.format(
						"Publishing service created event: [service] %s",
						cartridge.getType()));
			}
			publishEvent(tenantId, serviceCreatedEvent);
		}
	}

	public static void sendServiceRemovedEvent(int tenantId, List<Cartridge> cartridgeList) {
		ServiceRemovedEvent serviceRemovedEvent;
		for (Cartridge cartridge : cartridgeList) {
			serviceRemovedEvent = new ServiceRemovedEvent(cartridge.getType());
			if (log.isInfoEnabled()) {
				log.info(String.format(
						"Publishing service removed event: [service] %s",
						serviceRemovedEvent.getServiceName()));
			}
			publishEvent(tenantId, serviceRemovedEvent);
		}
	}

	public static void sendClusterCreatedEvent(int tenantId, String serviceName,
			String clusterId, Cluster cluster) {
		ClusterCreatedEvent clusterCreatedEvent = new ClusterCreatedEvent(
				serviceName, clusterId, cluster);

		if (log.isInfoEnabled()) {
			log.info("Publishing cluster created event: " + cluster.toString());
		}
		publishEvent(tenantId, clusterCreatedEvent);

	}

	public static void sendClusterRemovedEvent(int tenantId, ClusterContext ctxt,
			String deploymentPolicy) {

		ClusterRemovedEvent clusterRemovedEvent = new ClusterRemovedEvent(
				ctxt.getCartridgeType(), ctxt.getClusterId(), deploymentPolicy,
				ctxt.isLbCluster());

		if (log.isInfoEnabled()) {
			log.info(String
					.format("Publishing cluster removed event: [service] %s [cluster] %s",
							ctxt.getCartridgeType(), ctxt.getClusterId()));
		}
		publishEvent(tenantId, clusterRemovedEvent);

	}

	public static void sendClusterMaintenanceModeEvent(int tenantId, ClusterContext ctxt) {

		ClusterMaintenanceModeEvent clusterMaintenanceModeEvent = new ClusterMaintenanceModeEvent(
				ctxt.getCartridgeType(), ctxt.getClusterId());
		clusterMaintenanceModeEvent.setStatus(ClusterStatus.In_Maintenance);
		if (log.isInfoEnabled()) {
			log.info(String
					.format("Publishing cluster maintenance mode event: [service] %s [cluster] %s",
							clusterMaintenanceModeEvent.getServiceName(),
							clusterMaintenanceModeEvent.getClusterId()));
		}
		publishEvent(tenantId, clusterMaintenanceModeEvent);

	}

	public static void sendInstanceSpawnedEvent(int tenantId, String serviceName,
			String clusterId, String networkPartitionId, String partitionId,
			String memberId, String lbClusterId, String publicIp,
			String privateIp, MemberContext context) {
		InstanceSpawnedEvent instanceSpawnedEvent = new InstanceSpawnedEvent(
				serviceName, clusterId, networkPartitionId, partitionId,
				memberId);
		instanceSpawnedEvent.setLbClusterId(lbClusterId);
		instanceSpawnedEvent.setMemberIp(privateIp);
		instanceSpawnedEvent.setMemberPublicIp(publicIp);
		instanceSpawnedEvent.setProperties(CloudControllerUtil
				.toJavaUtilProperties(context.getProperties()));
		if (log.isInfoEnabled()) {
			log.info(String
					.format("Publishing instance spawned event: [service] %s [cluster] %s [network-partition] %s [partition] %s [member] %s [lb-cluster-id] %s",
							serviceName, clusterId, networkPartitionId,
							partitionId, memberId, lbClusterId));
		}
		publishEvent(tenantId, instanceSpawnedEvent);
	}

	public static void sendMemberStartedEvent(int tenantId,
                                              InstanceStartedEvent instanceStartedEvent) {
		MemberStartedEvent memberStartedEventTopology = new MemberStartedEvent(
				instanceStartedEvent.getServiceName(),
				instanceStartedEvent.getClusterId(),
				instanceStartedEvent.getNetworkPartitionId(),
				instanceStartedEvent.getPartitionId(),
				instanceStartedEvent.getMemberId());

		if (log.isInfoEnabled()) {
			log.info(String
					.format("Publishing member started event: [service] %s [cluster] %s [network-partition] %s [partition] %s [member] %s",
							instanceStartedEvent.getServiceName(),
							instanceStartedEvent.getClusterId(),
							instanceStartedEvent.getNetworkPartitionId(),
							instanceStartedEvent.getPartitionId(),
							instanceStartedEvent.getMemberId()));
		}
		publishEvent(tenantId, memberStartedEventTopology);
	}

	public static void sendMemberActivatedEvent(int tenantId,
                                                MemberActivatedEvent memberActivatedEvent) {
		if (log.isInfoEnabled()) {
			log.info(String
					.format("Publishing member activated event: [service] %s [cluster] %s [network-partition] %s [partition] %s [member] %s",
							memberActivatedEvent.getServiceName(),
							memberActivatedEvent.getClusterId(),
							memberActivatedEvent.getNetworkPartitionId(),
							memberActivatedEvent.getPartitionId(),
							memberActivatedEvent.getMemberId()));
		}
		publishEvent(tenantId, memberActivatedEvent);
	}

	public static void sendMemberReadyToShutdownEvent(int tenantId,
                                                      MemberReadyToShutdownEvent memberReadyToShutdownEvent) {
		if (log.isInfoEnabled()) {
			log.info(String
					.format("Publishing member Ready to shut down event: [service] %s [cluster] %s [network-partition] %s [partition] %s [member] %s",
							memberReadyToShutdownEvent.getServiceName(),
							memberReadyToShutdownEvent.getClusterId(),
							memberReadyToShutdownEvent.getNetworkPartitionId(),
							memberReadyToShutdownEvent.getPartitionId(),
							memberReadyToShutdownEvent.getMemberId()));
		}
		publishEvent(tenantId, memberReadyToShutdownEvent);
	}

	public static void sendMemberMaintenanceModeEvent(int tenantId,
                                                      MemberMaintenanceModeEvent memberMaintenanceModeEvent) {
		if (log.isInfoEnabled()) {
			log.info(String
					.format("Publishing Maintenance mode event: [service] %s [cluster] %s [network-partition] %s [partition] %s [member] %s",
							memberMaintenanceModeEvent.getServiceName(),
							memberMaintenanceModeEvent.getClusterId(),
							memberMaintenanceModeEvent.getNetworkPartitionId(),
							memberMaintenanceModeEvent.getPartitionId(),
							memberMaintenanceModeEvent.getMemberId()));
		}
		publishEvent(tenantId, memberMaintenanceModeEvent);
	}

	public static void sendMemberTerminatedEvent(int tenantId, String serviceName,
			String clusterId, String networkPartitionId, String partitionId,
			String memberId, Properties properties) {
		MemberTerminatedEvent memberTerminatedEvent = new MemberTerminatedEvent(
				serviceName, clusterId, networkPartitionId, partitionId,
				memberId);
		memberTerminatedEvent.setProperties(properties);
		if (log.isInfoEnabled()) {
			log.info(String
					.format("Publishing member terminated event: [service] %s [cluster] %s [network-partition] %s [partition] %s [member] %s",
							serviceName, clusterId, networkPartitionId,
							partitionId, memberId));
		}
		publishEvent(tenantId, memberTerminatedEvent);
	}

	public static void sendCompleteTopologyEvent(int tenantId, Topology topology) {
		CompleteTopologyEvent completeTopologyEvent = new CompleteTopologyEvent(
				topology);

		if (log.isDebugEnabled()) {
			log.debug(String.format("Publishing complete topology event"));
		}
		publishEvent(tenantId, completeTopologyEvent);
	}

	public static void publishEvent(int tenantId, Event event) {
		String topic = Util.getMessageTopicName(event);
        event.setTenantId(tenantId);
		EventPublisher eventPublisher = EventPublisherPool.getPublisher(topic);
		eventPublisher.publish(event);
	}
}