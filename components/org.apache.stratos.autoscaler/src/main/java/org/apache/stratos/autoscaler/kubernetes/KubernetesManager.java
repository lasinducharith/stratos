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

package org.apache.stratos.autoscaler.kubernetes;

import com.google.common.net.InetAddresses;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.exception.*;
import org.apache.stratos.autoscaler.registry.RegistryManager;
import org.apache.stratos.autoscaler.util.AutoScalerConstants;
import org.apache.stratos.common.kubernetes.KubernetesGroup;
import org.apache.stratos.common.kubernetes.KubernetesHost;
import org.apache.stratos.common.kubernetes.KubernetesMaster;

import java.util.*;

/**
 * Controller class for managing Kubernetes clusters.
 */
public class KubernetesManager {
    private static final Log log = LogFactory.getLog(KubernetesManager.class);
    private static KubernetesManager instance;

    // KubernetesGroups against groupId's
    private static Map<Integer, Map<String, KubernetesGroup>> tenantIdToKubernetesGroupsMap = new HashMap<Integer, Map<String, KubernetesGroup>>();

    // Make the constructor private to create a singleton object
    private KubernetesManager() {
    }

    public static KubernetesManager getInstance() {
        if (instance == null) {
            synchronized (KubernetesManager.class) {
                instance = new KubernetesManager();
            }
        }
        return instance;
    }


    private void validateKubernetesGroup(int tenantId, KubernetesGroup kubernetesGroup) throws InvalidKubernetesGroupException {
        if (kubernetesGroup == null) {
            throw new InvalidKubernetesGroupException("Kubernetes group can not be null");
        }
        if (StringUtils.isEmpty(kubernetesGroup.getGroupId())) {
            throw new InvalidKubernetesGroupException("Kubernetes group groupId can not be empty");
        }
        if (kubernetesGroupExists(tenantId, kubernetesGroup)) {
            throw new InvalidKubernetesGroupException(String.format("Kubernetes group already exists " +
                    "[id] %s", kubernetesGroup.getGroupId()));
        }
        if (kubernetesGroup.getKubernetesMaster() == null) {
            throw new InvalidKubernetesGroupException("Mandatory field master has not been set " +
                    "for the Kubernetes group [id] " + kubernetesGroup.getGroupId());
        }
        if (kubernetesGroup.getPortRange() == null) {
            throw new InvalidKubernetesGroupException("Mandatory field portRange has not been set " +
                    "for the Kubernetes group [id] " + kubernetesGroup.getGroupId());
        }

        // Port range validation
        if (kubernetesGroup.getPortRange().getUpper() > AutoScalerConstants.PORT_RANGE_MAX ||
                kubernetesGroup.getPortRange().getUpper() < AutoScalerConstants.PORT_RANGE_MIN ||
                kubernetesGroup.getPortRange().getLower() > AutoScalerConstants.PORT_RANGE_MAX ||
                kubernetesGroup.getPortRange().getLower() < AutoScalerConstants.PORT_RANGE_MIN ||
                kubernetesGroup.getPortRange().getUpper() < kubernetesGroup.getPortRange().getLower()) {
            throw new InvalidKubernetesGroupException("Port range is invalid " +
                    "for the Kubernetes group [id]" + kubernetesGroup.getGroupId());
        }
        try {
            validateKubernetesMaster(kubernetesGroup.getKubernetesMaster());
            validateKubernetesHosts(kubernetesGroup.getKubernetesHosts());

            // check whether master already exists
            if (kubernetesHostExists(tenantId, kubernetesGroup.getKubernetesMaster().getHostId())) {
                throw new InvalidKubernetesGroupException("Kubernetes host already exists [id] " +
                        kubernetesGroup.getKubernetesMaster().getHostId());
            }

            // Check for duplicate hostIds
            if (kubernetesGroup.getKubernetesHosts() != null) {
                List<String> hostIds = new ArrayList<String>();
                hostIds.add(kubernetesGroup.getKubernetesMaster().getHostId());

                for (KubernetesHost kubernetesHost : kubernetesGroup.getKubernetesHosts()) {
                    if (hostIds.contains(kubernetesHost.getHostId())) {
                        throw new InvalidKubernetesGroupException(
                                String.format("Kubernetes host [id] %s already defined in the request", kubernetesHost.getHostId()));
                    }

                    // check whether host already exists
                    if (kubernetesHostExists(tenantId, kubernetesHost.getHostId())) {
                        throw new InvalidKubernetesGroupException("Kubernetes host already exists [id] " +
                                kubernetesHost.getHostId());
                    }

                    hostIds.add(kubernetesHost.getHostId());
                }
            }

        } catch (InvalidKubernetesHostException e) {
            throw new InvalidKubernetesGroupException(e.getMessage());
        } catch (InvalidKubernetesMasterException e) {
            throw new InvalidKubernetesGroupException(e.getMessage());
        }
    }

    private void validateKubernetesHosts(KubernetesHost[] kubernetesHosts) throws InvalidKubernetesHostException {
        if (kubernetesHosts == null || kubernetesHosts.length == 0) {
            return;
        }
        for (KubernetesHost kubernetesHost : kubernetesHosts) {
            validateKubernetesHost(kubernetesHost);
        }
    }

    private void validateKubernetesHost(KubernetesHost kubernetesHost) throws InvalidKubernetesHostException {
        if (kubernetesHost == null) {
            throw new InvalidKubernetesHostException("Kubernetes host can not be null");
        }
        if (StringUtils.isEmpty(kubernetesHost.getHostId())) {
            throw new InvalidKubernetesHostException("Kubernetes host id can not be empty");
        }
        if (kubernetesHost.getHostIpAddress() == null) {
            throw new InvalidKubernetesHostException("Mandatory field Kubernetes host IP address has not been set " +
                    "for [hostId] " + kubernetesHost.getHostId());
        }
        if (!InetAddresses.isInetAddress(kubernetesHost.getHostIpAddress())) {
            throw new InvalidKubernetesHostException("Kubernetes host ip address is invalid: " + kubernetesHost.getHostIpAddress());
        }
    }

    private void validateKubernetesMaster(KubernetesMaster kubernetesMaster) throws InvalidKubernetesMasterException {
        try {
            validateKubernetesHost(kubernetesMaster);
        } catch (InvalidKubernetesHostException e) {
            throw new InvalidKubernetesMasterException(e.getMessage());
        }
    }


    /**
     * Register a new KubernetesGroup in AutoScaler.
     */
    public synchronized boolean addNewKubernetesGroup(int tenantId, KubernetesGroup kubernetesGroup)
            throws InvalidKubernetesGroupException {

        if (kubernetesGroup == null) {
            throw new InvalidKubernetesGroupException("Kubernetes Group can not be null");
        }
        if (log.isInfoEnabled()) {
            log.info("Deploying new Kubernetes group: " + kubernetesGroup);
        }
        validateKubernetesGroup(tenantId, kubernetesGroup);
        try {
            validateKubernetesEndPointViaCloudController(kubernetesGroup.getKubernetesMaster());

            // Add to information model
            addKubernetesGroupToInformationModel(tenantId, kubernetesGroup);

            // Persist the KubernetesGroup object in registry space
            RegistryManager.getInstance().persistKubernetesGroup(tenantId, kubernetesGroup);

            if (log.isInfoEnabled()) {
                log.info(String.format("Kubernetes group deployed successfully: [id] %s, [description] %s",
                        kubernetesGroup.getGroupId(), kubernetesGroup.getDescription()));
            }
            return true;
        } catch (Exception e) {
            throw new InvalidKubernetesGroupException(e.getMessage(), e);
        }
    }

    /**
     * Register a new KubernetesHost to an existing KubernetesGroup.
     */
    public synchronized boolean addNewKubernetesHost(int tenantId, String kubernetesGroupId, KubernetesHost kubernetesHost)
            throws InvalidKubernetesHostException, NonExistingKubernetesGroupException {

        if (kubernetesHost == null) {
            throw new InvalidKubernetesHostException("Kubernetes host can not be null");
        }
        if (StringUtils.isEmpty(kubernetesGroupId)) {
            throw new NonExistingKubernetesGroupException("Kubernetes group id can not be null");
        }
        if (log.isInfoEnabled()) {
            log.info("Deploying new Kubernetes Host: " + kubernetesHost + " for Kubernetes group id: " + kubernetesGroupId);
        }
        validateKubernetesHost(kubernetesHost);
        try {
            KubernetesGroup kubernetesGroupStored = getKubernetesGroup(tenantId, kubernetesGroupId);
            ArrayList<KubernetesHost> kubernetesHostArrayList;

            if (kubernetesGroupStored.getKubernetesHosts() == null) {
                kubernetesHostArrayList = new ArrayList<KubernetesHost>();
            } else {
                if (kubernetesHostExists(tenantId, kubernetesHost.getHostId())) {
                    throw new InvalidKubernetesHostException("Kubernetes host already exists: [id] " + kubernetesHost.getHostId());
                }
                kubernetesHostArrayList = new
                        ArrayList<KubernetesHost>(Arrays.asList(kubernetesGroupStored.getKubernetesHosts()));
            }
            kubernetesHostArrayList.add(kubernetesHost);

            // Update information model
            kubernetesGroupStored.setKubernetesHosts(kubernetesHostArrayList.toArray(new KubernetesHost[kubernetesHostArrayList.size()]));

            // Persist the new KubernetesHost wrapped under KubernetesGroup object
            RegistryManager.getInstance().persistKubernetesGroup(tenantId, kubernetesGroupStored);

            if (log.isInfoEnabled()) {
                log.info(String.format("Kubernetes host deployed successfully: [id] %s", kubernetesGroupStored.getGroupId()));
            }
            return true;
        } catch (Exception e) {
            throw new InvalidKubernetesHostException(e.getMessage(), e);
        }
    }

    /**
     * Update an existing Kubernetes master
     */
    public synchronized boolean updateKubernetesMaster(int tenantId, KubernetesMaster kubernetesMaster)
            throws InvalidKubernetesMasterException, NonExistingKubernetesMasterException {

        validateKubernetesMaster(kubernetesMaster);
        if (log.isInfoEnabled()) {
            log.info("Updating Kubernetes master: " + kubernetesMaster);
        }
        try {
            KubernetesGroup kubernetesGroupStored = getKubernetesGroupContainingHost(tenantId, kubernetesMaster.getHostId());

            // Update information model
            kubernetesGroupStored.setKubernetesMaster(kubernetesMaster);

            // Persist the new KubernetesHost wrapped under KubernetesGroup object
            RegistryManager.getInstance().persistKubernetesGroup(tenantId, kubernetesGroupStored);

            if (log.isInfoEnabled()) {
                log.info(String.format("Kubernetes master updated successfully: [id] %s", kubernetesMaster.getHostId()));
            }
        } catch (Exception e) {
            throw new InvalidKubernetesMasterException(e.getMessage(), e);
        }
        return false;
    }

    /**
     * Update an existing Kubernetes host
     */
    public synchronized boolean updateKubernetesHost(int tenantId, KubernetesHost kubernetesHost)
            throws InvalidKubernetesHostException, NonExistingKubernetesHostException {

        validateKubernetesHost(kubernetesHost);
        if (log.isInfoEnabled()) {
            log.info("Updating Kubernetes Host: " + kubernetesHost);
        }

        try {
            KubernetesGroup kubernetesGroupStored = getKubernetesGroupContainingHost(tenantId, kubernetesHost.getHostId());

            for (int i = 0; i < kubernetesGroupStored.getKubernetesHosts().length; i++) {
                if (kubernetesGroupStored.getKubernetesHosts()[i].getHostId().equals(kubernetesHost.getHostId())) {

                    // Update the information model
                    kubernetesGroupStored.getKubernetesHosts()[i] = kubernetesHost;

                    // Persist the new KubernetesHost wrapped under KubernetesGroup object
                    RegistryManager.getInstance().persistKubernetesGroup(tenantId, kubernetesGroupStored);

                    if (log.isInfoEnabled()) {
                        log.info(String.format("Kubernetes host updated successfully: [id] %s", kubernetesHost.getHostId()));
                    }

                    return true;
                }
            }
        } catch (Exception e) {
            throw new InvalidKubernetesHostException(e.getMessage(), e);
        }
        throw new NonExistingKubernetesHostException("Kubernetes host not found [id] " + kubernetesHost.getHostId());
    }

    /**
     * Remove a registered Kubernetes group from registry
     */
    public synchronized boolean removeKubernetesGroup(int tenantId, String kubernetesGroupId) throws NonExistingKubernetesGroupException {
        if (StringUtils.isEmpty(kubernetesGroupId)) {
            throw new NonExistingKubernetesGroupException("Kubernetes group id can not be empty");
        }
        if (log.isInfoEnabled()) {
            log.info("Removing Kubernetes group: " + kubernetesGroupId);
        }
        try {
            KubernetesGroup kubernetesGroupStored = getKubernetesGroup(tenantId, kubernetesGroupId);

            // Remove entry from information model
            if(tenantIdToKubernetesGroupsMap.containsKey(tenantId)) {
                tenantIdToKubernetesGroupsMap.get(tenantId).remove(kubernetesGroupId);
            }
            // Persist the new KubernetesHost wrapped under KubernetesGroup object
            RegistryManager.getInstance().removeKubernetesGroup(tenantId, kubernetesGroupStored);

            if (log.isInfoEnabled()) {
                log.info(String.format("Kubernetes group removed successfully: [id] %s", kubernetesGroupId));
            }
            return true;
        } catch (Exception e) {
            throw new NonExistingKubernetesGroupException(e.getMessage(), e);
        }
    }

    /**
     * Remove a registered Kubernetes host from registry
     */
    public synchronized boolean removeKubernetesHost(int tenantId, String kubernetesHostId) throws NonExistingKubernetesHostException {
        if (kubernetesHostId == null) {
            throw new NonExistingKubernetesHostException("Kubernetes host id can not be null");
        }
        if (log.isInfoEnabled()) {
            log.info("Removing Kubernetes Host: " + kubernetesHostId);
        }
        try {
            KubernetesGroup kubernetesGroupStored = getKubernetesGroupContainingHost(tenantId, kubernetesHostId);

            // Kubernetes master can not be removed
            if (kubernetesGroupStored.getKubernetesMaster().getHostId().equals(kubernetesHostId)) {
                throw new NonExistingKubernetesHostException("Kubernetes master is not allowed to be removed [id] " + kubernetesHostId);
            }

            List<KubernetesHost> kubernetesHostList = new ArrayList<KubernetesHost>();
            for (KubernetesHost kubernetesHost : kubernetesGroupStored.getKubernetesHosts()) {
                if (!kubernetesHost.getHostId().equals(kubernetesHostId)) {
                    kubernetesHostList.add(kubernetesHost);
                }
            }
            // member count will be equal only when host object was not found
            if (kubernetesHostList.size() == kubernetesGroupStored.getKubernetesHosts().length) {
                throw new NonExistingKubernetesHostException("Kubernetes host not found for [id] " + kubernetesHostId);
            }
            KubernetesHost[] kubernetesHostsArray = new KubernetesHost[kubernetesHostList.size()];
            kubernetesHostList.toArray(kubernetesHostsArray);

            // Update information model
            kubernetesGroupStored.setKubernetesHosts(kubernetesHostsArray);

            // Persist the updated KubernetesGroup object
            RegistryManager.getInstance().persistKubernetesGroup(tenantId, kubernetesGroupStored);

            if (log.isInfoEnabled()) {
                log.info(String.format("Kubernetes host removed successfully: [id] %s", kubernetesHostId));
            }

            return true;
        } catch (Exception e) {
            throw new NonExistingKubernetesHostException(e.getMessage(), e);
        }
    }

    private void addKubernetesGroupToInformationModel(int tenantId, KubernetesGroup kubernetesGroup) throws InvalidKubernetesGroupException{

        Map<String, KubernetesGroup> kubernetesGroupsMap;

        if (!tenantIdToKubernetesGroupsMap.containsKey(tenantId)) {
            kubernetesGroupsMap = new HashMap<String, KubernetesGroup>();
        } else {
            kubernetesGroupsMap = tenantIdToKubernetesGroupsMap.get(tenantId);
        }

        if (!kubernetesGroupsMap.containsKey(kubernetesGroup.getGroupId())) {
            if (log.isDebugEnabled()) {
                log.debug("Adding Kubernetes Group :" + kubernetesGroup.getGroupId() + " for tenant :" + tenantId);
            }
            kubernetesGroupsMap.put(kubernetesGroup.getGroupId(), kubernetesGroup);
            tenantIdToKubernetesGroupsMap.put(tenantId, kubernetesGroupsMap);
        } else {
            String errMsg = "Specified Kubernetes Group [" + kubernetesGroup.getGroupId() + "] already exists for tenant [" + tenantId + "]";
            log.error(errMsg);
            throw new InvalidKubernetesGroupException(errMsg);
        }
    }

    private void validateKubernetesEndPointViaCloudController(KubernetesMaster kubernetesMaster)
            throws KubernetesEndpointValidationException {
        // TODO
    }

    public boolean kubernetesGroupExists(int tenantId, KubernetesGroup kubernetesGroup) {
        if(tenantIdToKubernetesGroupsMap.containsKey(tenantId)) {
            return tenantIdToKubernetesGroupsMap.get(tenantId).containsKey(kubernetesGroup.getGroupId());
        }
        return false;
    }

    public boolean kubernetesHostExists(int tenantId, String hostId) {
        if (StringUtils.isEmpty(hostId)) {
            return false;
        }
        if(tenantIdToKubernetesGroupsMap.containsKey(tenantId)) {
            for (KubernetesGroup kubernetesGroup : tenantIdToKubernetesGroupsMap.get(tenantId).values()) {
                if (kubernetesGroup.getKubernetesHosts() != null) {
                    for (KubernetesHost kubernetesHost : kubernetesGroup.getKubernetesHosts()) {
                        if (kubernetesHost.getHostId().equals(hostId)) {
                            return true;
                        }
                    }
                }
                if (hostId.equals(kubernetesGroup.getKubernetesMaster().getHostId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public KubernetesHost[] getKubernetesHostsInGroup(int tenantId, String kubernetesGroupId) throws NonExistingKubernetesGroupException {
        if (StringUtils.isEmpty(kubernetesGroupId)) {
            throw new NonExistingKubernetesGroupException("Cannot find for empty group id");
        }
        KubernetesGroup kubernetesGroup = null;
        if(tenantIdToKubernetesGroupsMap.containsKey(tenantId)) {
            kubernetesGroup = tenantIdToKubernetesGroupsMap.get(tenantId).get(kubernetesGroupId);
        }
        if (kubernetesGroup != null) {
            return kubernetesGroup.getKubernetesHosts();
        }
        throw new NonExistingKubernetesGroupException("Kubernetes group not found for group id: " + kubernetesGroupId);
    }

    public KubernetesMaster getKubernetesMasterInGroup(int tenantId, String kubernetesGroupId) throws NonExistingKubernetesGroupException {
        if (StringUtils.isEmpty(kubernetesGroupId)) {
            throw new NonExistingKubernetesGroupException("Cannot find for empty group id");
        }
        KubernetesGroup kubernetesGroup = null;
        if(tenantIdToKubernetesGroupsMap.containsKey(tenantId)) {
            kubernetesGroup = tenantIdToKubernetesGroupsMap.get(tenantId).get(kubernetesGroupId);
        }
        if (kubernetesGroup != null) {
            return kubernetesGroup.getKubernetesMaster();
        }
        throw new NonExistingKubernetesGroupException("Kubernetes master not found for group id: " + kubernetesGroupId);
    }

    public KubernetesGroup getKubernetesGroup(int tenantId, String groupId) throws NonExistingKubernetesGroupException {
        if (StringUtils.isEmpty(groupId)) {
            throw new NonExistingKubernetesGroupException("Cannot find for empty group id");
        }
        KubernetesGroup kubernetesGroup = null;
        if(tenantIdToKubernetesGroupsMap.containsKey(tenantId)) {
            kubernetesGroup = tenantIdToKubernetesGroupsMap.get(tenantId).get(groupId);
        }
        if (kubernetesGroup != null) {
            return kubernetesGroup;
        }
        throw new NonExistingKubernetesGroupException("Kubernetes group not found for id: " + groupId);
    }

    public KubernetesGroup getKubernetesGroupContainingHost(int tenantId, String hostId) throws NonExistingKubernetesGroupException {
        if (StringUtils.isEmpty(hostId)) {
            return null;
        }

        if (tenantIdToKubernetesGroupsMap.containsKey(tenantId)) {
        for (KubernetesGroup kubernetesGroup : tenantIdToKubernetesGroupsMap.get(tenantId).values()) {
            if (hostId.equals(kubernetesGroup.getKubernetesMaster().getHostId())) {
                return kubernetesGroup;
            }
            if (kubernetesGroup.getKubernetesHosts() != null) {
                for (KubernetesHost kubernetesHost : kubernetesGroup.getKubernetesHosts()) {
                    if (kubernetesHost.getHostId().equals(hostId)) {
                        return kubernetesGroup;
                    }
                }
            }
        }
    }
        throw new NonExistingKubernetesGroupException("Kubernetes group not found containing host id: " + hostId);
    }

    public KubernetesGroup[] getKubernetesGroups(int tenantId) {
        if(tenantIdToKubernetesGroupsMap.containsKey(tenantId)) {
            return tenantIdToKubernetesGroupsMap.values().toArray(new KubernetesGroup[tenantIdToKubernetesGroupsMap.size()]);
        }
        return null;
    }
}
