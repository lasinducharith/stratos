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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.cloud.controller.persist.Deserializer;
import org.apache.stratos.cloud.controller.registry.RegistryManager;
import org.wso2.carbon.context.CarbonContext;

public class FasterLookupDataHolderManager {

    private static final Log log = LogFactory.getLog(FasterLookupDataHolderManager.class);

    private static Map<Integer, FasterLookUpDataHolder> tenantIdTofasterLookUpDataHolderMap = new HashMap<Integer, FasterLookUpDataHolder>();

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

    public boolean isFasterLookupDataHoldersInMemoryModel(int tenantId) {
        return (tenantIdTofasterLookUpDataHolderMap.containsKey(tenantId));
    }

    public static void loadFasterLookupDataHolderToMemoryModel() {
        Object obj = RegistryManager.getInstance(CarbonContext.getThreadLocalCarbonContext().getTenantId()).retrieve();
        if (obj != null) {
            try {
                Object dataObj = Deserializer
                        .deserializeFromByteArray((byte[]) obj);
                if (dataObj instanceof FasterLookUpDataHolder) {
                    FasterLookUpDataHolder serializedObj = (FasterLookUpDataHolder) dataObj;
                    FasterLookUpDataHolder currentData = FasterLookUpDataHolder
                            .getInstance();

                    // assign necessary data
                    currentData.setClusterIdToContext(serializedObj.getClusterIdToContext());
                    currentData.setMemberIdToContext(serializedObj.getMemberIdToContext());
                    currentData.setClusterIdToMemberContext(serializedObj.getClusterIdToMemberContext());
                    currentData.setCartridges(serializedObj.getCartridges());
                    currentData.setKubClusterIdToKubClusterContext(serializedObj.getKubClusterIdToKubClusterContext());
                    addFasterLookupDataHolderToMemoryModel(currentData);

                    if (log.isDebugEnabled()) {

                        log.debug("Cloud Controller Data is retrieved from registry.");
                    }
                } else {
                    if (log.isDebugEnabled()) {

                        log.debug("Cloud Controller Data cannot be found in registry.");
                    }
                }
            } catch (Exception e) {

                String msg = "Unable to acquire data from Registry. Hence, any historical data will not get reflected.";
                log.warn(msg, e);
            }
        }
    }

    private static void addFasterLookupDataHolderToMemoryModel(
            FasterLookUpDataHolder fasterLookUpDataHolder) {
        int tenantId = CarbonContext.getThreadLocalCarbonContext().getTenantId();
        if (!tenantIdTofasterLookUpDataHolderMap.containsKey(tenantId)) {
            tenantIdTofasterLookUpDataHolderMap.put(tenantId, fasterLookUpDataHolder);
        }
    }

    public static FasterLookUpDataHolder getDataHolderForTenant(){
       int tenantId = CarbonContext.getThreadLocalCarbonContext().getTenantId();
       if(getDataHolderForTenantFromMemoryModel(tenantId)==null){
           loadFasterLookupDataHolderToMemoryModel();
           return getDataHolderForTenantFromMemoryModel(tenantId);
       }
        else{
           return getDataHolderForTenantFromMemoryModel(tenantId);
       }
    }


    private static FasterLookUpDataHolder getDataHolderForTenantFromMemoryModel(int tenantId){
        if (tenantIdTofasterLookUpDataHolderMap.containsKey(tenantId)) {
            return tenantIdTofasterLookUpDataHolderMap.get(tenantId);
        }
        return null;
    }
}
