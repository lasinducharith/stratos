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

package org.apache.stratos.manager.utils;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.manager.internal.DataHolder;
import org.wso2.carbon.user.api.Permission;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.mgt.UserMgtConstants;

public class UserRoleCreator {

    private transient static final Log log = LogFactory.getLog(UserRoleCreator.class);
    private static String role = "Tenant-User";

    /**
     * Creating a Tenant User Carbon Role at Server Start-up
     */
    public static void CreateTenantUserRole() {

        try {

            RealmService realmService = DataHolder.getRealmService();
            UserRealm realm = realmService.getBootstrapRealm();
            UserStoreManager manager = realm.getUserStoreManager();

            if (!manager.isExistingRole(role)) {
                if (log.isDebugEnabled()) {
                    log.debug("Creating subscriber role: " + role);
                }
                Permission[] TenantUserPermissions = new Permission[]{new Permission(CartridgeConstants.Permissions.VIEW, UserMgtConstants.EXECUTE_ACTION),
                                                                      new Permission(CartridgeConstants.Permissions.ADD_SUBSCRIPTION, UserMgtConstants.EXECUTE_ACTION)
                };

                String superTenantName = DataHolder.getRealmService().getBootstrapRealmConfiguration().getAdminUserName();
                String[] userList = new String[]{superTenantName};
                manager.addRole(role, userList, TenantUserPermissions);
            }

        } catch (UserStoreException e) {
            log.error("Error while creating the role: " + role + " - " +
                      e.getMessage());
        }

    }
}
