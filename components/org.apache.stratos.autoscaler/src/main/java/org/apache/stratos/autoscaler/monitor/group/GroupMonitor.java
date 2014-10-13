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
package org.apache.stratos.autoscaler.monitor.group;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.exception.DependencyBuilderException;
import org.apache.stratos.autoscaler.exception.TopologyInConsistentException;
import org.apache.stratos.autoscaler.grouping.dependency.context.ApplicationContext;
import org.apache.stratos.autoscaler.monitor.EventHandler;
import org.apache.stratos.autoscaler.monitor.Monitor;
import org.apache.stratos.autoscaler.monitor.MonitorStatusEventBuilder;
import org.apache.stratos.autoscaler.monitor.events.ClusterStatusEvent;
import org.apache.stratos.autoscaler.monitor.events.GroupStatusEvent;
import org.apache.stratos.autoscaler.monitor.events.MonitorStatusEvent;
import org.apache.stratos.autoscaler.status.checker.StatusChecker;
import org.apache.stratos.messaging.domain.topology.Group;
import org.apache.stratos.messaging.domain.topology.Status;
import org.apache.stratos.messaging.event.application.status.StatusEvent;

/**
 * This is GroupMonitor to monitor the group which consists of
 * groups and clusters
 */
public class GroupMonitor extends Monitor implements EventHandler {
    private static final Log log = LogFactory.getLog(GroupMonitor.class);

    //Parent monitor of this monitor
    private Monitor parent;

    /**
     * Constructor of GroupMonitor
     * @param group Takes the group from the Topology
     * @throws DependencyBuilderException throws when couldn't build the Topology
     * @throws TopologyInConsistentException throws when topology is inconsistent
     */
    public GroupMonitor(Group group) throws DependencyBuilderException,
                                            TopologyInConsistentException {
        super(group);
        this.id = group.getAlias();
        startDependency();
        this.status = Status.Created;

    }

    /**
     * Will set the status of the monitor based on Topology Group status/child status like scaling
     * @param status
     */
    public void setStatus(Status status) {
        log.info(String.format("[Monitor] %s is notifying the parent" +
                "on its state change from %s to %s", id, this.status, status));
        this.status = status;
        //notifying the parent
        MonitorStatusEventBuilder.handleGroupStatusEvent(this.parent, this.status, this.id);
    }

    @Override
    public void onEvent(MonitorStatusEvent statusEvent) {
        monitor(statusEvent);
    }

    @Override
    protected void monitor(MonitorStatusEvent statusEvent) {
        String id = statusEvent.getId();
        ApplicationContext context = this.dependencyTree.
                findApplicationContextWithId(id);
        if(context.getStatusLifeCycle().isEmpty()) {
            try {
                //if life cycle is empty, need to start the monitor
                boolean startDep = startDependency(statusEvent.getId());
                if(log.isDebugEnabled()) {
                    log.debug("started a child: " + startDep + " by the group/cluster: " + id);

                }
                //updating the life cycle and current status
                context.setStatus(statusEvent.getStatus());
                context.addStatusToLIfeCycle(statusEvent.getStatus());
                if(!startDep) {
                    //Checking in the children whether all are active,
                    // since no dependency found to be started.
                    StatusChecker.getInstance().onChildStatusChange(id, this.id, this.appId);
                }
            } catch (TopologyInConsistentException e) {
                //TODO revert the siblings and notify parent, change a flag for reverting/un-subscription
                log.error(e);
            }
        } else {
            //TODO act based on life cycle events
        }

    }

    public Monitor getParent() {
        return parent;
    }

    public void setParent(Monitor parent) {
        this.parent = parent;
        this.appId = parent.getAppId();
    }

}
