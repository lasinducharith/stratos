package org.apache.stratos.autoscaler.rule;
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
import org.apache.stratos.autoscaler.Constants;
import org.apache.stratos.autoscaler.KubernetesClusterContext;
import org.apache.stratos.autoscaler.NetworkPartitionLbHolder;
import org.apache.stratos.autoscaler.PartitionContext;
import org.apache.stratos.autoscaler.algorithm.AutoscaleAlgorithm;
import org.apache.stratos.autoscaler.algorithm.OneAfterAnother;
import org.apache.stratos.autoscaler.algorithm.RoundRobin;
import org.apache.stratos.autoscaler.client.cloud.controller.CloudControllerClient;
import org.apache.stratos.autoscaler.client.cloud.controller.InstanceNotificationClient;
import org.apache.stratos.autoscaler.exception.SpawningException;
import org.apache.stratos.autoscaler.exception.TerminationException;
import org.apache.stratos.autoscaler.partition.PartitionManager;
import org.apache.stratos.cloud.controller.stub.pojo.MemberContext;

/**
 * This will have utility methods that need to be executed from rule file...
 */
public class RuleTasksDelegator {

    public static final double SCALE_UP_FACTOR = 0.8;   //get from config
    public static final double SCALE_DOWN_FACTOR = 0.2;

    private static final Log log = LogFactory.getLog(RuleTasksDelegator.class);

    public double getPredictedValueForNextMinute(float average, float gradient, float secondDerivative, int timeInterval){
        double predictedValue;
//        s = u * t + 0.5 * a * t * t
        if(log.isDebugEnabled()){
            log.debug(String.format("Predicting the value, [average]: %s , [gradient]: %s , [second derivative]" +
                    ": %s , [time intervals]: %s ", average, gradient, secondDerivative, timeInterval));
        }
        predictedValue = average + gradient * timeInterval + 0.5 * secondDerivative * timeInterval * timeInterval;

        return predictedValue;
    }

    public AutoscaleAlgorithm getAutoscaleAlgorithm(String partitionAlgorithm){
        AutoscaleAlgorithm autoscaleAlgorithm = null;
        if(log.isDebugEnabled()){
            log.debug(String.format("Partition algorithm is ", partitionAlgorithm));
        }
        if(Constants.ROUND_ROBIN_ALGORITHM_ID.equals(partitionAlgorithm)){

            autoscaleAlgorithm = new RoundRobin();
        } else if(Constants.ONE_AFTER_ANOTHER_ALGORITHM_ID.equals(partitionAlgorithm)){

            autoscaleAlgorithm = new OneAfterAnother();
        } else {
            if(log.isErrorEnabled()){
                log.error(String.format("Partition algorithm %s could not be identified !", partitionAlgorithm));
            }
        }
        return autoscaleAlgorithm;
    }
    
    public void delegateSpawn(int tenantId, PartitionContext partitionContext, String clusterId, String lbRefType, boolean isPrimary) {
    	
        try {

            String nwPartitionId = partitionContext.getNetworkPartitionId();
            NetworkPartitionLbHolder lbHolder =
                                          PartitionManager.getInstance()
                                                          .getNetworkPartitionLbHolder(nwPartitionId);
            String lbClusterId = getLbClusterId(lbRefType, partitionContext, lbHolder);
            MemberContext memberContext =
                                         CloudControllerClient.getInstance()
                                                              .spawnAnInstance(tenantId, partitionContext.getPartition(),
                                                                      clusterId,
                                                                      lbClusterId, partitionContext.getNetworkPartitionId(),
                                                                      isPrimary,
                                                                      partitionContext.getMinimumMemberCount());
            if (memberContext != null) {
                partitionContext.addPendingMember(memberContext);
                if(log.isDebugEnabled()){
                    log.debug(String.format("Pending member added, [member] %s [partition] %s", memberContext.getMemberId(),
                            memberContext.getPartition().getId()));
                }
            } else if(log.isDebugEnabled()){
                log.debug("Returned member context is null, did not add to pending members");
            }

        } catch (Throwable e) {
            String message = "Cannot spawn an instance";
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
    }

    // Original method. Assume this is invoked from mincheck.drl
    
   /* public void delegateSpawn(PartitionContext partitionContext, String clusterId, String lbRefType) {
        try {

            String nwPartitionId = partitionContext.getNetworkPartitionId();
                                                         .getNetworkPartitionLbHolder(nwPartitionId);
            NetworkPartitionLbHolder lbHolder =
                                          PartitionManager.getInstance()
                                                          .getNetworkPartitionLbHolder(nwPartitionId);

            
            String lbClusterId = getLbClusterId(lbRefType, partitionContext, lbHolder);

            MemberContext memberContext =
                                         CloudControllerClient.getInstance()
                                                              .spawnAnInstance(partitionContext.getPartition(),
                                                                      clusterId,
                                                                      lbClusterId, partitionContext.getNetworkPartitionId());
            if (memberContext != null) {
                partitionContext.addPendingMember(memberContext);
                if(log.isDebugEnabled()){
                    log.debug(String.format("Pending member added, [member] %s [partition] %s", memberContext.getMemberId(),
                            memberContext.getPartition().getId()));
                }
            } else if(log.isDebugEnabled()){
                log.debug("Returned member context is null, did not add to pending members");
            }

        } catch (Throwable e) {
            String message = "Cannot spawn an instance";
            log.error(message, e);
            throw new RuntimeException(message, e);
        }
   	}*/



    public static String getLbClusterId(String lbRefType, PartitionContext partitionCtxt, 
        NetworkPartitionLbHolder networkPartitionLbHolder) {

       String lbClusterId = null;

        if (lbRefType != null) {
            if (lbRefType.equals(org.apache.stratos.messaging.util.Constants.DEFAULT_LOAD_BALANCER)) {
                lbClusterId = networkPartitionLbHolder.getDefaultLbClusterId();
//                lbClusterId = nwPartitionCtxt.getDefaultLbClusterId();
            } else if (lbRefType.equals(org.apache.stratos.messaging.util.Constants.SERVICE_AWARE_LOAD_BALANCER)) {
                String serviceName = partitionCtxt.getServiceName();
                lbClusterId = networkPartitionLbHolder.getLBClusterIdOfService(serviceName);
//                lbClusterId = nwPartitionCtxt.getLBClusterIdOfService(serviceName);
            } else {
                log.warn("Invalid LB reference type defined: [value] "+lbRefType);
            }
        }
        if (log.isDebugEnabled()){
            log.debug(String.format("Getting LB id for spawning instance [lb reference] %s ," +
                    " [partition] %s [network partition] %s [Lb id] %s ", lbRefType, partitionCtxt.getPartitionId(),
                    networkPartitionLbHolder.getNetworkPartitionId(), lbClusterId));
        }
       return lbClusterId;
    }

    public void delegateTerminate(PartitionContext partitionContext, String memberId) {
        try {
            //calling SM to send the instance notification event.
            InstanceNotificationClient.getInstance().sendMemberCleanupEvent(memberId);
            partitionContext.moveActiveMemberToTerminationPendingMembers(memberId);
            //CloudControllerClient.getInstance().terminate(memberId);
        } catch (Throwable e) {
            log.error("Cannot terminate instance", e);
        }
    }

    public void terminateObsoleteInstance(int tenantId, String memberId) {
        try {
            CloudControllerClient.getInstance().terminate(tenantId, memberId);
        } catch (Throwable e) {
            log.error("Cannot terminate instance", e);
        }
    }

   	public void delegateTerminateAll(int tenantId, String clusterId) {
           try {

               CloudControllerClient.getInstance().terminateAllInstances(tenantId, clusterId);
           } catch (Throwable e) {
               log.error("Cannot terminate instance", e);
           }
       }

    public void delegateStartContainers(int tenantId, KubernetesClusterContext kubernetesClusterContext) {
        try {
            String kubernetesClusterId = kubernetesClusterContext.getKubernetesClusterID();
            String clusterId = kubernetesClusterContext.getClusterId();
            CloudControllerClient ccClient = CloudControllerClient.getInstance();
            MemberContext[] memberContexts = ccClient.startContainers(tenantId, kubernetesClusterId, clusterId);
            if (null != memberContexts) {
                for (MemberContext memberContext : memberContexts) {
                    if (null != memberContext) {
                        kubernetesClusterContext.addPendingMember(memberContext);
                        kubernetesClusterContext.setServiceClusterCreated(true);
                        if (log.isDebugEnabled()) {
                            log.debug(String.format(
                                    "Pending member added, [member] %s [kub cluster] %s",
                                    memberContext.getMemberId(), kubernetesClusterId));
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Returned member context is null, did not add any pending members");
                        }
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Returned member context is null, did not add to pending members");
                }
            }
        } catch (Exception e) {
            log.error("Cannot create containers ", e);
        }
    }

    public void delegateScaleUpContainers(int tenantId, KubernetesClusterContext kubernetesClusterContext,
                                         int newReplicasCount) {
        String clusterId = kubernetesClusterContext.getClusterId();
        try {
            CloudControllerClient ccClient = CloudControllerClient.getInstance();
            // getting newly created pods' member contexts
            MemberContext[] memberContexts = ccClient.updateContainers(tenantId, clusterId, newReplicasCount);
            if (null != memberContexts) {
                for (MemberContext memberContext : memberContexts) {
                    if (null != memberContext) {
                        kubernetesClusterContext.addPendingMember(memberContext);
                        if (log.isDebugEnabled()) {
                            String kubernetesClusterID = kubernetesClusterContext.getKubernetesClusterID();
                            log.debug(String.format(
                                    "Pending member added, [member] %s [kub cluster] %s",
                                    memberContext.getMemberId(), kubernetesClusterID));
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Returned member context is null, did not add any pending members");
                        }
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Returned array of member context is null, did not add to pending members");
                }
            }
        } catch (Exception e) {
            log.error("Scaling up failed, couldn't update kubernetes controller ", e);
        }
    }
    
    public void delegateScaleDownContainers(int tenantId, KubernetesClusterContext kubernetesClusterContext,
    										int newReplicasCount) {
    	String clusterId = kubernetesClusterContext.getClusterId();
    	try {
    		CloudControllerClient ccClient = CloudControllerClient.getInstance();
    		// getting terminated pods's member contexts
    		MemberContext[] memberContexts = ccClient.updateContainers(tenantId, clusterId, newReplicasCount);
    		if (null != memberContexts) {
				for (MemberContext memberContext : memberContexts) {
					if (null != memberContext) {
						// we are not removing from active/pending list, it will be handled in AS event receiver
						if (log.isDebugEnabled()) {
							log.debug(String.format("Scaling down, terminated the member with id %s in cluster %s", 
									memberContext.getMemberId(), memberContext.getClusterId()));
						}
					}
				}
			}
     	} catch (Exception e) {
    		log.error("Scaling down failed, couldn't update kubernetes controller ", e);
    	}
    }
    
    public void delegateTerminateContainer(int tenantId, KubernetesClusterContext kubernetesClusterContext, String memberId) {
    	try {
    		CloudControllerClient ccClient = CloudControllerClient.getInstance();
    		ccClient.terminateContainer(tenantId, memberId);
    	} catch (TerminationException e) {
    		log.error("Cannot delete container ", e);
		}
    }

    public int getPredictedReplicasForStat(int minReplicas, float statUpperLimit, float statPredictedValue) {
        if (statUpperLimit == 0) {
            return 0;
        }
        float predictedValue = ((minReplicas / statUpperLimit) * statPredictedValue);
        return (int) Math.ceil(predictedValue);
    }
}
