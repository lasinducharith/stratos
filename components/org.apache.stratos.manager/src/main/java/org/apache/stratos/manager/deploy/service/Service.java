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

package org.apache.stratos.manager.deploy.service;

import org.apache.stratos.cloud.controller.stub.pojo.CartridgeInfo;
import org.apache.stratos.cloud.controller.stub.pojo.Properties;
import org.apache.stratos.manager.behaviour.CartridgeMgtBehaviour;
import org.apache.stratos.manager.dao.Cluster;
import org.apache.stratos.manager.exception.ADCException;
import org.apache.stratos.manager.exception.NotSubscribedException;
import org.apache.stratos.manager.exception.UnregisteredCartridgeException;
import org.apache.stratos.manager.payload.PayloadData;
import org.apache.stratos.manager.subscription.utils.CartridgeSubscriptionUtils;

public abstract class Service extends CartridgeMgtBehaviour {

	private static final long serialVersionUID = -3261972002698749232L;

    private String type;
    private String autoscalingPolicyName;
    private String deploymentPolicyName;
    private String tenantRange;
    private int tenantId;
    private String subscriptionKey;
    private CartridgeInfo cartridgeInfo;
    private PayloadData payloadData;
    private Cluster cluster;
    private boolean isPublic;

    public Service (String type, String autoscalingPolicyName, String deploymentPolicyName, int tenantId, CartridgeInfo cartridgeInfo,
    		String tenantRange, boolean isPublic) {

        this.type = type;
        this.autoscalingPolicyName = autoscalingPolicyName;
        this.deploymentPolicyName = deploymentPolicyName;
        this.tenantId = tenantId;
        this.cartridgeInfo = cartridgeInfo;
        this.tenantRange = tenantRange;
        this.subscriptionKey = CartridgeSubscriptionUtils.generateSubscriptionKey();
        this.setCluster(new Cluster());
        this.isPublic = isPublic;
    }

    public void create () throws ADCException {

        setClusterId(generateClusterId(null, type));
        //host name is the hostname defined in cartridge definition
        setHostName(generateHostName(null, cartridgeInfo.getHostName()));

        // create and set PayloadData instance
        setPayloadData(createPayload(cartridgeInfo, subscriptionKey, null, cluster, null, null, null));
    }

    protected String generateClusterId (String alias, String cartridgeType) {

        String clusterId = cartridgeType + "." + cartridgeInfo.getHostName() + ".domain";
        // limit the cartridge alias to 30 characters in length
        if (clusterId.length() > 30) {
            clusterId = CartridgeSubscriptionUtils.limitLengthOfString(clusterId, 30);
        }

        return clusterId;
    }

    protected String generateHostName (String alias, String cartridgeDefinitionHostName) {

        return cartridgeDefinitionHostName;
    }

    public void deploy (int tenantId, Properties properties) throws ADCException, UnregisteredCartridgeException {

        register(tenantId, getCartridgeInfo(), getCluster(), getPayloadData(), getAutoscalingPolicyName(), getDeploymentPolicyName(), properties, null);
    }

    public void undeploy (int tenantId) throws ADCException, NotSubscribedException {

        remove(tenantId, cluster.getClusterDomain(), null);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAutoscalingPolicyName() {
        return autoscalingPolicyName;
    }

    public void setAutoscalingPolicyName(String autoscalingPolicyName) {
        this.autoscalingPolicyName = autoscalingPolicyName;
    }

    public String getDeploymentPolicyName() {
        return deploymentPolicyName;
    }

    public void setDeploymentPolicyName(String deploymentPolicyName) {
        this.deploymentPolicyName = deploymentPolicyName;
    }

    public String getTenantRange() {
        return tenantRange;
    }

    public void setTenantRange(String tenantRange) {
        this.tenantRange = tenantRange;
    }

    public String getClusterId() {
        return cluster.getClusterDomain();
    }

    public void setClusterId(String clusterId) {
        this.cluster.setClusterDomain(clusterId);
    }

    public String getHostName() {
        return cluster.getHostName();
    }

    public void setHostName(String hostName) {
        this.cluster.setHostName(hostName);
    }

    public int getTenantId() {
        return tenantId;
    }

    public void setTenantId(int tenantId) {
        this.tenantId = tenantId;
    }

    public CartridgeInfo getCartridgeInfo() {
        return cartridgeInfo;
    }

    public void setCartridgeInfo(CartridgeInfo cartridgeInfo) {
        this.cartridgeInfo = cartridgeInfo;
    }

    public String getSubscriptionKey() {
        return subscriptionKey;
    }

    public void setSubscriptionKey(String subscriptionKey) {
        this.subscriptionKey = subscriptionKey;
    }

    public PayloadData getPayloadData() {
        return payloadData;
    }

    public void setPayloadData(PayloadData payloadData) {
        this.payloadData = payloadData;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }
    
    public boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }
}
