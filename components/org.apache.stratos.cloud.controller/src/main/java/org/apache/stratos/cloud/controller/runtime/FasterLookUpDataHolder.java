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
import org.apache.stratos.cloud.controller.pojo.*;
import org.wso2.carbon.databridge.agent.thrift.AsyncDataPublisher;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This object holds all runtime data and provides faster access. This is a Singleton class.
 */
public class FasterLookUpDataHolder implements Serializable{

    private static final long serialVersionUID = -2662307358852779897L;
    
    private static final Log log = LogFactory.getLog(FasterLookUpDataHolder.class);

	//private static volatile FasterLookUpDataHolder ctxt;

	/* We keep following maps in order to make the look up time, small. */
	
	/**
     * Key - cluster id
     * Value - list of {@link MemberContext}
     */
    private Map<String, List<MemberContext>> clusterIdToMemberContext = new ConcurrentHashMap<String, List<MemberContext>>();
    

    /**
	 * Key - member id
	 * Value - {@link MemberContext}
	 */
	private Map<String, MemberContext> memberIdToContext = new ConcurrentHashMap<String, MemberContext>();
	
	/**
	 * Key - Kubernetes cluster id
	 * Value - {@link KubernetesClusterContext}
	 */
	private Map<String, KubernetesClusterContext> kubClusterIdToKubClusterContext = 
			new ConcurrentHashMap<String, KubernetesClusterContext>();
	
	/**
	 * Key - cluster id
	 * Value - {@link ClusterContext}
	 */
	private Map<String, ClusterContext> clusterIdToContext = new ConcurrentHashMap<String, ClusterContext>();
	
	/**
	 * This works as a cache to hold already validated partitions against a cartridge type.
	 * Key - cartridge type
	 * Value - list of partition ids
	 */
	private Map<String, List<String>> cartridgeTypeToPartitionIds = new ConcurrentHashMap<String, List<String>>();
	
	/**
     * Thread pool used in this task to execute parallel tasks.
     */
    private transient ExecutorService executor = Executors.newFixedThreadPool(20);
	
	/**
	 * List of registered {@link Cartridge}s
	 */
	private List<Cartridge> cartridges;


//	public static FasterLookUpDataHolder getInstance() {
//
//		if (ctxt == null) {
//			synchronized (FasterLookUpDataHolder.class) {
//				if (ctxt == null && RegistryManager.getInstance(CarbonContext.getThreadLocalCarbonContext().getTenantId()) != null) {
//
//					Object obj = RegistryManager.getInstance(CarbonContext.getThreadLocalCarbonContext().getTenantId()).retrieve();
//					if (obj != null) {
//						if (obj instanceof FasterLookUpDataHolder) {
//							ctxt = (FasterLookUpDataHolder) obj;
//						}
//					}
//				}
//				if(ctxt == null) {
//					ctxt = new FasterLookUpDataHolder();
//				}
//			}
//		}
//
//		return ctxt;
//	}

	public FasterLookUpDataHolder() {

		cartridges = new ArrayList<Cartridge>();
		
	}

	public List<Cartridge> getCartridges() {
		return cartridges;
	}
	
	public void setCartridges(List<Cartridge> cartridges) {
	    this.cartridges = cartridges;
	}

	public Cartridge getCartridge(String cartridgeType) {
		for (Cartridge cartridge : cartridges) {
			if (cartridge.getType().equals(cartridgeType)) {
				return cartridge;
			}
		}

		return null;

	}
	
	public void addCartridge(Cartridge newCartridges) {
	
		cartridges.add(newCartridges);
	}

	public void removeCartridges(List<Cartridge> cartridges) {
		if (this.cartridges != null) {
			this.cartridges.removeAll(cartridges);
		}

	}
	
    public void addMemberContext(MemberContext ctxt) {
        memberIdToContext.put(ctxt.getMemberId(), ctxt);
        
        List<MemberContext> ctxts;
        
        if((ctxts = clusterIdToMemberContext.get(ctxt.getClusterId())) == null) {
            ctxts = new ArrayList<MemberContext>();
        } 
        if(ctxts.contains(ctxt)) {
        	ctxts.remove(ctxt);
        }
        ctxts.add(ctxt);
        clusterIdToMemberContext.put(ctxt.getClusterId(), ctxts);
        if(log.isDebugEnabled()) {
        	
        	log.debug("Added Member Context to the information model. "+ctxt);
        }
    }
    
    public void removeMemberContextsOfCluster(String clusterId) {
        List<MemberContext> ctxts = clusterIdToMemberContext.remove(clusterId);
        if(ctxts == null) {
            return;
        }
        for (MemberContext memberContext : ctxts) {
            String memberId = memberContext.getMemberId();
            memberIdToContext.remove(memberId);
        }
        if(log.isDebugEnabled()) {
        	
        	log.debug("Removed Member Context from the information model. "+ ctxts);
        }
    }
    
    public void removeMemberContext(String memberId, String clusterId) {
    	memberIdToContext.remove(memberId);
        List<MemberContext> ctxts = clusterIdToMemberContext.get(clusterId);
        if(ctxts == null) {
            return;
        }
        
        List<MemberContext> newCtxts =  new ArrayList<MemberContext>(ctxts);
        
        for (Iterator<MemberContext> iterator = newCtxts.iterator(); iterator.hasNext();) {
			MemberContext memberContext = (MemberContext) iterator.next();
			if(memberId.equals(memberContext.getMemberId())) {
				if(log.isDebugEnabled()) {
					
					log.debug("MemberContext [id]: "+memberId+" removed from information model.");
				}
            	iterator.remove();
            }
		}
        
        clusterIdToMemberContext.put(clusterId, newCtxts);
    }
    
    public MemberContext getMemberContextOfMemberId(String memberId) {
        return memberIdToContext.get(memberId);
    }
    
    public List<MemberContext> getMemberContextsOfClusterId(String clusterId) {
        return clusterIdToMemberContext.get(clusterId);
    }

    public Map<String, List<MemberContext>> getClusterIdToMemberContext() {
        return clusterIdToMemberContext;
    }
    
    public void setClusterIdToMemberContext(Map<String, List<MemberContext>> clusterIdToMemberContext) {
        this.clusterIdToMemberContext = clusterIdToMemberContext;
    }

    public Map<String, MemberContext> getMemberIdToContext() {
        return memberIdToContext;
    }

    public void setMemberIdToContext(Map<String, MemberContext> memberIdToContext) {
        this.memberIdToContext = memberIdToContext;
    }

    public void addClusterContext(ClusterContext ctxt) {
        clusterIdToContext.put(ctxt.getClusterId(), ctxt);
    }
    
    public ClusterContext getClusterContext(String clusterId) {
        return clusterIdToContext.get(clusterId);
    }
    
    public ClusterContext removeClusterContext(String clusterId) {
        return clusterIdToContext.remove(clusterId);
    }
    
    public Map<String, ClusterContext> getClusterIdToContext() {
        return clusterIdToContext;
    }

    public void setClusterIdToContext(Map<String, ClusterContext> clusterIdToContext) {
        this.clusterIdToContext = clusterIdToContext;
    }

	public ExecutorService getExecutor() {
		return executor;
	}

	public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	public Map<String, List<String>> getCartridgeTypeToPartitionIds() {
		return cartridgeTypeToPartitionIds;
	}

	public void setCartridgeTypeToPartitionIds(
			Map<String, List<String>> cartridgeTypeToPartitionIds) {
		this.cartridgeTypeToPartitionIds = cartridgeTypeToPartitionIds;
	}
	
	public void addToCartridgeTypeToPartitionIdMap(String cartridgeType, String partitionId) {
		List<String> list = this.cartridgeTypeToPartitionIds.get(cartridgeType);
		
		if(list == null) {
			list = new ArrayList<String>();
		}
		
		list.add(partitionId);
		this.cartridgeTypeToPartitionIds.put(cartridgeType, list);
	}
	
	public void removeFromCartridgeTypeToPartitionIds(String cartridgeType) {
		this.cartridgeTypeToPartitionIds.remove(cartridgeType);
	}

	public Map<String, KubernetesClusterContext> getKubClusterIdToKubClusterContext() {
		return kubClusterIdToKubClusterContext;
	}
	
	public KubernetesClusterContext getKubernetesClusterContext(String kubClusterId) {
		return kubClusterIdToKubClusterContext.get(kubClusterId);
	}
	
	public void addKubernetesClusterContext(KubernetesClusterContext ctxt) {
		this.kubClusterIdToKubClusterContext.put(ctxt.getKubernetesClusterId(), ctxt);
	}

	public void setKubClusterIdToKubClusterContext(
			Map<String, KubernetesClusterContext> kubClusterIdToKubClusterContext) {
		this.kubClusterIdToKubClusterContext = kubClusterIdToKubClusterContext;
	}
	
}