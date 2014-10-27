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
package org.apache.stratos.cloud.controller.interfaces;

import org.apache.stratos.cloud.controller.deployment.partition.Partition;
import org.apache.stratos.cloud.controller.exception.InvalidCartridgeDefinitionException;
import org.apache.stratos.cloud.controller.exception.InvalidCartridgeTypeException;
import org.apache.stratos.cloud.controller.exception.InvalidClusterException;
import org.apache.stratos.cloud.controller.exception.InvalidIaasProviderException;
import org.apache.stratos.cloud.controller.exception.InvalidMemberException;
import org.apache.stratos.cloud.controller.exception.InvalidPartitionException;
import org.apache.stratos.cloud.controller.exception.MemberTerminationFailedException;
import org.apache.stratos.cloud.controller.exception.UnregisteredCartridgeException;
import org.apache.stratos.cloud.controller.exception.UnregisteredClusterException;
import org.apache.stratos.cloud.controller.pojo.*;

/**
 * This API provides a way to communicate with underline
 * Infrastructure which are supported by <i>jClouds</i>.
 * 
 */
public interface CloudControllerService {
    
	/**
	 * Deploys a Cartridge configuration
     * @param tenantId tenantId of the service tenant
	 * @param cartridgeConfig cartridge configuration to be deployed
	 * @throws InvalidCartridgeDefinitionException if the cartridge configuration is not valid.
	 * @throws InvalidIaasProviderException if the iaas providers configured are not valid.
	 * @throws IllegalArgumentException  if the provided argument is not valid.
	 */
    void deployCartridgeDefinition(int tenantId, CartridgeConfig cartridgeConfig)
            throws InvalidCartridgeDefinitionException, InvalidIaasProviderException;
    
    /**
     * Undeploys a Cartridge configuration which is already deployed.
     * @param tenantId tenantId of the service tenant
     * @param cartridgeType type of the cartridge to be undeployed.
     * @throws InvalidCartridgeTypeException if the cartridge type specified is not a deployed cartridge.
     */
    void undeployCartridgeDefinition(int tenantId, String cartridgeType) throws InvalidCartridgeTypeException;

    /**
     * Validate a given {@link Partition} for basic property existence.
     * @param partition partition to be validated.
     * @return whether the partition is a valid one.
     * @throws InvalidPartitionException if the partition is invalid.
     */
    boolean validatePartition(Partition partition) throws InvalidPartitionException;
    
    /**
     * Validate a given {@link DeploymentPolicy's partitions} against a Cartridge.
     * @param tenantId tenantId of the service tenant
     * @param cartridgeType type of the cartridge that this policy is going to be attached to.
     * @param partitions policy's partitions to be validated.
     * @return whether the policy is a valid one against the given Cartridge.
     * @throws InvalidPartitionException if the policy contains at least one invalid partition.
     * @throws InvalidCartridgeTypeException if the given Cartridge type is not a valid one.
     */
     boolean validateDeploymentPolicy(int tenantId, String cartridgeType, Partition[] partitions)
            throws InvalidPartitionException, InvalidCartridgeTypeException;

    /**
     * <p>
     * Registers the details of a newly created service cluster. This will override an already
     * present service cluster, if there is any. A service cluster is uniquely identified by its
     * domain and sub domain combination.
     * </p>
     * @param tenantId tenantId of the service tenant
     * @param clusterContext information about the new subscription.
     * @return whether the registration is successful or not.
     * 
     * @throws UnregisteredCartridgeException
     *             when the cartridge type requested by this service is
     *             not a registered one.
     */
    boolean registerService(int tenantId, Registrant registrant) throws UnregisteredCartridgeException;

    /**
     * Calling this method will result in an instance startup, which is belong
     * to the provided Cluster ID. Also note that the instance that is starting up
     * belongs to the group whose name is derived from its Cluster ID, replacing <i>.</i>
     * by a hyphen (<i>-</i>).
     * @param tenantId tenantId of the service tenant
     * @param Member Context with cluster id, partition etc.
     * @return updated {@link MemberContext}
     * @throws UnregisteredCartridgeException if the requested Cartridge type is not a registered one.
     * @throws InvalidIaasProviderException if the iaas requested is not valid.
     */
    MemberContext startInstance(int tenantId, MemberContext member) throws UnregisteredCartridgeException, InvalidIaasProviderException;
    
    /**
     * Create a container cluster.
     * @param tenantId tenantId of the service tenant
     * @param {@link ContainerClusterContext} Context with cluster id, and host cluster details. 
     * @return a list of {@link MemberContext}s correspond to each Pod created.
     * @throws UnregisteredCartridgeException if the requested Cartridge type is not a registered one.
     */
    MemberContext[] startContainers(int tenantId, ContainerClusterContext clusterContext) throws UnregisteredCartridgeException;
    
    /**
     * Calling this method will result in termination of the instance with given member id in the given Partition.
     * @param tenantId tenantId of the service tenant
     * @param memberId
     *            member ID of the instance to be terminated.
     * @param partition
     *            It contains the region, zone, network and host of a IaaS where
     *            an instance need to be terminated..
     * @return whether an instance terminated successfully or not.
     */
    void terminateInstance(int tenantId, String memberId) throws InvalidMemberException, InvalidCartridgeTypeException;

    /**
     * Calling this method will result in termination of all instances belong
     * to the provided cluster ID.
     * @param tenantId tenantId of the service tenant
     * @param clusterId
     *            cluster ID of the instance to be terminated.
     * @return whether an instance terminated successfully or not.
     */
    void terminateAllInstances(int tenantId, String clusterId) throws InvalidClusterException;
    
    /**
     * Terminate all containers of the given cluster.
     * @param tenantId tenantId of the service tenant
     * @param clusterId id of the subjected cluster.
     * @return terminated {@link MemberContext}s
     * @throws InvalidClusterException
     */
    MemberContext[] terminateAllContainers(int tenantId, String clusterId) throws InvalidClusterException;
    
    /**
     * Terminate a given member/Kubernetes Pod.
     * @param tenantId tenantId of the service tenant
     * @param memberId member/Pod id to be terminated.
     * @return terminated {@link MemberContext}
     * @throws MemberTerminationFailedException
     */
    MemberContext terminateContainer(int tenantId, String memberId) throws MemberTerminationFailedException;
    
    /**
     * Update the Kubernetes controller created for the given cluster with the specified number of replicas.
     * @param tenantId tenantId of the service tenant
     * @param clusterId id of the subjected cluster.
     * @param replicas total number of replicas to be set to the controller.
     * @return newly created Members if any / terminated {@link MemberContext} in scale down scenario.
     * @throws InvalidClusterException
     */
    MemberContext[] updateContainers(int tenantId, String clusterId, int replicas) throws UnregisteredCartridgeException;
    
    /**
     * Unregister a docker service identified by the given cluster id.
     * @param tenantId tenantId of the service tenant
     * @param clusterId service cluster id.
     * @throws UnregisteredClusterException if the service cluster requested is not a registered one.
     */
    void unregisterDockerService(int tenantId, String clusterId) throws UnregisteredClusterException;

    /**
     * Unregister the service cluster identified by the given cluster id.
     * @param tenantId tenantId of the service tenant
     * @param clusterId service cluster id.
     * @throws UnregisteredClusterException if the service cluster requested is not a registered one.
     */
    void unregisterService(int tenantId, String clusterId) throws UnregisteredClusterException;
    
    /**
     * This method will return the information regarding the given cartridge, if present.
     * Else this will return <code>null</code>.
     * @param tenantId tenantId of the service tenant
     * @param cartridgeType
     *            type of the cartridge.
     * @return {@link org.apache.stratos.cloud.controller.pojo.CartridgeInfo} of the given cartridge type or <code>null</code>.
     * @throws UnregisteredCartridgeException if there is no registered cartridge with this type.
     */
    CartridgeInfo getCartridgeInfo(int tenantId, String cartridgeType) throws UnregisteredCartridgeException;

    /**
     * Calling this method will result in returning the types of {@link org.apache.stratos.cloud.controller.pojo.Cartridge}s
     * registered in Cloud Controller.
     * @param tenantId tenantId of the service tenant
     * @return String array containing types of registered {@link org.apache.stratos.cloud.controller.pojo.Cartridge}s.
     */
    String[] getRegisteredCartridges(int tenantId);

    /**
     * Returns the {@link org.apache.stratos.cloud.controller.pojo.ClusterContext} object associated with the given cluster id, or null if not found
     * @param tenantId tenantId of the service tenant
     * @param clusterId cluster id
     * @return {@link org.apache.stratos.cloud.controller.pojo.ClusterContext} object  associated with the given cluster id, or null
     */
    ClusterContext getClusterContext (int tenantId, String clusterId);

}
