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
package org.apache.stratos.cloud.controller.publisher;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.cloud.controller.exception.CloudControllerException;
import org.apache.stratos.cloud.controller.pojo.Cartridge;
import org.apache.stratos.cloud.controller.pojo.MemberContext;
import org.apache.stratos.cloud.controller.runtime.FasterLookUpDataHolder;
import org.apache.stratos.cloud.controller.runtime.FasterLookupDataHolderManager;
import org.apache.stratos.cloud.controller.util.CloudControllerConstants;
import org.jclouds.compute.domain.NodeMetadata;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.databridge.agent.thrift.AsyncDataPublisher;
import org.wso2.carbon.databridge.agent.thrift.exception.AgentException;
import org.wso2.carbon.databridge.commons.Attribute;
import org.wso2.carbon.databridge.commons.AttributeType;
import org.wso2.carbon.databridge.commons.Event;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.utils.CarbonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *  This will publish the state changes of a node in the topology to a data receiver
 */
public class CartridgeInstanceDataPublisher {
    
    private static final Log log = LogFactory.getLog(CartridgeInstanceDataPublisher.class);
    private static AsyncDataPublisher dataPublisher;
    private static StreamDefinition streamDefinition;
    private static final String cloudControllerEventStreamVersion = "1.0.0";

    public static void publish(String memberId,
                               String partitionId,
                               String networkId,
                               String clusterId,
                               String serviceName,
                               String status,
                               NodeMetadata metadata) {
        if(!FasterLookupDataHolderManager.getDataHolderForTenant().getEnableBAMDataPublisher()){
            return;
        }
        log.debug(CloudControllerConstants.DATA_PUB_TASK_NAME+" cycle started.");

        if(dataPublisher==null){
            createDataPublisher();

            //If we cannot create a data publisher we should give up
            //this means data will not be published
            if(dataPublisher == null){
                log.error("Data Publisher cannot be created or found.");
                release();
                return;
            }
        }


        MemberContext memberContext = FasterLookupDataHolderManager.getDataHolderForTenant().getMemberContextOfMemberId(memberId);
        String cartridgeType = memberContext.getCartridgeType();
        Cartridge cartridge = FasterLookupDataHolderManager.getDataHolderForTenant().getCartridge(cartridgeType);
        
        //Construct the data to be published
        List<Object> payload = new ArrayList<Object>();
        // Payload values
        payload.add(memberId);
        payload.add(serviceName);
        payload.add(clusterId);
        payload.add(memberContext.getLbClusterId());
        payload.add(partitionId);
        payload.add(networkId);
		if (cartridge != null) {
			payload.add(String.valueOf(cartridge.isMultiTenant()));
		} else {
			payload.add("");
		}
        payload.add(memberContext.getPartition().getProvider());
        payload.add(status);

        if(metadata != null) {
            payload.add(metadata.getHostname());
            payload.add(metadata.getHardware().getHypervisor());
            payload.add(String.valueOf(metadata.getHardware().getRam()));
            payload.add(metadata.getImageId());
            payload.add(metadata.getLoginPort());
            payload.add(metadata.getOperatingSystem().getName());
            payload.add(metadata.getOperatingSystem().getVersion());
            payload.add(metadata.getOperatingSystem().getArch());
            payload.add(String.valueOf(metadata.getOperatingSystem().is64Bit()));
        } else {
            payload.add("");
            payload.add("");
            payload.add("");
            payload.add("");
            payload.add(0);
            payload.add("");
            payload.add("");
            payload.add("");
            payload.add("");
        }

        payload.add(memberContext.getPrivateIpAddress());
        payload.add(memberContext.getPublicIpAddress());
        payload.add(memberContext.getAllocatedIpAddress());

        Event event = new Event();
        event.setPayloadData(payload.toArray());
        event.setArbitraryDataMap(new HashMap<String, String>());

        try {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Publishing BAM event: [stream] %s [version] %s", streamDefinition.getName(), streamDefinition.getVersion()));
            }
            dataPublisher.publish(streamDefinition.getName(), streamDefinition.getVersion(), event);
        } catch (AgentException e) {
            if (log.isErrorEnabled()) {
                log.error(String.format("Could not publish BAM event: [stream] %s [version] %s", streamDefinition.getName(), streamDefinition.getVersion()), e);
            }
        }
    }
    
    private static void release(){
        FasterLookupDataHolderManager.getDataHolderForTenant().setPublisherRunning(false);
    }
    
    private static StreamDefinition initializeStream() throws Exception {
        streamDefinition = new StreamDefinition(
                CloudControllerConstants.CLOUD_CONTROLLER_EVENT_STREAM,
                cloudControllerEventStreamVersion);
        streamDefinition.setNickName("cloud.controller");
        streamDefinition.setDescription("Instances booted up by the Cloud Controller");
        // Payload definition
        List<Attribute> payloadData = new ArrayList<Attribute>();
        payloadData.add(new Attribute(CloudControllerConstants.MEMBER_ID_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.CARTRIDGE_TYPE_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.CLUSTER_ID_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.LB_CLUSTER_ID_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.PARTITION_ID_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.NETWORK_ID_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.IS_MULTI_TENANT_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.IAAS_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.STATUS_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.HOST_NAME_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.HYPERVISOR_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.RAM_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.IMAGE_ID_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.LOGIN_PORT_COL, AttributeType.INT));
        payloadData.add(new Attribute(CloudControllerConstants.OS_NAME_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.OS_VERSION_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.OS_ARCH_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.OS_BIT_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.PRIV_IP_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.PUB_IP_COL, AttributeType.STRING));
        payloadData.add(new Attribute(CloudControllerConstants.ALLOCATE_IP_COL, AttributeType.STRING));
        streamDefinition.setPayloadData(payloadData);
        return streamDefinition;
    }


    private static void createDataPublisher(){
        //creating the agent

        ServerConfiguration serverConfig =  CarbonUtils.getServerConfiguration();
        String trustStorePath = serverConfig.getFirstProperty("Security.TrustStore.Location");
        String trustStorePassword = serverConfig.getFirstProperty("Security.TrustStore.Password");
        String bamServerUrl = serverConfig.getFirstProperty("BamServerURL");
        String adminUsername = FasterLookupDataHolderManager.getDataHolderForTenant().getDataPubConfig().getBamUsername();
        String adminPassword = FasterLookupDataHolderManager.getDataHolderForTenant().getDataPubConfig().getBamPassword();

        System.setProperty("javax.net.ssl.trustStore", trustStorePath);
        System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);


        try {
            dataPublisher = new AsyncDataPublisher("tcp://" +  bamServerUrl + "", adminUsername, adminPassword);
            FasterLookupDataHolderManager.getDataHolderForTenant().setDataPublisher(dataPublisher);
            initializeStream();
            dataPublisher.addStreamDefinition(streamDefinition);
        } catch (Exception e) {
            String msg = "Unable to create a data publisher to " + bamServerUrl +
                    ". Usage Agent will not function properly. ";
            log.error(msg, e);
            throw new CloudControllerException(msg, e);
        }
    }
    
}
