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
package org.apache.tomcat.util.scan;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.util.scan.MultiReleaseUtil.MultiReleaseInfo;

public class TestMultiVersionUtil {

    @Test
    public void testValidVersion() {
        MultiReleaseInfo info = MultiReleaseUtil.parseVersion("META-INF/versions/9/test.txt");
        Assert.assertNotNull(info);
        Assert.assertTrue(info.isValid());
        Assert.assertEquals(9, info.version());
        Assert.assertEquals("test.txt", "META-INF/versions/9/test.txt".substring(info.namePos()));
    }


    /*
     * The JDK does not treat a version directory with a leading zero as a versioned entry (it resolves the base name
     * to the base entry, not to the entry below META-INF/versions/09/) so neither should Tomcat.
     */
    @Test
    public void testLeadingZeroVersionIsInvalid() {
        MultiReleaseInfo info = MultiReleaseUtil.parseVersion("META-INF/versions/09/test.txt");
        Assert.assertNotNull(info);
        Assert.assertFalse(info.isValid());
    }


    @Test
    public void testCurrentRuntimeVersion() {
        int feature = Runtime.version().feature();
        MultiReleaseInfo info = MultiReleaseUtil.parseVersion("META-INF/versions/" + feature + "/test.txt");
        Assert.assertNotNull(info);
        Assert.assertTrue(info.isValid());
        Assert.assertEquals(feature, info.version());
    }


    @Test
    public void testNotVersioned() {
        Assert.assertNull(MultiReleaseUtil.parseVersion(null));
        Assert.assertNull(MultiReleaseUtil.parseVersion(""));
        Assert.assertNull(MultiReleaseUtil.parseVersion("test/Foo.class"));
        Assert.assertNull(MultiReleaseUtil.parseVersion("META-INF/MANIFEST.MF"));
        // No trailing '/' so not the versions directory
        Assert.assertNull(MultiReleaseUtil.parseVersion("META-INF/versions"));
        Assert.assertNull(MultiReleaseUtil.parseVersion("other/META-INF/versions/9/test.txt"));
    }


    @Test
    public void testLeadingSlash() {
        String name = "/META-INF/versions/9/test.txt";
        MultiReleaseInfo info = MultiReleaseUtil.parseVersion(name);
        Assert.assertNotNull(info);
        Assert.assertTrue(info.isValid());
        Assert.assertEquals(9, info.version());
        Assert.assertEquals("test.txt", name.substring(info.namePos()));
    }


    @Test
    public void testDirectoryEntry() {
        String name = "META-INF/versions/9/";
        MultiReleaseInfo info = MultiReleaseUtil.parseVersion(name);
        Assert.assertNotNull(info);
        Assert.assertTrue(info.isValid());
        Assert.assertEquals(name.length(), info.namePos());
    }


    @Test
    public void testInvalidVersions() {
        String[] versions = { "+9", "-9", "9x", "x9", "9.0", "", "0", "09", "00", "99999999999999", "2147483648" };
        for (String version : versions) {
            String name = "META-INF/versions/" + version + "/test.txt";
            MultiReleaseInfo info = MultiReleaseUtil.parseVersion(name);
            Assert.assertNotNull(version, info);
            Assert.assertFalse(version, info.isValid());
            // The position of the resource name is still reported so callers can keep the entry under its full name
            Assert.assertEquals(version, "test.txt", name.substring(info.namePos()));
        }
    }


    @Test
    public void testNoSeparatorAfterVersionsDirectory() {
        MultiReleaseInfo info = MultiReleaseUtil.parseVersion("META-INF/versions/Foo.class");
        Assert.assertNotNull(info);
        Assert.assertFalse(info.isValid());
        Assert.assertEquals(-1, info.namePos());
    }


    /*
     * Java 21 onwards treats 8 as the lowest version directory that is used. Lower versions are ignored.
     */
    @Test
    public void testMinimumVersion() {
        for (int version = 1; version < 8; version++) {
            String name = "META-INF/versions/" + version + "/test.txt";
            MultiReleaseInfo info = MultiReleaseUtil.parseVersion(name);
            Assert.assertNotNull(Integer.toString(version), info);
            Assert.assertFalse(Integer.toString(version), info.isValid());
            Assert.assertEquals(Integer.toString(version), "test.txt", name.substring(info.namePos()));
        }

        for (int version = 8; version <= 10; version++) {
            MultiReleaseInfo info = MultiReleaseUtil.parseVersion("META-INF/versions/" + version + "/test.txt");
            Assert.assertNotNull(Integer.toString(version), info);
            Assert.assertTrue(Integer.toString(version), info.isValid());
            Assert.assertEquals(version, info.version());
        }
    }
}
