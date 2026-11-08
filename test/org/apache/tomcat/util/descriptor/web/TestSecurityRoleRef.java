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

import org.junit.Assert;
import org.junit.Test;

public class TestSecurityRoleRef {

    @Test
    public void testEqualsWithNull() {
        // Contract: equals(null) must return false, not throw
        SecurityRoleRef ref = new SecurityRoleRef();
        ref.setName("roleName");
        ref.setLink("roleLink");
        Assert.assertFalse(ref.equals(null));
    }

    @Test
    public void testEquals() {
        SecurityRoleRef ref1 = new SecurityRoleRef();
        ref1.setName("roleName");
        ref1.setLink("roleLink");

        SecurityRoleRef ref2 = new SecurityRoleRef();
        ref2.setName("roleName");
        ref2.setLink("roleLink");

        Assert.assertEquals(ref1, ref1);
        Assert.assertEquals(ref1, ref2);
        Assert.assertEquals(ref1.hashCode(), ref2.hashCode());

        ref2.setLink("otherLink");
        Assert.assertNotEquals(ref1, ref2);

        ref2.setLink("roleLink");
        ref2.setName("otherName");
        Assert.assertNotEquals(ref1, ref2);

        Assert.assertNotEquals(ref1, new Object());
    }

    @Test
    public void testEqualsNullFields() {
        SecurityRoleRef ref1 = new SecurityRoleRef();
        SecurityRoleRef ref2 = new SecurityRoleRef();

        Assert.assertEquals(ref1, ref2);
        Assert.assertEquals(ref1.hashCode(), ref2.hashCode());

        ref2.setName("roleName");
        Assert.assertNotEquals(ref1, ref2);
    }
}
