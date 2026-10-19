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
package org.apache.jasper.util;

import org.junit.Assert;
import org.junit.Test;


public class TestUniqueAttributesImpl {

    private static UniqueAttributesImpl createAttributes() {
        UniqueAttributesImpl attrs = new UniqueAttributesImpl();
        attrs.addAttribute("", "a", "a", "CDATA", "1");
        attrs.addAttribute("", "b", "b", "CDATA", "2");
        return attrs;
    }


    /*
     * Renaming an attribute to a new name must register that name, so a
     * later attribute of the same name is still detected as a duplicate.
     * The method used to remove the old name without adding the new one,
     * leaving the tracking set behind the attributes.
     */
    @Test
    public void testSetQNameRegistersNewName() throws Exception {
        UniqueAttributesImpl attrs = createAttributes();

        attrs.setQName(1, "c");
        Assert.assertEquals("c", attrs.getQName(1));

        try {
            attrs.addAttribute("", "c", "c", "CDATA", "3");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }


    /*
     * Renaming an attribute to the name of another attribute must be
     * rejected as a duplicate rather than renaming onto it.
     */
    @Test
    public void testSetQNameToExistingNameRejected() throws Exception {
        UniqueAttributesImpl attrs = createAttributes();

        try {
            attrs.setQName(1, "a");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        // The rejected rename left the attribute unchanged
        Assert.assertEquals("b", attrs.getQName(1));
    }


    /*
     * Renaming an attribute to the name it already has must not unregister
     * the name from duplicate detection.
     */
    @Test
    public void testSetQNameToSameName() throws Exception {
        UniqueAttributesImpl attrs = createAttributes();

        attrs.setQName(0, "a");
        Assert.assertEquals("a", attrs.getQName(0));

        try {
            attrs.addAttribute("", "a", "a", "CDATA", "3");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }


    /*
     * Replacing an attribute with a duplicate name must be rejected and must
     * keep the tracking set in step with the attributes, so the replaced
     * attribute's name is still detected as a duplicate afterwards.
     */
    @Test
    public void testSetAttributeToExistingNameRejected() throws Exception {
        UniqueAttributesImpl attrs = createAttributes();

        try {
            attrs.setAttribute(1, "", "a", "a", "CDATA", "3");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        // The rejected replacement left the attribute unchanged
        Assert.assertEquals("b", attrs.getQName(1));

        // The unchanged attribute's name must still be tracked
        try {
            attrs.addAttribute("", "b", "b", "CDATA", "4");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
