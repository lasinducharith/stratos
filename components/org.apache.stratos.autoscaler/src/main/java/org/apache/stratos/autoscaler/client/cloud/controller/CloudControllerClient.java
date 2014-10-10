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
 * KIND, either express or implied.  TcSee the License for the 
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.stratos.autoscaler.client.cloud.controller;

import java.rmi.RemoteException;

import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.impl.llom.util.AXIOMUtil;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.Constants;
import org.apache.stratos.autoscaler.api.AutoScalerServiceImpl;
import org.apache.stratos.autoscaler.deployment.policy.DeploymentPolicy;
import org.apache.stratos.autoscaler.exception.NonExistingKubernetesGroupException;
import org.apache.stratos.autoscaler.exception.PartitionValidationException;
import org.apache.stratos.autoscaler.exception.SpawningException;
import org.apache.stratos.autoscaler.exception.TerminationException;
import org.apache.stratos.autoscaler.interfaces.AutoScalerServiceInterface;
import org.apache.stratos.autoscaler.kubernetes.KubernetesManager;
import org.apache.stratos.autoscaler.util.ConfUtil;
import org.apache.stratos.cloud.controller.stub.*;
import org.apache.stratos.cloud.controller.stub.deployment.partition.Partition;
import org.apache.stratos.cloud.controller.stub.pojo.MemberContext;
import org.apache.stratos.cloud.controller.stub.pojo.Properties;
import org.apache.stratos.cloud.controller.stub.pojo.Property;
import org.apache.stratos.common.constants.StratosConstants;
import org.apache.stratos.common.kubernetes.KubernetesGroup;
import org.apache.stratos.common.kubernetes.KubernetesMaster;

import org.wso2.carbon.context.CarbonContext;


/**
 * This class will call cloud controller web service to take the action decided by Autoscaler
 */
public class CloudControllerClient {

    private static final Log log = LogFactory.getLog(CloudControllerClient.class);
    private static CloudControllerServiceStub stub;
    
    /* An instance of a CloudControllerClient is created when the class is loaded. 
     * Since the class is loaded only once, it is guaranteed that an object of 
     * CloudControllerClient is created only once. Hence it is singleton.
     */
    private static class InstanceHolder {
        private static final CloudControllerClient INSTANCE = new CloudControllerClient(); 
    }
    
    private static CloudControllerClient getInstance() {
    	return InstanceHolder.INSTANCE;
    }
    
    private CloudControllerClient(){
    	try {
            XMLConfiguration conf = ConfUtil.getInstance(null).getConfiguration();
            int port = conf.getInt("autoscaler.cloudController.port", Constants.CLOUD_CONTROLLER_DEFAULT_PORT);
            String hostname = conf.getString("autoscaler.cloudController.hostname", "localhost");
            String epr = "https://" + hostname + ":" + port + "/" + Constants.CLOUD_CONTROLLER_SERVICE_SFX  ;
            int cloudControllerClientTimeout = conf.getInt("autoscaler.cloudController.clientTimeout", 180000);
            stub = new CloudControllerServiceStub(epr);
            stub._getServiceClient().getOptions().setProperty(HTTPConstants.SO_TIMEOUT, cloudControllerClientTimeout);
            stub._getServiceClient().getOptions().setProperty(HTTPConstants.CONNECTION_TIMEOUT, cloudControllerClientTimeout);
		} catch (Exception e) {
			log.error("Stub init error", e);
		}
    }
    
    /**
     * Sets the mutual auth header with default carbon.
     */
    private void setMutualAuthHeader() {
    	String userName=CarbonContext.getThreadLocalCarbonContext().getUsername();
    	String tenantDomain=CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
    	String fullUserName = userName+"@"+tenantDomain;
    	        
    	String mutualAuthHeader = "<tns:UserName xmlns:tns=\""+ StratosConstants.MUTUAL_AUTH_URL+ "\">" + fullUserName + "</tns:UserName> ";
        try {
        	// Need to remove headers since this is a stateless client and this is a new request
            stub._getServiceClient().removeHeaders();
        	stub._getServiceClient().addHeader(AXIOMUtil.stringToOM(mutualAuthHeader));
        } catch (XMLStreamException e) {
            log.error("Failed to set mutualAuth Header to stub:" + stub, e);
        }
    }
    
    /**
     * Gets the client with mutual auth header set.
     *
     * @return the client with mutual auth header set
     */
    public static CloudControllerClient getClientWithMutualAuthHeaderSet() {
		CloudControllerClient client = CloudControllerClient.getInstance();
		// Set mutual auth header for communication between Autoscalar and Cloud controller
        client.setMutualAuthHeader();
    	return client;
    }
    
    /*
     * This will validate the given partitions against the given cartridge type.
     */
    
    public synchronized boolean validateDeploymentPolicy(String cartridgeType, DeploymentPolicy deploymentPolicy) throws PartitionValidationException{
        try {
            if(log.isInfoEnabled()) {
                log.info(String.format("Validating partitions of policy via cloud controller: [id] %s", deploymentPolicy.getId()));
            }
            long startTime = System.currentTimeMillis();
            boolean result = stub.validateDeploymentPolicy(cartridgeType, deploymentPolicy.getAllPartitions());
            if(log.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                log.debug(String.format("Service call validateDeploymentPolicy() returned in %dms", (endTime - startTime)));
            }
            return result;
        } catch (RemoteException e) {
            log.error(e.getMessage(), e);
            throw new PartitionValidationException(e.getMessage(), e);
        } catch (CloudControllerServiceInvalidPartitionExceptionException e) {
            log.error(e.getFaultMessage().getInvalidPartitionException().getMessage(), e);
            throw new PartitionValidationException(e.getFaultMessage().getInvalidPartitionException().getMessage());
        } catch (CloudControllerServiceInvalidCartridgeTypeExceptionException e) {
            log.error(e.getFaultMessage().getInvalidCartridgeTypeException().getMessage(), e);
            throw new PartitionValidationException(e.getFaultMessage().getInvalidCartridgeTypeException().getMessage());
        }

    }
    
    /*
     * Calls the CC to validate the partition.
     */
    public synchronized boolean validatePartition(Partition partition) throws PartitionValidationException{
        
        try {
            if(log.isInfoEnabled()) {
                log.info(String.format("Validating partition via cloud controller: [id] %s", partition.getId()));
            }
            long startTime = System.currentTimeMillis();
            boolean result = stub.validatePartition(partition);
            if(log.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                log.debug(String.format("Service call validatePartition() returned in %dms", (endTime - startTime)));
            }
            return result;
        } catch (RemoteException e) {
        	log.error(e.getMessage(), e);
            throw new PartitionValidationException(e.getMessage(), e);
        } catch (CloudControllerServiceInvalidPartitionExceptionException e) {
        	log.error(e.getFaultMessage().getInvalidPartitionException().getMessage(), e);
        	throw new PartitionValidationException(e.getFaultMessage().getInvalidPartitionException().getMessage(), e);
		}

    }

    public synchronized MemberContext spawnAnInstance(Partition partition,
    		String clusterId, String lbClusterId, String networkPartitionId, boolean isPrimary, int minMemberCount) throws SpawningException {
        try {
            if(log.isInfoEnabled()) {
                log.info(String.format("Trying to spawn an instance via cloud controller: [cluster] %s [partition] %s [lb-cluster] %s [network-partition-id] %s",
                    clusterId, partition.getId(), lbClusterId, networkPartitionId));
            }

            MemberContext member = new MemberContext();
            member.setClusterId(clusterId);
            member.setPartition(partition);
            member.setLbClusterId(lbClusterId);
            member.setInitTime(System.currentTimeMillis());
            member.setNetworkPartitionId(networkPartitionId);
            Properties memberContextProps = new Properties();
            Property isPrimaryProp = new Property();
            isPrimaryProp.setName("PRIMARY");
            isPrimaryProp.setValue(String.valueOf(isPrimary));
            
            Property minCountProp = new Property();
            minCountProp.setName("MIN_COUNT");
            minCountProp.setValue(String.valueOf(minMemberCount));
            
            memberContextProps.addProperties(isPrimaryProp);
            memberContextProps.addProperties(minCountProp);
            member.setProperties(memberContextProps);
            
            
            long startTime = System.currentTimeMillis();
            MemberContext memberContext = stub.startInstance(member);
            if(log.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                log.debug(String.format("Service call startInstance() returned in %dms", (endTime - startTime)));
            }
            return memberContext;
        } catch (CloudControllerServiceUnregisteredCartridgeExceptionException e) {
        	String message = e.getFaultMessage().getUnregisteredCartridgeException().getMessage();
        	log.error(message, e);
			throw new SpawningException(message, e);
        } catch (RemoteException e) {
        	log.error(e.getMessage(), e);
            throw new SpawningException(e.getMessage(), e);
		} catch (CloudControllerServiceInvalidIaasProviderExceptionException e) {
			String message = e.getFaultMessage().getInvalidIaasProviderException().getMessage();
        	log.error(message, e);
			throw new SpawningException(message, e);
		}
    }
    
    public synchronized void terminateAllInstances(String clusterId) throws TerminationException {
        try {
            if(log.isInfoEnabled()) {
                log.info(String.format("Terminating all instances of cluster via cloud controller: [cluster] %s", clusterId));
            }
            long startTime = System.currentTimeMillis();
            stub.terminateAllInstances(clusterId);
            if(log.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                log.debug(String.format("Service call terminateAllInstances() returned in %dms", (endTime - startTime)));
            }
        } catch (RemoteException e) {
            String msg = e.getMessage();
            log.error(msg, e);
            throw new TerminationException(msg, e);

        } catch (CloudControllerServiceInvalidClusterExceptionException e) {
        	String message = e.getFaultMessage().getInvalidClusterException().getMessage();
            log.error(message, e);
            throw new TerminationException(message, e);
        }
    }

    public synchronized void terminate(String memberId) throws TerminationException {
        try {
            if(log.isInfoEnabled()) {
                log.info(String.format("Terminating instance via cloud controller: [member] %s", memberId));
            }
            long startTime = System.currentTimeMillis();
            stub.terminateInstance(memberId);
            if(log.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                log.debug(String.format("Service call terminateInstance() returned in %dms", (endTime - startTime)));
            }
        } catch (RemoteException e) {
        	String msg = e.getMessage();
            log.error(msg, e);
            throw new TerminationException(msg, e);
        } catch (CloudControllerServiceInvalidMemberExceptionException e) {
        	String msg = e.getFaultMessage().getInvalidMemberException().getMessage();
            log.error(msg, e);
            throw new TerminationException(msg, e);
        } catch (CloudControllerServiceInvalidCartridgeTypeExceptionException e) {
        	String msg = e.getFaultMessage().getInvalidCartridgeTypeException().getMessage();
            log.error(msg, e);
            throw new TerminationException(msg, e);
        }
    }

    /**
     * @param kubernetesClusterId
     * @param clusterId
     * @return
     * @throws SpawningException
     */
    public synchronized MemberContext createContainer(String kubernetesClusterId, String clusterId) throws SpawningException {
        try {
        	
        	KubernetesManager kubernetesManager = KubernetesManager.getInstance();
        	KubernetesMaster kubernetesMaster = kubernetesManager.getKubernetesMasterInGroup(kubernetesClusterId);
        	String kubernetesMasterIP = kubernetesMaster.getHostIpAddress();
        	KubernetesGroup kubernetesGroup = kubernetesManager.getKubernetesGroup(kubernetesClusterId);
    		int lower = kubernetesGroup.getPortRange().getLower();
    		int upper = kubernetesGroup.getPortRange().getUpper();
    		String portRange = Integer.toString(lower) + "-" + Integer.toString(upper);
    		
            MemberContext member = new MemberContext();
            member.setClusterId(clusterId);
            member.setInitTime(System.currentTimeMillis());
            Properties memberContextProps = new Properties();
            Property kubernetesClusterMasterIPProps = new Property();
            kubernetesClusterMasterIPProps.setName(StratosConstants.KUBERNETES_MASTER_IP);
            kubernetesClusterMasterIPProps.setValue(kubernetesMasterIP);
            memberContextProps.addProperties(kubernetesClusterMasterIPProps);
            Property kubernetesClusterPortRangeProps = new Property();
            kubernetesClusterPortRangeProps.setName(StratosConstants.KUBERNETES_PORT_RANGE);
            kubernetesClusterPortRangeProps.setValue(portRange);
            memberContextProps.addProperties(kubernetesClusterPortRangeProps);
            member.setProperties(memberContextProps);
            long startTime = System.currentTimeMillis();
            MemberContext memberContext = stub.startContainers(member);
            
            if(log.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                log.debug(String.format("Service call startContainer() returned in %dms", (endTime - startTime)));
            }
            return memberContext;
        } catch (CloudControllerServiceUnregisteredCartridgeExceptionException e) {
        	String message = e.getFaultMessage().getUnregisteredCartridgeException().getMessage();
        	log.error(message, e);
			throw new SpawningException(message, e);
        } catch (RemoteException e) {
        	log.error(e.getMessage(), e);
            throw new SpawningException(e.getMessage(), e);
		} catch (NonExistingKubernetesGroupException e){
            log.error(e.getMessage(), e);
            throw new SpawningException(e.getMessage(), e);
        }
    }
    
    public synchronized void terminateAllContainers(String clusterId) throws TerminationException {
        try {
            if(log.isInfoEnabled()) {
                log.info(String.format("Terminating containers via cloud controller: [cluster] %s", clusterId));
            }
            long startTime = System.currentTimeMillis();
            stub.terminateAllContainers(clusterId);
            if(log.isDebugEnabled()) {
                long endTime = System.currentTimeMillis();
                log.debug(String.format("Service call terminateContainer() returned in %dms", (endTime - startTime)));
            }
        } catch (RemoteException e) {
        	String msg = e.getMessage();
            log.error(msg, e);
            throw new TerminationException(msg, e);
        } catch (CloudControllerServiceInvalidClusterExceptionException e) {
        	String msg = e.getFaultMessage().getInvalidClusterException().getMessage();
            log.error(msg, e);
            throw new TerminationException(msg, e);
		} 
    }

}
