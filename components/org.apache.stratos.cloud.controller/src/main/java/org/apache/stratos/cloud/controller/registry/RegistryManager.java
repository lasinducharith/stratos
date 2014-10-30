package org.apache.stratos.cloud.controller.registry;
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
*/

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.cloud.controller.exception.CloudControllerException;
import org.apache.stratos.cloud.controller.persist.Serializer;
import org.apache.stratos.cloud.controller.runtime.FasterLookUpDataHolder;
import org.apache.stratos.cloud.controller.util.CloudControllerConstants;
import org.apache.stratos.cloud.controller.util.ServiceReferenceHolder;
import org.apache.stratos.messaging.domain.topology.Topology;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.exceptions.ResourceNotFoundException;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.util.HashMap;
import java.util.Map;

/**
 * Manager class to do Registry Operations
 * Persistence and Retrieval of CC data and topology
 */
public class RegistryManager {
    private final static Log log = LogFactory.getLog(RegistryManager.class);
    private static Registry registryService;
    private static final String PATH_SEPARATOR = "/";

    private static class Holder {
        static final RegistryManager INSTANCE = new RegistryManager();
    }

    public static RegistryManager getInstance() {
        registryService = ServiceReferenceHolder.getInstance().getRegistry();
        if (registryService == null) {
            log.warn("Registry Service is null. Hence unable to fetch data from registry.");
            return null;
        }

        return Holder.INSTANCE;
    }

    private RegistryManager() {
        try {

            if (!registryService.resourceExists(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE +
                    CloudControllerConstants.TENANT_RESOURCE)) {
                registryService.put(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE +
                                CloudControllerConstants.TENANT_RESOURCE,
                        registryService.newCollection());
            }
        } catch (RegistryException e) {
            String msg =
                    "Failed to create the registry resource " +
                            CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE + CloudControllerConstants.TENANT_RESOURCE;
            log.error(msg, e);
            throw new CloudControllerException(msg, e);
        }
    }

    /**
     * Persist CC data object in the ST registry based on tenantId.
     *
     * @param tenantId tenantId of the requested tenant.
     * @param dataObj  object to be persisted.
     */
    public synchronized void persist(int tenantId, FasterLookUpDataHolder dataObj) throws RegistryException {
        try {

            PrivilegedCarbonContext ctx = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            ctx.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            ctx.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

            registryService.beginTransaction();

            Resource nodeResource = registryService.newResource();

            nodeResource.setContent(Serializer.serializeToByteArray(dataObj));

            registryService.put(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE +
                    CloudControllerConstants.TENANT_RESOURCE + PATH_SEPARATOR + tenantId +
                    CloudControllerConstants.DATA_RESOURCE, nodeResource);

            registryService.commitTransaction();

        } catch (Exception e) {
            String msg = "Failed to persist the cloud controller data in registry for tenant: " + tenantId;
            registryService.rollbackTransaction();
            log.error(msg, e);
            throw new CloudControllerException(msg, e);

        }
    }

    /**
     * Persist Topology object in the ST registry based on tenantId.
     *
     * @param tenantId tenantId of the requested tenant
     * @param topology Topology object to be persisted
     * @throws RegistryException
     */
    public synchronized void persistTopology(int tenantId, Topology topology) throws RegistryException {
        try {

            PrivilegedCarbonContext ctx = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            ctx.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            ctx.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

            registryService.beginTransaction();

            Resource nodeResource = registryService.newResource();

            nodeResource.setContent(Serializer.serializeToByteArray(topology));

            registryService.put(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE +
                    CloudControllerConstants.TENANT_RESOURCE + PATH_SEPARATOR + tenantId +
                    CloudControllerConstants.TOPOLOGY_RESOURCE, nodeResource);

            registryService.commitTransaction();

        } catch (Exception e) {
            String msg = "Failed to persist the cloud controller topology in registry for tenant: " + tenantId;
            registryService.rollbackTransaction();
            log.error(msg, e);
            throw new CloudControllerException(msg, e);

        }
    }

    /**
     * Retrieve CC data object in the ST registry based on tenantId.
     *
     * @param tenantId tenantId of the requested tenant
     * @return Object retrieved CC data
     */
    public synchronized Object retrieve(int tenantId) {

        try {
            PrivilegedCarbonContext ctx = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            ctx.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            ctx.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            Resource resource = registryService.get(
                    CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE +
                            CloudControllerConstants.TENANT_RESOURCE + PATH_SEPARATOR + tenantId +
                            CloudControllerConstants.DATA_RESOURCE);

            return resource.getContent();

        } catch (ResourceNotFoundException ignore) {
            // this means, we've never persisted CC info in registry
            return null;
        } catch (RegistryException e) {
            String msg = "Failed to retrieve cloud controller data from registry for tenant: " + tenantId;
            log.error(msg, e);
            throw new CloudControllerException(msg, e);
        }

    }

    /**
     * Retrieve topology object in the ST registry based on tenantId.
     * @param tenantId tenantId of the requested tenant
     * @return Object retrieved topology
     */
    public synchronized Object retrieveTopology(int tenantId) {

        try {
            PrivilegedCarbonContext ctx = PrivilegedCarbonContext
                    .getThreadLocalCarbonContext();
            ctx.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            ctx.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

            Resource resource = registryService
                    .get(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE +
                            CloudControllerConstants.TENANT_RESOURCE + PATH_SEPARATOR + tenantId +
                            CloudControllerConstants.TOPOLOGY_RESOURCE);

            return resource.getContent();

        } catch (ResourceNotFoundException ignore) {
            // this means, we've never persisted CC info in registry
            return null;
        } catch (RegistryException e) {
            String msg = "Failed to retrieve cloud controller data from registry.";
            log.error(msg, e);
            throw new CloudControllerException(msg, e);
        }

    }

    /**
     * Retrieve Complete Topology from the ST registry by iterating through the Registry Collection
     * @return Map<Integer, Object> tenantId to Object Maps
     */
    public synchronized Map<Integer, Object> retrieveCompleteTopology() {

        Map<Integer, Object> topologyList = new HashMap<Integer, Object>();
        try {
            PrivilegedCarbonContext ctx = PrivilegedCarbonContext
                    .getThreadLocalCarbonContext();
            ctx.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            ctx.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

            Resource resource = registryService
                    .get(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE +
                            CloudControllerConstants.TENANT_RESOURCE);

            Collection tenantCollection = (Collection) resource;
            String[] tenantDirectories = tenantCollection.getChildren();

            for (String topologyLocation : tenantDirectories) {
                int lastIndex = topologyLocation.lastIndexOf(PATH_SEPARATOR);
                int tenantId = Integer.parseInt(topologyLocation.substring(lastIndex + 1));
                topologyList.put(tenantId, registryService.get(topologyLocation +
                        CloudControllerConstants.TOPOLOGY_RESOURCE).getContent());
            }
            return topologyList;

        } catch (ResourceNotFoundException ignore) {
            // this means, we've never persisted CC info in registry
            return null;
        } catch (RegistryException e) {
            String msg = "Failed to retrieve cloud controller data from registry.";
            log.error(msg, e);
            throw new CloudControllerException(msg, e);
        }

    }

}
