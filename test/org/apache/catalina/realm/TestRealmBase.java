/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.realm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.connector.TesterResponse;
import org.apache.catalina.core.TesterContext;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.TesterMapRealm;

public class TestRealmBase {

    private static final String USER1 = "user1";
    private static final String PWD1 = "password1";
    private static final String ROLE1 = "role1";

    @Test
    public void testUserWithSingleRole() throws IOException {
        List<String> userRoles = new ArrayList<>();
        List<String> constraintRoles = new ArrayList<>();
        List<String> applicationRoles = new ArrayList<>();

        // Configure this test
        userRoles.add(ROLE1);
        constraintRoles.add(ROLE1);
        applicationRoles.add(ROLE1);

        doRoleTest(userRoles, constraintRoles, applicationRoles, true);
    }


    @Test
    public void testUserWithNoRoles() throws IOException {
        List<String> userRoles = new ArrayList<>();
        List<String> constraintRoles = new ArrayList<>();
        List<String> applicationRoles = new ArrayList<>();

        // Configure this test
        constraintRoles.add(ROLE1);
        applicationRoles.add(ROLE1);

        doRoleTest(userRoles, constraintRoles, applicationRoles, false);
    }


    @Test
    public void testUserWithSingleRoleAndAllRoles() throws IOException {
        List<String> userRoles = new ArrayList<>();
        List<String> constraintRoles = new ArrayList<>();
        List<String> applicationRoles = new ArrayList<>();

        // Configure this test
        userRoles.add(ROLE1);
        applicationRoles.add(ROLE1);
        constraintRoles.add(SecurityConstraint.ROLE_ALL_ROLES);

        doRoleTest(userRoles, constraintRoles, applicationRoles, true);
    }


    @Test
    public void testUserWithoutNoRolesAndAllRoles() throws IOException {
        List<String> userRoles = new ArrayList<>();
        List<String> constraintRoles = new ArrayList<>();
        List<String> applicationRoles = new ArrayList<>();

        // Configure this test
        constraintRoles.add(SecurityConstraint.ROLE_ALL_ROLES);
        applicationRoles.add(ROLE1);

        doRoleTest(userRoles, constraintRoles, applicationRoles, false);
    }


    @Test
    public void testAllRolesWithNoAppRole() throws IOException {
        List<String> userRoles = new ArrayList<>();
        List<String> constraintRoles = new ArrayList<>();
        List<String> applicationRoles = new ArrayList<>();

        // Configure this test
        userRoles.add(ROLE1);
        constraintRoles.add(SecurityConstraint.ROLE_ALL_ROLES);

        doRoleTest(userRoles, constraintRoles, applicationRoles, false);
    }


    private void doRoleTest(List<String> userRoles,
            List<String> constraintRoles, List<String> applicationRoles,
            boolean expected) throws IOException {

        // Configure the users in the Realm
        TesterMapRealm mapRealm = new TesterMapRealm();
        for (String userRole : userRoles) {
            mapRealm.addUser(USER1, userRole);
        }

        // Configure the security constraints for the resource
        SecurityConstraint constraint = new SecurityConstraint();
        for (String constraintRole : constraintRoles) {
            constraint.addAuthRole(constraintRole);
        }
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/*");
        SecurityConstraint[] constraints =
                new SecurityConstraint[] {constraint};

        // Set up the mock request and response
        Request request = new Request();
        Response response = new TesterResponse();
        Context context = new TesterContext();
        for (String applicationRole : applicationRoles) {
            context.addSecurityRole(applicationRole);
        }
        request.setContext(context);

        // Set up an authenticated user
        GenericPrincipal gp = new GenericPrincipal(USER1, PWD1, userRoles);
        request.setUserPrincipal(gp);

        // Check if user meets constaints
        boolean result = mapRealm.hasResourcePermission(
                request, response, constraints, null);

        Assert.assertEquals(Boolean.valueOf(expected), Boolean.valueOf(result));
    }
}
