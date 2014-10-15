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
package org.apache.stratos.cloud.controller.topology;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.cloud.controller.runtime.CommonDataHolder;
import org.apache.stratos.cloud.controller.runtime.FasterLookUpDataHolder;
import org.apache.stratos.cloud.controller.runtime.FasterLookupDataHolderManager;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.ntask.core.Task;

public class TopologySynchronizerTask implements Task{
    private static final Log log = LogFactory.getLog(TopologySynchronizerTask.class);
    private static CommonDataHolder commonDataHolder = CommonDataHolder.getInstance();

    @Override
    public void execute() {
        if (log.isDebugEnabled()) {
            log.debug("Executing topology synchronization task");
        }
        int tenantId = getTenantId();
        // this is a temporary fix to avoid task execution - limitation with ntask
        if(!CommonDataHolder.getInstance().getEnableTopologySync()){
            if(log.isWarnEnabled()) {
                log.warn("Topology synchronization is disabled.");
            }
            return;
        }
        
    	// publish to the topic 
        if (TopologyManager.getTopology(tenantId) != null) {
            TopologyEventPublisher.sendCompleteTopologyEvent(tenantId, TopologyManager.getTopology(tenantId));
        }
    }
    
    @Override
    public void init() {

    	// this is a temporary fix to avoid task execution - limitation with ntask
		if(!CommonDataHolder.getInstance().getEnableTopologySync()){
            if(log.isWarnEnabled()) {
                log.warn("Topology synchronization is disabled.");
            }
			return;
		}
    }

    @Override
    public void setProperties(Map<String, String> arg0) {}

    private int getTenantId(){
        return CarbonContext.getThreadLocalCarbonContext().getTenantId();
    }
}
