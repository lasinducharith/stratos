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

package org.apache.stratos.manager.user.mgt;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.manager.user.mgt.beans.UserInfoBean;
import org.apache.stratos.manager.user.mgt.exception.UserManagementException;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.UserCoreConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This Class provides the operations related to adding/updating/deleting and listing users using
 * the carbon UserStoreManager in the particular tenant space
 */
public class StratosUserManager {

    private transient static final Log log = LogFactory.getLog(StratosUserManager.class);
    private static final String INTERNAL_EVERYONE_ROLE = "Internal/everyone";
    private static final String GET_ALL_USERS_WILD_CARD = "*";

    /**
     * Add a user to the user-store of the particular tenant
     *
     * @param userStoreManager  UserStoreManager
     * @param userInfoBean      UserInfoBean
     * @throws UserManagementException
     */
    public void addUser(UserStoreManager userStoreManager, UserInfoBean userInfoBean)
            throws UserManagementException {

        try {

            if (!userStoreManager.isExistingUser(userInfoBean.getUserName())) {
                if (log.isDebugEnabled()) {
                    log.debug("Creating new User: " + userInfoBean.getUserName());
                }

                String[] roles = new String[1];
                roles[0] = userInfoBean.getRole();
                Map<String, String> claims = new HashMap<String, String>();

                //set firstname, lastname and email as user claims
                claims.put(UserCoreConstants.ClaimTypeURIs.EMAIL_ADDRESS, userInfoBean.getEmail());
                claims.put(UserCoreConstants.ClaimTypeURIs.GIVEN_NAME, userInfoBean.getFirstName());
                claims.put(UserCoreConstants.ClaimTypeURIs.SURNAME, userInfoBean.getLastName());
                userStoreManager.addUser(userInfoBean.getUserName(), userInfoBean.getCredential(), roles, claims, userInfoBean.getProfileName());

            }

        } catch (UserStoreException e) {
            String msg = "Error in adding the user : "+ userInfoBean.getUserName();
            log.error(msg, e);
            throw new UserManagementException(e.getMessage(), e);
        }
    }

    /**
     * Delete the user with the given username in the relevant tenant space
     *
     * @param userStoreManager  UserStoreManager
     * @param userName          UserName
     * @throws UserManagementException
     */
    public void deleteUser(UserStoreManager userStoreManager, String userName)
            throws UserManagementException {

        try {
            userStoreManager.deleteUser(userName);
        } catch (UserStoreException e) {
            String msg = "Error in deleting the user : "+ userName;
            log.error(msg, e);
            throw new UserManagementException(e.getMessage(), e);
        }
    }


    /**
     * Updates the user info given the new UserInfoBean
     *
     * @param userStoreManager  UserStoreManager
     * @param userInfoBean      UserInfoBean
     * @throws UserManagementException
     */
    public void updateUser(UserStoreManager userStoreManager, UserInfoBean userInfoBean)
            throws UserManagementException {

        try {
            if (userStoreManager.isExistingUser(userInfoBean.getUserName())) {
                if (log.isDebugEnabled()) {
                    log.debug("Updating User: " + userInfoBean.getUserName());
                }

                String[] newRoles = new String[1];
                newRoles[0] = userInfoBean.getRole();

                userStoreManager.updateRoleListOfUser(userInfoBean.getUserName(), getRefinedListOfRolesOfUser(userStoreManager, userInfoBean.getUserName()), newRoles);
                userStoreManager.setUserClaimValue(userInfoBean.getUserName(), UserCoreConstants.ClaimTypeURIs.EMAIL_ADDRESS, userInfoBean.getEmail(), userInfoBean.getProfileName());
                userStoreManager.setUserClaimValue(userInfoBean.getUserName(), UserCoreConstants.ClaimTypeURIs.GIVEN_NAME, userInfoBean.getFirstName(), userInfoBean.getProfileName());
                userStoreManager.setUserClaimValue(userInfoBean.getUserName(), UserCoreConstants.ClaimTypeURIs.SURNAME, userInfoBean.getLastName(), userInfoBean.getProfileName());
                userStoreManager.updateCredentialByAdmin(userInfoBean.getUserName(), userInfoBean.getCredential());

            }
        } catch (UserStoreException e) {
            String msg = "Error in updating the user : "+ userInfoBean.getUserName();
            log.error(msg, e);
            throw new UserManagementException(e.getMessage(), e);
        }

    }

    /**
     * Get a List of usernames and associated Roles as a UserInfoBean
     *
     * @param userStoreManager UserStoreManager
     * @return List<UserInfoBean>
     * @throws UserManagementException
     */
    public List<UserInfoBean> getAllUsers(UserStoreManager userStoreManager)
            throws UserManagementException {

        String[] users;
        List<UserInfoBean> userList = new ArrayList<UserInfoBean>();

        try {
            users = userStoreManager.listUsers(GET_ALL_USERS_WILD_CARD, -1);

            //Iterate through the list of users and retrieve their roles
            for (String user : users) {
                UserInfoBean userInfoBean = new UserInfoBean();
                userInfoBean.setUserName(user);
                userInfoBean.setRole(getRefinedListOfRolesOfUser(userStoreManager, user)[0]);
                userList.add(userInfoBean);
            }

        } catch (UserStoreException e) {
            String msg = "Error in listing users";
            log.error(msg, e);
            throw new UserManagementException(e.getMessage(), e);
        }

        return userList;
    }

    /**
     * Get the List of userRoles except the everyone role
     *
     * @param userStoreManager UserStoreManager
     * @param username         Username of the user
     * @return String[]
     * @throws UserManagementException
     */
    private String[] getRefinedListOfRolesOfUser(UserStoreManager userStoreManager, String username)
            throws UserManagementException {

        ArrayList<String> rolesWithoutEveryoneRole = new ArrayList<String>();

        try {

            String[] allUserRoles = userStoreManager.getRoleListOfUser(username);

            for (String role : allUserRoles) {
                if (!role.equals(INTERNAL_EVERYONE_ROLE)) {
                    rolesWithoutEveryoneRole.add(role);
                }
            }

        } catch (UserStoreException e) {
            log.error(e.getMessage(), e);
            throw new UserManagementException(e.getMessage(), e);
        }

        String[] rolesWithoutEveryoneRoleArray = new String[rolesWithoutEveryoneRole.size()];
        return rolesWithoutEveryoneRole.toArray(rolesWithoutEveryoneRoleArray);
    }

}
