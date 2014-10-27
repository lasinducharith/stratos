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

package org.apache.stratos.messaging.event.tenant;

import java.io.Serializable;

/**
 * This event is fired when a tenant updates its tenant domain.
 */
public class TenantUpdatedEvent extends TenantEvent implements Serializable {
    private static final long serialVersionUID = -3507199180268215748L;

    private String tenantDomain;

    public TenantUpdatedEvent(int tenantId, String tenantDomain) {
        super(tenantId);
        this.tenantDomain = tenantDomain;
    }

    public String getTenantDomain() {
        return tenantDomain;
    }
}
