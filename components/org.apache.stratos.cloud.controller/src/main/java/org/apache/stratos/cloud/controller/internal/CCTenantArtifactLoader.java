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
package org.apache.stratos.cloud.controller.internal;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.stratos.cloud.controller.runtime.FasterLookupDataHolderManager;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.utils.AbstractAxis2ConfigurationContextObserver;


public class CCTenantArtifactLoader extends AbstractAxis2ConfigurationContextObserver
{
	public void creatingConfigurationContext(int tenantId) {
    }

    public void createdConfigurationContext(ConfigurationContext configContext) {
    	if(!checkIfArtifactsAreInSync()){
        	updateArtifacts();
        }
    }

    public void terminatingConfigurationContext(ConfigurationContext configCtx) {	
    	clearArtifacts();
    }

    public void terminatedConfigurationContext(ConfigurationContext configCtx) {
    }
    
    private boolean checkIfArtifactsAreInSync()
    {
    	int tenantId = CarbonContext.getThreadLocalCarbonContext().getTenantId();
    	
    	// check whether tenant Id is present in inMemModels 
    	return (FasterLookupDataHolderManager.getInstance().isFasterLookupDataHoldersInMemoryModel(tenantId));
    }
    
    private void updateArtifacts()
    {
    	// Adding the registry stored FasterLookupDataHolderManager to the information model
        FasterLookupDataHolderManager.getInstance().loadFasterLookupDataHolderToMemoryModel();
    }
    
    private void clearArtifacts()
    {
    	int currentTenantId = CarbonContext.getThreadLocalCarbonContext().getTenantId();
    	
    	// Adding the registry stored partitions to the information model
//        PartitionManager.getInstance().removePartitionsFromInformationModel(currentTenantId);
//
//        // Adding the registry stored autoscaling policies to the information model
//        PolicyManager.getInstance().removeASPoliciesFromInformationModel(currentTenantId);
//
//        // Adding the registry stored deployment policies to the information model
//        PolicyManager.getInstance().removeDeploymentPoliciesFromInformationModel(currentTenantId);
    }
}
