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

package org.apache.stratos.cloud.controller.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.cloud.controller.exception.CloudControllerException;
import org.apache.stratos.cloud.controller.persist.Deserializer;
import org.apache.stratos.cloud.controller.registry.RegistryManager;
import org.wso2.carbon.registry.core.exceptions.RegistryException;

public class FasterLookupDataHolderManager {

    private static final Log log = LogFactory.getLog(FasterLookupDataHolderManager.class);

    private static Map<Integer, FasterLookUpDataHolder> tenantIdToFasterLookUpDataHolderMap = new ConcurrentHashMap<Integer, FasterLookUpDataHolder>();

    /* An instance of a FasterLookupDataHolderManager is created when the class is loaded.
     * Since the class is loaded only once, it is guaranteed that an object of
     * FasterLookupDataHolderManager is created only once. Hence it is singleton.
     */
    private static class InstanceHolder {
        private static final FasterLookupDataHolderManager INSTANCE = new FasterLookupDataHolderManager();
    }


    public static FasterLookupDataHolderManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private FasterLookupDataHolderManager() {
    }

    private static FasterLookUpDataHolder loadFasterLookupDataHolderFromRegistry(int tenantId) {

        Object obj = RegistryManager.getInstance().retrieve(tenantId);
        if (obj != null) {
            try {
                Object dataObj = Deserializer
                        .deserializeFromByteArray((byte[]) obj);
                if (dataObj instanceof FasterLookUpDataHolder) {
                    FasterLookUpDataHolder serializedObj = (FasterLookUpDataHolder) dataObj;
                    FasterLookUpDataHolder currentData = new FasterLookUpDataHolder();

                    // assign necessary data
                    currentData.setClusterIdToContext(serializedObj.getClusterIdToContext());
                    currentData.setMemberIdToContext(serializedObj.getMemberIdToContext());
                    currentData.setClusterIdToMemberContext(serializedObj.getClusterIdToMemberContext());
                    currentData.setCartridges(serializedObj.getCartridges());
                    currentData.setKubClusterIdToKubClusterContext(serializedObj.getKubClusterIdToKubClusterContext());

                    if (log.isDebugEnabled()) {
                        log.debug("Cloud Controller Data is retrieved from registry for tenant: " + tenantId);
                    }
                    return currentData;

                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Cloud Controller Data cannot be found in registry for tenant: " + tenantId);
                    }
                }
            } catch (Exception e) {
                String msg = "Unable to acquire data from Registry. Hence, any historical data will not get reflected.";
                log.warn(msg, e);
            }
        }
        return null;
    }

    /**
     * Update tenant's FasterLookupDataHolder in memory model
     * @param tenantId tenantId of the Object
     * @param fasterLookUpDataHolder dataHolder object of tenant
     */
    public static void addFasterLookupDataHolderToMemoryModel(int tenantId,
                                                              FasterLookUpDataHolder fasterLookUpDataHolder) {
        if (fasterLookUpDataHolder != null) {
            tenantIdToFasterLookUpDataHolderMap.put(tenantId, fasterLookUpDataHolder);
        }
    }

    /**
     * Get DataHolder from in-memory model or registry
     * @param tenantId tenantId of the requested DataHolder Object
     * @return FasterLookUpDataHolder requested dataHolder object
     */
    public static FasterLookUpDataHolder getDataHolderForTenant(int tenantId) {
        //Check in memory model
        if (getDataHolderForTenantFromMemoryModel(tenantId) == null) {
            //if not retrieve from registry
            FasterLookUpDataHolder fasterLookUpDataHolder = loadFasterLookupDataHolderFromRegistry(tenantId);
            if (fasterLookUpDataHolder == null) {
                //if it is not even in registry, create a new Object
                addFasterLookupDataHolderToMemoryModel(tenantId, new FasterLookUpDataHolder());
            } else {
                addFasterLookupDataHolderToMemoryModel(tenantId, fasterLookUpDataHolder);
            }
        }
        return getDataHolderForTenantFromMemoryModel(tenantId);
    }

    /**
     * Persist data in registry.
     */
    private void persist(int tenantId, FasterLookUpDataHolder dataHolder) {
        try {
            RegistryManager.getInstance().persist(tenantId,
                    dataHolder);
        } catch (RegistryException e) {
            String msg = "Failed to persist the Cloud Controller data in registry. Further, transaction roll back also failed.";
            throw new CloudControllerException(msg, e);
        }
    }

    private static FasterLookUpDataHolder getDataHolderForTenantFromMemoryModel(int tenantId) {
        if (tenantIdToFasterLookUpDataHolderMap.containsKey(tenantId)) {
            return tenantIdToFasterLookUpDataHolderMap.get(tenantId);
        }
        return null;
    }
}