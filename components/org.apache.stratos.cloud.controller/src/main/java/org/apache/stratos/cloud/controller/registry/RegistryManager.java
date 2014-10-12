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
import org.wso2.carbon.registry.api.RegistryService;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.ResourceNotFoundException;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.registry.api.RegistryException;

/**
 *
 */
public class RegistryManager {
    private final static Log log = LogFactory.getLog(RegistryManager.class);
    private static RegistryService registryService;
    private static Registry registry;

    private static class Holder {
        static final RegistryManager INSTANCE = new RegistryManager();
    }

    public static RegistryManager getInstance(int tenantId) {

        try {
            registryService = ServiceReferenceHolder.getInstance().getRegistryService();
            if (registryService == null) {
                log.warn("Registry Service is null. Hence unable to fetch data from registry.");
                return null;
            }
            registry = (Registry) registryService.getGovernanceSystemRegistry(tenantId);
        }
        catch (RegistryException e) {
            String msg = "Failed when retrieving Governance System Registry.";
            log.error(msg, e);
            throw new CloudControllerException(msg, e);
        }

        synchronized (RegistryManager.class) {
            if (registryService == null) {
                log.warn("Registry Service is null. Hence unable to fetch data from registry.");
                return null;
            }
        }
        return Holder.INSTANCE;
    }

    private RegistryManager() {
        try {

            if (!registry.resourceExists(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE)) {
                registry.put(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE,
                             registry.newCollection());
            }
        } catch (RegistryException e) {
            String msg =
                    "Failed to create the registry resource " +
                    CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE;
            log.error(msg, e);
            throw new CloudControllerException(msg, e);
        }
    }

    /**
     * Persist an object in the local registry.
     *
     * @param dataObj object to be persisted.
     */
    public synchronized void persist(FasterLookUpDataHolder dataObj) throws RegistryException {
        try {

            PrivilegedCarbonContext ctx = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            ctx.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            ctx.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

            registry.beginTransaction();

            Resource nodeResource = registry.newResource();

            nodeResource.setContent(Serializer.serializeToByteArray(dataObj));

            registry.put(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE + CloudControllerConstants.DATA_RESOURCE, nodeResource);

            registry.commitTransaction();

        } catch (Exception e) {
            String msg = "Failed to persist the cloud controller data in registry.";
            registry.rollbackTransaction();
            log.error(msg, e);
            throw new CloudControllerException(msg, e);

        }
    }

    public synchronized void persistTopology(Topology topology) throws RegistryException {
        try {

            PrivilegedCarbonContext ctx = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            ctx.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            ctx.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

            registry.beginTransaction();

            Resource nodeResource = registry.newResource();

            nodeResource.setContent(Serializer.serializeToByteArray(topology));

            registry.put(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE + CloudControllerConstants.TOPOLOGY_RESOURCE, nodeResource);

            registry.commitTransaction();

        } catch (Exception e) {
            String msg = "Failed to persist the cloud controller data in registry.";
            registry.rollbackTransaction();
            log.error(msg, e);
            throw new CloudControllerException(msg, e);

        }
    }


    public synchronized Object retrieve() {

        try {
            PrivilegedCarbonContext ctx = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            ctx.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            ctx.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            Resource resource = registry.get(
                    CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE + CloudControllerConstants.DATA_RESOURCE);

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

    public synchronized Object retrieveTopology() {

        try {
            PrivilegedCarbonContext ctx = PrivilegedCarbonContext
                    .getThreadLocalCarbonContext();
            ctx.setTenantId(MultitenantConstants.SUPER_TENANT_ID);
            ctx.setTenantDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);

            Resource resource = registry
                    .get(CloudControllerConstants.CLOUD_CONTROLLER_RESOURCE
                         + CloudControllerConstants.TOPOLOGY_RESOURCE);

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

}
