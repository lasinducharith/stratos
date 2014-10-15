/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.stratos.autoscaler.monitor;

import java.util.Properties;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.KubernetesClusterContext;
import org.apache.stratos.autoscaler.client.cloud.controller.CloudControllerClient;
import org.apache.stratos.autoscaler.exception.SpawningException;
import org.apache.stratos.autoscaler.policy.model.AutoscalePolicy;
import org.apache.stratos.autoscaler.rule.AutoscalerRuleEvaluator;
import org.apache.stratos.autoscaler.util.AutoScalerConstants;
import org.apache.stratos.autoscaler.util.ConfUtil;
import org.apache.stratos.cloud.controller.stub.pojo.MemberContext;
import org.apache.stratos.common.constants.StratosConstants;
import org.apache.stratos.messaging.domain.topology.Cluster;
import org.apache.stratos.messaging.domain.topology.ClusterStatus;
import org.apache.stratos.messaging.domain.topology.Service;
import org.apache.stratos.messaging.message.receiver.topology.TopologyManager;

/*
 * It is monitoring a kubernetes service cluster periodically.
 */
public final class KubernetesServiceClusterMonitor extends KubernetesClusterMonitor {

    private static final Log log = LogFactory.getLog(KubernetesServiceClusterMonitor.class);

    private String lbReferenceType;
    private int numberOfReplicasInServiceCluster = 0;
    int retryInterval = 60000;

    public KubernetesServiceClusterMonitor(KubernetesClusterContext kubernetesClusterCtxt,
                                           String serviceClusterID, String serviceId,
                                           AutoscalePolicy autoscalePolicy) {
        super(serviceClusterID, serviceId, kubernetesClusterCtxt,
              new AutoscalerRuleEvaluator(), autoscalePolicy);
        readConfigurations();
    }

    @Override
    public void run() {

        if (log.isDebugEnabled()) {
            log.debug("KubernetesServiceClusterMonitor is running.. " + this.toString());
        }
        try {
            if (!ClusterStatus.In_Maintenance.equals(getStatus())) {
                monitor();
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("KubernetesServiceClusterMonitor is suspended as the cluster is in "
                              + ClusterStatus.In_Maintenance + " mode......");
                }
            }
        } catch (Exception e) {
            log.error("KubernetesServiceClusterMonitor : Monitor failed." + this.toString(),
                      e);
        }
    }

    @Override
    protected void monitor() {
        minCheck();
        scaleCheck();
    }

    private void scaleCheck() {
        boolean rifReset = getKubernetesClusterCtxt().isRifReset();
        boolean memoryConsumptionReset = getKubernetesClusterCtxt().isMemoryConsumptionReset();
        boolean loadAverageReset = getKubernetesClusterCtxt().isLoadAverageReset();
        if (log.isDebugEnabled()) {
            log.debug("flag of rifReset : " + rifReset
                      + " flag of memoryConsumptionReset : "
                      + memoryConsumptionReset + " flag of loadAverageReset : "
                      + loadAverageReset);
        }
        String kubernetesClusterID = getKubernetesClusterCtxt().getKubernetesClusterID();
        String clusterId = getClusterId();
        if (rifReset || memoryConsumptionReset || loadAverageReset) {
            getScaleCheckKnowledgeSession().setGlobal("clusterId", clusterId);
            getScaleCheckKnowledgeSession().setGlobal("autoscalePolicy", autoscalePolicy);
            getScaleCheckKnowledgeSession().setGlobal("rifReset", rifReset);
            getScaleCheckKnowledgeSession().setGlobal("mcReset", memoryConsumptionReset);
            getScaleCheckKnowledgeSession().setGlobal("laReset", loadAverageReset);
            if (log.isDebugEnabled()) {
                log.debug(String.format(
                        "Running scale check for [kub-cluster] : %s [cluster] : %s ", kubernetesClusterID, getClusterId()));
            }
            scaleCheckFactHandle = AutoscalerRuleEvaluator.evaluateScaleCheck(
                    getScaleCheckKnowledgeSession(), scaleCheckFactHandle, getKubernetesClusterCtxt());
            getKubernetesClusterCtxt().setRifReset(false);
            getKubernetesClusterCtxt().setMemoryConsumptionReset(false);
            getKubernetesClusterCtxt().setLoadAverageReset(false);
        } else if (log.isDebugEnabled()) {
            log.debug(String.format("Scale check will not run since none of the statistics have not received yet for "
                                    + "[kub-cluster] : %s [cluster] : %s", kubernetesClusterID, clusterId));
        }
    }

    private void minCheck() {
        getMinCheckKnowledgeSession().setGlobal("clusterId", getClusterId());
        String kubernetesClusterID = getKubernetesClusterCtxt().getKubernetesClusterID();
        if (log.isDebugEnabled()) {
            log.debug(String.format(
                    "Running min check for [kub-cluster] : %s [cluster] : %s ", kubernetesClusterID, getClusterId()));
        }
        minCheckFactHandle = AutoscalerRuleEvaluator.evaluateMinCheck(
                getMinCheckKnowledgeSession(), minCheckFactHandle,
                getKubernetesClusterCtxt());
    }

    @Override
    public void destroy() {
        getMinCheckKnowledgeSession().dispose();
        getScaleCheckKnowledgeSession().dispose();
        setDestroyed(true);
        stopScheduler();
        if (log.isDebugEnabled()) {
            log.debug("KubernetesServiceClusterMonitor Drools session has been disposed. " + this.toString());
        }
    }

    @Override
    protected void readConfigurations() {
        XMLConfiguration conf = ConfUtil.getInstance(null).getConfiguration();
        int monitorInterval = conf.getInt(AutoScalerConstants.AUTOSCALER_MONITOR_INTERVAL, 90000);
        setMonitorIntervalMilliseconds(monitorInterval);
        if (log.isDebugEnabled()) {
            log.debug("KubernetesServiceClusterMonitor task interval: " + getMonitorIntervalMilliseconds());
        }
    }

    @Override
    public String toString() {
        return "KubernetesServiceClusterMonitor "
               + "[ kubernetesHostClusterId=" + getKubernetesClusterCtxt().getKubernetesClusterID()
               + ", clusterId=" + getClusterId()
               + ", serviceId=" + getServiceId() + "]";
    }

    public String getLbReferenceType() {
        return lbReferenceType;
    }

    public void setLbReferenceType(String lbReferenceType) {
        this.lbReferenceType = lbReferenceType;
    }
}