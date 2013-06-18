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

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.connector.TesterResponse;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.TesterMapRealm;

public class TestRealmBase {

    private static final String USER1 = "user1";
    private static final String PWD1 = "password1";
    private static final String ROLE1 = "role1";

    @Test
    public void testSingleRole() throws Exception {
        // Configure the users in the Realm
        TesterMapRealm mapRealm = new TesterMapRealm();
        mapRealm.addUser("user", ROLE1);

        // Configure the security constraints for the resourc
        SecurityConstraint constraint = new SecurityConstraint();
        constraint.addAuthRole(ROLE1);
        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/*");
        SecurityConstraint[] constraints =
                new SecurityConstraint[] {constraint};

        // Set up the mock request and response
        Request request = new Request();
        Response response = new TesterResponse();

        // Set up an authenticated user
        List<String> userRoles = new ArrayList<>();
        userRoles.add(ROLE1);
        GenericPrincipal gp = new GenericPrincipal(USER1, PWD1, userRoles);
        request.setUserPrincipal(gp);

        boolean result = mapRealm.hasResourcePermission(
                request, response, constraints, null);

        Assert.assertTrue(result);
    }
}
