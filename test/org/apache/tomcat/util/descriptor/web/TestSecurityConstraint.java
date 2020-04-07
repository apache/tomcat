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
package org.apache.tomcat.util.descriptor.web;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.HttpConstraintElement;
import javax.servlet.HttpMethodConstraintElement;
import javax.servlet.ServletSecurityElement;
import javax.servlet.annotation.ServletSecurity;
import javax.servlet.annotation.ServletSecurity.EmptyRoleSemantic;

import org.junit.Assert;
import org.junit.Test;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class TestSecurityConstraint {

    private static final String URL_PATTERN = "/test";
    private static final String ROLE1 = "R1";

    private static final Log DUMMY_LOG = LogFactory.getLog("DUMMY");

    private static final SecurityConstraint GET_ONLY;
    private static final SecurityConstraint POST_ONLY;

    private static final SecurityConstraint GET_OMIT;
    private static final SecurityConstraint POST_OMIT;

    static {
        // Configure the constraints to use in the tests
        GET_ONLY = new SecurityConstraint();
        GET_ONLY.addAuthRole(ROLE1);
        SecurityCollection scGetOnly = new SecurityCollection();
        scGetOnly.addMethod("GET");
        scGetOnly.addPatternDecoded(URL_PATTERN);
        scGetOnly.setName("GET-ONLY");
        GET_ONLY.addCollection(scGetOnly);

        POST_ONLY = new SecurityConstraint();
        POST_ONLY.addAuthRole(ROLE1);
        SecurityCollection scPostOnly = new SecurityCollection();
        scPostOnly.addMethod("POST");
        scPostOnly.addPatternDecoded(URL_PATTERN);
        scPostOnly.setName("POST_ONLY");
        POST_ONLY.addCollection(scPostOnly);

        GET_OMIT = new SecurityConstraint();
        GET_OMIT.addAuthRole(ROLE1);
        SecurityCollection scGetOmit = new SecurityCollection();
        scGetOmit.addOmittedMethod("GET");
        scGetOmit.addPatternDecoded(URL_PATTERN);
        scGetOmit.setName("GET_OMIT");
        GET_OMIT.addCollection(scGetOmit);

        POST_OMIT = new SecurityConstraint();
        POST_OMIT.addAuthRole(ROLE1);
        SecurityCollection scPostOmit = new SecurityCollection();
        scPostOmit.addOmittedMethod("POST");
        scPostOmit.addPatternDecoded(URL_PATTERN);
        scPostOmit.setName("POST_OMIT");
        POST_OMIT.addCollection(scPostOmit);
    }

    /**
     * Uses the examples in SRV.13.4 as the basis for these tests
     */
    @Test
    public void testCreateConstraints() {

        ServletSecurityElement element;
        SecurityConstraint[] result;
        Set<HttpMethodConstraintElement> hmces = new HashSet<>();

        // Example 13-1
        // @ServletSecurity
        element = new ServletSecurityElement();
        result = SecurityConstraint.createConstraints(element, URL_PATTERN);

        Assert.assertEquals(0, result.length);

        // Example 13-2
        // @ServletSecurity(
        //     @HttpConstraint(
        //         transportGuarantee = TransportGuarantee.CONFIDENTIAL))
        element = new ServletSecurityElement(
                new HttpConstraintElement(
                        ServletSecurity.TransportGuarantee.CONFIDENTIAL));
        result = SecurityConstraint.createConstraints(element, URL_PATTERN);

        Assert.assertEquals(1, result.length);
        Assert.assertFalse(result[0].getAuthConstraint());
        Assert.assertTrue(result[0].findCollections()[0].findPattern(URL_PATTERN));
        Assert.assertEquals(0, result[0].findCollections()[0].findMethods().length);
        Assert.assertEquals(ServletSecurity.TransportGuarantee.CONFIDENTIAL.name(),
                result[0].getUserConstraint());

        // Example 13-3
        // @ServletSecurity(@HttpConstraint(EmptyRoleSemantic.DENY))
        element = new ServletSecurityElement(
                new HttpConstraintElement(EmptyRoleSemantic.DENY));
        result = SecurityConstraint.createConstraints(element, URL_PATTERN);

        Assert.assertEquals(1, result.length);
        Assert.assertTrue(result[0].getAuthConstraint());
        Assert.assertTrue(result[0].findCollections()[0].findPattern(URL_PATTERN));
        Assert.assertEquals(0, result[0].findCollections()[0].findMethods().length);
        Assert.assertEquals(ServletSecurity.TransportGuarantee.NONE.name(),
                result[0].getUserConstraint());

        // Example 13-4
        // @ServletSecurity(@HttpConstraint(rolesAllowed = "R1"))
        element = new ServletSecurityElement(new HttpConstraintElement(
                ServletSecurity.TransportGuarantee.NONE, ROLE1));
        result = SecurityConstraint.createConstraints(element, URL_PATTERN);

        Assert.assertEquals(1, result.length);
        Assert.assertTrue(result[0].getAuthConstraint());
        Assert.assertEquals(1, result[0].findAuthRoles().length);
        Assert.assertTrue(result[0].findAuthRole(ROLE1));
        Assert.assertTrue(result[0].findCollections()[0].findPattern(URL_PATTERN));
        Assert.assertEquals(0, result[0].findCollections()[0].findMethods().length);
        Assert.assertEquals(ServletSecurity.TransportGuarantee.NONE.name(),
                result[0].getUserConstraint());

        // Example 13-5
        // @ServletSecurity((httpMethodConstraints = {
        //     @HttpMethodConstraint(value = "GET", rolesAllowed = "R1"),
        //     @HttpMethodConstraint(value = "POST", rolesAllowed = "R1",
        //     transportGuarantee = TransportGuarantee.CONFIDENTIAL)
        // })
        hmces.clear();
        hmces.add(new HttpMethodConstraintElement("GET",
                new HttpConstraintElement(
                        ServletSecurity.TransportGuarantee.NONE, ROLE1)));
        hmces.add(new HttpMethodConstraintElement("POST",
                new HttpConstraintElement(
                        ServletSecurity.TransportGuarantee.CONFIDENTIAL,
                        ROLE1)));
        element = new ServletSecurityElement(hmces);
        result = SecurityConstraint.createConstraints(element, URL_PATTERN);

        Assert.assertEquals(2, result.length);
        for (int i = 0; i < 2; i++) {
            Assert.assertTrue(result[i].getAuthConstraint());
            Assert.assertEquals(1, result[i].findAuthRoles().length);
            Assert.assertTrue(result[i].findAuthRole(ROLE1));
            Assert.assertTrue(result[i].findCollections()[0].findPattern(URL_PATTERN));
            Assert.assertEquals(1, result[i].findCollections()[0].findMethods().length);
            String method = result[i].findCollections()[0].findMethods()[0];
            if ("GET".equals(method)) {
                Assert.assertEquals(ServletSecurity.TransportGuarantee.NONE.name(),
                        result[i].getUserConstraint());
            } else if ("POST".equals(method)) {
                Assert.assertEquals(ServletSecurity.TransportGuarantee.CONFIDENTIAL.name(),
                        result[i].getUserConstraint());
            } else {
                Assert.fail("Unexpected method :[" + method + "]");
            }
        }

        // Example 13-6
        // @ServletSecurity(value = @HttpConstraint(rolesAllowed = "R1"),
        //     httpMethodConstraints = @HttpMethodConstraint("GET"))
        hmces.clear();
        hmces.add(new HttpMethodConstraintElement("GET"));
        element = new ServletSecurityElement(
                new HttpConstraintElement(
                        ServletSecurity.TransportGuarantee.NONE,
                        ROLE1),
                hmces);
        result = SecurityConstraint.createConstraints(element, URL_PATTERN);

        Assert.assertEquals(2, result.length);
        for (int i = 0; i < 2; i++) {
            Assert.assertTrue(result[i].findCollections()[0].findPattern(URL_PATTERN));
            if (result[i].findCollections()[0].findMethods().length == 1) {
                Assert.assertEquals("GET",
                        result[i].findCollections()[0].findMethods()[0]);
                Assert.assertFalse(result[i].getAuthConstraint());
            } else if (result[i].findCollections()[0].findOmittedMethods().length == 1) {
                Assert.assertEquals("GET",
                        result[i].findCollections()[0].findOmittedMethods()[0]);
                Assert.assertTrue(result[i].getAuthConstraint());
                Assert.assertEquals(1, result[i].findAuthRoles().length);
                Assert.assertEquals(ROLE1, result[i].findAuthRoles()[0]);
            } else {
                Assert.fail("Unexpected number of methods defined");
            }
            Assert.assertEquals(ServletSecurity.TransportGuarantee.NONE.name(),
                    result[i].getUserConstraint());
        }

        // Example 13-7
        // @ServletSecurity(value = @HttpConstraint(rolesAllowed = "R1"),
        //     httpMethodConstraints = @HttpMethodConstraint(value="TRACE",
        //         emptyRoleSemantic = EmptyRoleSemantic.DENY))
        hmces.clear();
        hmces.add(new HttpMethodConstraintElement("TRACE",
                new HttpConstraintElement(EmptyRoleSemantic.DENY)));
        element = new ServletSecurityElement(
                new HttpConstraintElement(
                        ServletSecurity.TransportGuarantee.NONE,
                        ROLE1),
                hmces);
        result = SecurityConstraint.createConstraints(element, URL_PATTERN);

        Assert.assertEquals(2, result.length);
        for (int i = 0; i < 2; i++) {
            Assert.assertTrue(result[i].findCollections()[0].findPattern(URL_PATTERN));
            if (result[i].findCollections()[0].findMethods().length == 1) {
                Assert.assertEquals("TRACE",
                        result[i].findCollections()[0].findMethods()[0]);
                Assert.assertTrue(result[i].getAuthConstraint());
                Assert.assertEquals(0, result[i].findAuthRoles().length);
            } else if (result[i].findCollections()[0].findOmittedMethods().length == 1) {
                Assert.assertEquals("TRACE",
                        result[i].findCollections()[0].findOmittedMethods()[0]);
                Assert.assertTrue(result[i].getAuthConstraint());
                Assert.assertEquals(1, result[i].findAuthRoles().length);
                Assert.assertEquals(ROLE1, result[i].findAuthRoles()[0]);
            } else {
                Assert.fail("Unexpected number of methods defined");
            }
            Assert.assertEquals(ServletSecurity.TransportGuarantee.NONE.name(),
                    result[i].getUserConstraint());
        }

        // Example 13-8 is the same as 13-4
        // Example 13-9 is the same as 13-7
    }


    @Test
    public void testFindUncoveredHttpMethods01() {
        // No new constraints if denyUncoveredHttpMethods is false
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {GET_ONLY}, false, DUMMY_LOG);
        Assert.assertEquals(0, result.length);
    }


    @Test
    public void testFindUncoveredHttpMethods02() {
        // No new constraints if denyUncoveredHttpMethods is false
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {GET_OMIT}, false, DUMMY_LOG);
        Assert.assertEquals(0, result.length);
    }


    @Test
    public void testFindUncoveredHttpMethods03() {
        // No new constraints if denyUncoveredHttpMethods is false
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {POST_ONLY}, false, DUMMY_LOG);
        Assert.assertEquals(0, result.length);
    }


    @Test
    public void testFindUncoveredHttpMethods04() {
        // No new constraints if denyUncoveredHttpMethods is false
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {POST_OMIT}, false, DUMMY_LOG);
        Assert.assertEquals(0, result.length);
    }


    @Test
    public void testFindUncoveredHttpMethods05() {
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {GET_ONLY}, true, DUMMY_LOG);
        Assert.assertEquals(1, result.length);
        // Should be a deny constraint
        Assert.assertTrue(result[0].getAuthConstraint());
        // Should have a single collection
        Assert.assertEquals(1, result[0].findCollections().length);
        SecurityCollection sc = result[0].findCollections()[0];
        // Should list GET as an omitted method
        Assert.assertEquals(0, sc.findMethods().length);
        Assert.assertEquals(1, sc.findOmittedMethods().length);
        Assert.assertEquals("GET", sc.findOmittedMethods()[0]);
    }


    @Test
    public void testFindUncoveredHttpMethods06() {
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {POST_ONLY}, true, DUMMY_LOG);
        Assert.assertEquals(1, result.length);
        // Should be a deny constraint
        Assert.assertTrue(result[0].getAuthConstraint());
        // Should have a single collection
        Assert.assertEquals(1, result[0].findCollections().length);
        SecurityCollection sc = result[0].findCollections()[0];
        // Should list POST as an omitted method
        Assert.assertEquals(0, sc.findMethods().length);
        Assert.assertEquals(1, sc.findOmittedMethods().length);
        Assert.assertEquals("POST", sc.findOmittedMethods()[0]);
    }


    @Test
    public void testFindUncoveredHttpMethods07() {
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {GET_OMIT}, true, DUMMY_LOG);
        Assert.assertEquals(1, result.length);
        // Should be a deny constraint
        Assert.assertTrue(result[0].getAuthConstraint());
        // Should have a single collection
        Assert.assertEquals(1, result[0].findCollections().length);
        SecurityCollection sc = result[0].findCollections()[0];
        // Should list GET as an method
        Assert.assertEquals(0, sc.findOmittedMethods().length);
        Assert.assertEquals(1, sc.findMethods().length);
        Assert.assertEquals("GET", sc.findMethods()[0]);
    }


    @Test
    public void testFindUncoveredHttpMethods08() {
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {POST_OMIT}, true, DUMMY_LOG);
        Assert.assertEquals(1, result.length);
        // Should be a deny constraint
        Assert.assertTrue(result[0].getAuthConstraint());
        // Should have a single collection
        Assert.assertEquals(1, result[0].findCollections().length);
        SecurityCollection sc = result[0].findCollections()[0];
        // Should list POST as an method
        Assert.assertEquals(0, sc.findOmittedMethods().length);
        Assert.assertEquals(1, sc.findMethods().length);
        Assert.assertEquals("POST", sc.findMethods()[0]);
    }


    @Test
    public void testFindUncoveredHttpMethods09() {
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {GET_ONLY, GET_OMIT}, true,
                        DUMMY_LOG);
        Assert.assertEquals(0, result.length);
    }


    @Test
    public void testFindUncoveredHttpMethods10() {
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {POST_ONLY, POST_OMIT}, true,
                        DUMMY_LOG);
        Assert.assertEquals(0, result.length);
    }


    @Test
    public void testFindUncoveredHttpMethods11() {
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {GET_ONLY, POST_ONLY}, true,
                        DUMMY_LOG);
        Assert.assertEquals(1, result.length);
        // Should be a deny constraint
        Assert.assertTrue(result[0].getAuthConstraint());
        // Should have a single collection
        Assert.assertEquals(1, result[0].findCollections().length);
        SecurityCollection sc = result[0].findCollections()[0];
        // Should list GET and POST as omitted methods
        Assert.assertEquals(0, sc.findMethods().length);
        Assert.assertEquals(2, sc.findOmittedMethods().length);
        HashSet<String> omittedMethods = new HashSet<>();
        omittedMethods.addAll(Arrays.asList(sc.findOmittedMethods()));
        Assert.assertTrue(omittedMethods.remove("GET"));
        Assert.assertTrue(omittedMethods.remove("POST"));
    }


    @Test
    public void testFindUncoveredHttpMethods12() {
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {GET_OMIT, POST_OMIT}, true,
                        DUMMY_LOG);
        Assert.assertEquals(0, result.length);
    }


    @Test
    public void testFindUncoveredHttpMethods13() {
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {GET_ONLY, POST_OMIT}, true,
                        DUMMY_LOG);
        Assert.assertEquals(1, result.length);
        // Should be a deny constraint
        Assert.assertTrue(result[0].getAuthConstraint());
        // Should have a single collection
        Assert.assertEquals(1, result[0].findCollections().length);
        SecurityCollection sc = result[0].findCollections()[0];
        // Should list POST as a method
        Assert.assertEquals(1, sc.findMethods().length);
        Assert.assertEquals(0, sc.findOmittedMethods().length);
        Assert.assertEquals("POST", sc.findMethods()[0]);
    }


    @Test
    public void testFindUncoveredHttpMethods14() {
        SecurityConstraint[] result =
                SecurityConstraint.findUncoveredHttpMethods(
                        new SecurityConstraint[] {GET_OMIT, POST_ONLY}, true,
                        DUMMY_LOG);
        Assert.assertEquals(1, result.length);
        // Should be a deny constraint
        Assert.assertTrue(result[0].getAuthConstraint());
        // Should have a single collection
        Assert.assertEquals(1, result[0].findCollections().length);
        SecurityCollection sc = result[0].findCollections()[0];
        // Should list GET as a method
        Assert.assertEquals(1, sc.findMethods().length);
        Assert.assertEquals(0, sc.findOmittedMethods().length);
        Assert.assertEquals("GET", sc.findMethods()[0]);
    }
}
