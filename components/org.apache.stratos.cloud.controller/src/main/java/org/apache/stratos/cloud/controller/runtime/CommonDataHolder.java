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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.cloud.controller.pojo.DataPublisherConfig;
import org.apache.stratos.cloud.controller.pojo.IaasProvider;
import org.apache.stratos.cloud.controller.pojo.TopologyConfig;
import org.wso2.carbon.databridge.agent.thrift.AsyncDataPublisher;

import java.io.Serializable;
import java.util.List;

public class CommonDataHolder implements Serializable {

    private static final Log log = LogFactory.getLog(CommonDataHolder.class);
    private static final long serialVersionUID = 5059620147037124821L;
    /**
     * List of IaaS Providers.
     */
    private List<IaasProvider> iaasProviders;
    private boolean enableBAMDataPublisher;
    private transient DataPublisherConfig dataPubConfig;
    private boolean enableTopologySync;
    private transient TopologyConfig topologyConfig;
    private String serializationDir;
    private boolean isPublisherRunning;
    private transient AsyncDataPublisher dataPublisher;


    public IaasProvider getIaasProvider(String type) {
        if(type == null) {
            return null;
        }

        for (IaasProvider iaasProvider : iaasProviders) {
            if(type.equals(iaasProvider.getType())) {
                return iaasProvider;
            }
        }
        return null;
    }

    public List<IaasProvider> getIaasProviders() {
        return iaasProviders;
    }

    public void setIaasProviders(List<IaasProvider> iaasProviders) {
        this.iaasProviders = iaasProviders;
    }

    public boolean getEnableBAMDataPublisher() {
        return enableBAMDataPublisher;
    }

    public void setEnableBAMDataPublisher(boolean enableBAMDataPublisher) {
        this.enableBAMDataPublisher = enableBAMDataPublisher;
    }

    public DataPublisherConfig getDataPubConfig() {
        return dataPubConfig;
    }

    public void setDataPubConfig(DataPublisherConfig dataPubConfig) {
        this.dataPubConfig = dataPubConfig;
    }

    public boolean getEnableTopologySync() {
        return enableTopologySync;
    }

    public void setEnableTopologySync(boolean enableTopologySync) {
        this.enableTopologySync = enableTopologySync;
    }

    public TopologyConfig getTopologyConfig() {
        return topologyConfig;
    }

    public void setTopologyConfig(TopologyConfig topologyConfig) {
        this.topologyConfig = topologyConfig;
    }

    public String getSerializationDir() {
        return serializationDir;
    }

    public void setSerializationDir(String serializationDir) {
        this.serializationDir = serializationDir;
    }

    public boolean isPublisherRunning() {
        return isPublisherRunning;
    }

    public void setPublisherRunning(boolean isPublisherRunning) {
        this.isPublisherRunning = isPublisherRunning;
    }

    public static CommonDataHolder getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private CommonDataHolder() {
    }

    private static class InstanceHolder {
        private static final CommonDataHolder INSTANCE = new CommonDataHolder();
    }

}