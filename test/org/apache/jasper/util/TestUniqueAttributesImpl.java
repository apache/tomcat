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

import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;


public class TestUniqueAttributesImpl {

    private static UniqueAttributesImpl createAttributes() {
        UniqueAttributesImpl attrs = new UniqueAttributesImpl();
        attrs.addAttribute("", "a", "a", "CDATA", "1");
        attrs.addAttribute("", "b", "b", "CDATA", "2");
        return attrs;
    }

    private static Attributes attributes(String... nameValuePairs) {
        AttributesImpl atts = new AttributesImpl();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            atts.addAttribute("", nameValuePairs[i], nameValuePairs[i], "CDATA",
                    nameValuePairs[i + 1]);
        }
        return atts;
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


    /*
     * Replacing the attributes must reset the duplicate tracking, so names
     * from the replaced attributes are no longer reported as duplicates and
     * names of the new attributes are.
     */
    @Test
    public void testSetAttributesResetsTracking() throws Exception {
        UniqueAttributesImpl attrs = createAttributes();

        attrs.setAttributes(attributes("x", "3", "y", "4"));

        Assert.assertEquals(2, attrs.getLength());
        Assert.assertEquals("x", attrs.getQName(0));

        // The replaced attributes' names must no longer be tracked
        attrs.addAttribute("", "a", "a", "CDATA", "5");

        // The new attributes' names must be tracked
        try {
            attrs.addAttribute("", "x", "x", "CDATA", "6");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }


    /*
     * The incoming set must be validated against itself, not against the
     * attributes being replaced, so a name held by a replaced attribute may
     * be reused with any value.
     */
    @Test
    public void testSetAttributesReplacesHeldName() throws Exception {
        UniqueAttributesImpl attrs = createAttributes();

        attrs.setAttributes(attributes("a", "9"));

        Assert.assertEquals(1, attrs.getLength());
        Assert.assertEquals("9", attrs.getValue("a"));
    }


    /*
     * Replacing the attributes with a set containing a rejected duplicate
     * must leave both the attributes and the tracking untouched.
     */
    @Test
    public void testSetAttributesRejectedDuplicateLeavesStateUntouched() throws Exception {
        UniqueAttributesImpl attrs = createAttributes();

        try {
            attrs.setAttributes(attributes("m", "3", "m", "4"));
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        Assert.assertEquals(2, attrs.getLength());
        Assert.assertEquals("a", attrs.getQName(0));

        // The rejected set's names must not have been left behind in the
        // tracking either
        attrs.addAttribute("", "m", "m", "CDATA", "5");
        Assert.assertEquals(3, attrs.getLength());

        try {
            attrs.addAttribute("", "a", "a", "CDATA", "6");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }


    /*
     * On a page directive, repeated import attributes in the incoming set
     * must be merged into a single attribute.
     */
    @Test
    public void testSetAttributesMergesImports() throws Exception {
        UniqueAttributesImpl attrs = new UniqueAttributesImpl(true);

        attrs.setAttributes(attributes("import", "a", "import", "b",
                "contentType", "text/html"));

        Assert.assertEquals(2, attrs.getLength());
        Assert.assertEquals("a,b", attrs.getValue("import"));

        // A later import still merges
        attrs.addAttribute("", "import", "import", "CDATA", "c");
        Assert.assertEquals("a,b,c", attrs.getValue("import"));
    }


    /*
     * On a page directive, an attribute in the incoming set repeated with an
     * identical value must be tolerated without storing the duplicate, while
     * a repeated pageEncoding must be rejected even with an identical value.
     */
    @Test
    public void testSetAttributesPageDirectiveRules() throws Exception {
        UniqueAttributesImpl attrs = new UniqueAttributesImpl(true);

        attrs.setAttributes(attributes("foo", "1", "foo", "1"));
        Assert.assertEquals(1, attrs.getLength());

        try {
            attrs.setAttributes(attributes("pageEncoding", "UTF-8",
                    "pageEncoding", "UTF-8"));
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        // The rejected set left the previous attributes in place
        Assert.assertEquals(1, attrs.getLength());
        Assert.assertEquals("1", attrs.getValue("foo"));
    }


    /*
     * Names are tracked exactly, so a repeated, differently cased import is
     * not the mergeable page directive import attribute and must be rejected
     * like any other duplicate name rather than causing an internal error.
     */
    @Test
    public void testDifferentlyCasedImportRepeatRejected() throws Exception {
        UniqueAttributesImpl attrs = new UniqueAttributesImpl(true);

        attrs.addAttribute("", "Import", "Import", "CDATA", "a");

        try {
            attrs.addAttribute("", "Import", "Import", "CDATA", "b");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        Assert.assertEquals(1, attrs.getLength());
    }
}
