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
package org.apache.stratos.messaging.util;

public class Constants {
	/* Message broker topic names */
	public static final String TOPOLOGY_TOPIC = "topology";
	public static final String HEALTH_STAT_TOPIC = "summarized-health-stats";
    public static final String INSTANCE_STATUS_TOPIC = "instance-status";
    public static final String ARTIFACT_SYNCHRONIZATION_TOPIC = "artifact-synchronization";

    public static final String TENANT_RANGE_DELIMITER = "-";
    public static final String EVENT_CLASS_NAME = "event-class-name";
}
