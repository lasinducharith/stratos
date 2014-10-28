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

package org.apache.stratos.manager.client;

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.stub.*;
import org.apache.stratos.autoscaler.stub.deployment.policy.DeploymentPolicy;
import org.apache.stratos.autoscaler.stub.kubernetes.KubernetesGroup;
import org.apache.stratos.autoscaler.stub.kubernetes.KubernetesHost;
import org.apache.stratos.autoscaler.stub.kubernetes.KubernetesMaster;
import org.apache.stratos.autoscaler.stub.policy.model.AutoscalePolicy;
import org.apache.stratos.cloud.controller.stub.deployment.partition.Partition;
import org.apache.stratos.cloud.controller.stub.pojo.Properties;
import org.apache.stratos.manager.internal.DataHolder;
import org.apache.stratos.manager.utils.CartridgeConstants;

import java.rmi.RemoteException;

public class AutoscalerServiceClient {

    private AutoScalerServiceStub stub;

    private static final Log log = LogFactory.getLog(AutoscalerServiceClient.class);
    private static volatile AutoscalerServiceClient serviceClient;

    public AutoscalerServiceClient(String epr) throws AxisFault {


        String autosclaerSocketTimeout =
                System.getProperty(CartridgeConstants.AUTOSCALER_SOCKET_TIMEOUT) == null ? "300000" : System.getProperty(CartridgeConstants.AUTOSCALER_SOCKET_TIMEOUT);
        String autosclaerConnectionTimeout =
                System.getProperty(CartridgeConstants.AUTOSCALER_CONNECTION_TIMEOUT) == null ? "300000" : System.getProperty(CartridgeConstants.AUTOSCALER_CONNECTION_TIMEOUT);

        ConfigurationContext clientConfigContext = DataHolder.getClientConfigContext();
        try {
            stub = new AutoScalerServiceStub(clientConfigContext, epr);
            stub._getServiceClient().getOptions().setProperty(HTTPConstants.SO_TIMEOUT, new Integer(autosclaerSocketTimeout));
            stub._getServiceClient().getOptions().setProperty(HTTPConstants.CONNECTION_TIMEOUT, new Integer(autosclaerConnectionTimeout));

        } catch (AxisFault axisFault) {
            String msg = "Failed to initiate autoscaler service client. " + axisFault.getMessage();
            log.error(msg, axisFault);
            throw new AxisFault(msg, axisFault);
        }
    }

    public static AutoscalerServiceClient getServiceClient() throws AxisFault {
        if (serviceClient == null) {
            synchronized (AutoscalerServiceClient.class) {
                if (serviceClient == null) {
                    serviceClient = new AutoscalerServiceClient(System.getProperty(CartridgeConstants.AUTOSCALER_SERVICE_URL));
                }
            }
        }
        return serviceClient;
    }

    public Partition[] getAvailablePartitions(int tenantId) throws RemoteException {

        Partition[] partitions;
        partitions = stub.getAllAvailablePartitions(tenantId);

        return partitions;
    }

    public Partition getPartition(int tenantId,
            String partitionId) throws RemoteException {

        Partition partition;
        partition = stub.getPartition(tenantId, partitionId);

        return partition;
    }

    public Partition[] getPartitionsOfGroup(int tenantId,
            String deploymentPolicyId, String partitionGroupId)
            throws RemoteException {

        Partition[] partitions;
        partitions = stub.getPartitionsOfGroup(tenantId, deploymentPolicyId,
                partitionGroupId);

        return partitions;
    }

    public Partition[]
    getPartitionsOfDeploymentPolicy(int tenantId,
            String deploymentPolicyId) throws RemoteException {

        Partition[] partitions;
        partitions = stub.getPartitionsOfDeploymentPolicy(tenantId, deploymentPolicyId);

        return partitions;
    }

    public org.apache.stratos.autoscaler.stub.partition.PartitionGroup[] getPartitionGroups(int tenantId,
            String deploymentPolicyId) throws RemoteException {

        org.apache.stratos.autoscaler.stub.partition.PartitionGroup[] partitionGroups;
        partitionGroups = stub.getPartitionGroups(tenantId, deploymentPolicyId);

        return partitionGroups;
    }

    public org.apache.stratos.autoscaler.stub.policy.model.AutoscalePolicy[] getAutoScalePolicies(int tenantId)
            throws RemoteException {

        org.apache.stratos.autoscaler.stub.policy.model.AutoscalePolicy[] autoscalePolicies;
        autoscalePolicies = stub.getAllAutoScalingPolicy(tenantId);

        return autoscalePolicies;
    }

    public org.apache.stratos.autoscaler.stub.policy.model.AutoscalePolicy getAutoScalePolicy(int tenantId,
            String autoscalingPolicyId) throws RemoteException {

        org.apache.stratos.autoscaler.stub.policy.model.AutoscalePolicy autoscalePolicy;
        autoscalePolicy = stub.getAutoscalingPolicy(tenantId, autoscalingPolicyId);

        return autoscalePolicy;
    }

    public org.apache.stratos.autoscaler.stub.deployment.policy.DeploymentPolicy[] getDeploymentPolicies(int tenantId)
            throws RemoteException {

        org.apache.stratos.autoscaler.stub.deployment.policy.DeploymentPolicy[] deploymentPolicies;
        deploymentPolicies = stub.getAllDeploymentPolicies(tenantId);

        return deploymentPolicies;
    }

    public org.apache.stratos.autoscaler.stub.deployment.policy.DeploymentPolicy[] getDeploymentPolicies(int tenantId,
            String cartridgeType) throws RemoteException {

        org.apache.stratos.autoscaler.stub.deployment.policy.DeploymentPolicy[] deploymentPolicies;
        deploymentPolicies = stub
                .getValidDeploymentPoliciesforCartridge(tenantId, cartridgeType);

        return deploymentPolicies;
    }

    public void checkLBExistenceAgainstPolicy(int tenantId, String clusterId, String deploymentPolicyId) throws RemoteException,
            AutoScalerServiceNonExistingLBExceptionException {
        stub.checkLBExistenceAgainstPolicy(tenantId, clusterId, deploymentPolicyId);
    }

    public boolean checkDefaultLBExistenceAgainstPolicy(int tenantId,
            String deploymentPolicyId) throws RemoteException {
        return stub.checkDefaultLBExistenceAgainstPolicy(tenantId, deploymentPolicyId);
    }

    public boolean checkServiceLBExistenceAgainstPolicy(int tenantId, String serviceName, String deploymentPolicyId) throws RemoteException {
        return stub.checkServiceLBExistenceAgainstPolicy(tenantId, serviceName, deploymentPolicyId);
    }

    public org.apache.stratos.autoscaler.stub.deployment.policy.DeploymentPolicy getDeploymentPolicy(int tenantId, String deploymentPolicyId) throws RemoteException {

        org.apache.stratos.autoscaler.stub.deployment.policy.DeploymentPolicy deploymentPolicy;
        deploymentPolicy = stub.getDeploymentPolicy(tenantId, deploymentPolicyId);

        return deploymentPolicy;
    }

    public boolean deployDeploymentPolicy(int tenantId, DeploymentPolicy deploymentPolicy) throws RemoteException,
            AutoScalerServiceInvalidPolicyExceptionException {

        return stub.addDeploymentPolicy(tenantId, deploymentPolicy);

    }

    public boolean deployAutoscalingPolicy(int tenantId, AutoscalePolicy autoScalePolicy) throws RemoteException,
            AutoScalerServiceInvalidPolicyExceptionException {

        return stub.addAutoScalingPolicy(tenantId, autoScalePolicy);

    }

    public boolean deployPartition(int tenantId, Partition partition) throws RemoteException,
            AutoScalerServiceInvalidPartitionExceptionException {

        return stub.addPartition(tenantId, partition);

    }

    public String getDefaultLBClusterId(int tenantId, String deploymentPolicy) throws RemoteException {
        return stub.getDefaultLBClusterId(tenantId, deploymentPolicy);
    }


    public String getServiceLBClusterId(int tenantId, String serviceType, String deploymentPolicy) throws RemoteException {
        return stub.getServiceLBClusterId(tenantId, serviceType, deploymentPolicy);
    }

    public boolean deployKubernetesGroup(int tenantId, KubernetesGroup kubernetesGroup) throws RemoteException,
            AutoScalerServiceInvalidKubernetesGroupExceptionException {
        return stub.addKubernetesGroup(tenantId, kubernetesGroup);
    }

    public boolean deployKubernetesHost(int tenantId, String kubernetesGroupId, KubernetesHost kubernetesHost)
            throws RemoteException, AutoScalerServiceInvalidKubernetesHostExceptionException,
            AutoScalerServiceNonExistingKubernetesGroupExceptionException {

        return stub.addKubernetesHost(tenantId, kubernetesGroupId, kubernetesHost);
    }

    public boolean updateKubernetesMaster(int tenantId, KubernetesMaster kubernetesMaster)
            throws RemoteException, AutoScalerServiceInvalidKubernetesMasterExceptionException,
            AutoScalerServiceNonExistingKubernetesMasterExceptionException {
        return stub.updateKubernetesMaster(tenantId, kubernetesMaster);
    }

    public KubernetesGroup[] getAvailableKubernetesGroups(int tenantId) throws RemoteException {
        return stub.getAllKubernetesGroups(tenantId);
    }

    public KubernetesGroup getKubernetesGroup(int tenantId, String kubernetesGroupId)
            throws RemoteException, AutoScalerServiceNonExistingKubernetesGroupExceptionException {
        return stub.getKubernetesGroup(tenantId, kubernetesGroupId);
    }

    public boolean undeployKubernetesGroup(int tenantId, String kubernetesGroupId)
            throws RemoteException, AutoScalerServiceNonExistingKubernetesGroupExceptionException {
        return stub.removeKubernetesGroup(tenantId, kubernetesGroupId);
    }

    public boolean undeployKubernetesHost(int tenantId, String kubernetesHostId)
            throws RemoteException, AutoScalerServiceNonExistingKubernetesHostExceptionException {
        return stub.removeKubernetesHost(tenantId, kubernetesHostId);
    }

    public KubernetesHost[] getKubernetesHosts(int tenantId, String kubernetesGroupId)
            throws RemoteException, AutoScalerServiceNonExistingKubernetesGroupExceptionException {
        return stub.getHostsForKubernetesGroup(tenantId, kubernetesGroupId);
    }

    public KubernetesMaster getKubernetesMaster(int tenantId, String kubernetesGroupId)
            throws RemoteException, AutoScalerServiceNonExistingKubernetesGroupExceptionException {
        return stub.getMasterForKubernetesGroup(tenantId, kubernetesGroupId);
    }

    public boolean updateKubernetesHost(int tenantId, KubernetesHost kubernetesHost)
            throws RemoteException, AutoScalerServiceInvalidKubernetesHostExceptionException,
            AutoScalerServiceNonExistingKubernetesHostExceptionException {
        return stub.updateKubernetesHost(tenantId, kubernetesHost);
    }
    
    public void updateClusterMonitor(String clusterId, Properties properties) throws RemoteException, AutoScalerServiceInvalidArgumentExceptionException {
        stub.updateClusterMonitor(clusterId, properties);
    }
}
