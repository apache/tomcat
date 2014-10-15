/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.http;

import org.junit.Assert;
import org.junit.Test;

public class TestMimeHeaders {

    public static final String HEADER_NAME_LC_STRING = "test";
    public static final String HEADER_NAME_UC_STRING = "TEST";
    public static final String HEADER_NAME_MIXED_STRING = "tEsT";

    @Test
    public void testSetValueStringIgnoresCase01() {
        MimeHeaders mh = new MimeHeaders();

        mh.setValue(HEADER_NAME_LC_STRING).setString(HEADER_NAME_LC_STRING);
        mh.setValue(HEADER_NAME_UC_STRING).setString(HEADER_NAME_UC_STRING);

        Assert.assertEquals(HEADER_NAME_UC_STRING, mh.getValue(HEADER_NAME_UC_STRING).toString());
        Assert.assertEquals(HEADER_NAME_UC_STRING, mh.getValue(HEADER_NAME_LC_STRING).toString());
        Assert.assertEquals(HEADER_NAME_UC_STRING, mh.getValue(HEADER_NAME_MIXED_STRING).toString());
    }

    @Test
    public void testSetValueStringIgnoresCase02() {
        MimeHeaders mh = new MimeHeaders();

        mh.setValue(HEADER_NAME_UC_STRING).setString(HEADER_NAME_UC_STRING);
        mh.setValue(HEADER_NAME_LC_STRING).setString(HEADER_NAME_LC_STRING);

        Assert.assertEquals(HEADER_NAME_LC_STRING, mh.getValue(HEADER_NAME_LC_STRING).toString());
        Assert.assertEquals(HEADER_NAME_LC_STRING, mh.getValue(HEADER_NAME_UC_STRING).toString());
        Assert.assertEquals(HEADER_NAME_LC_STRING, mh.getValue(HEADER_NAME_MIXED_STRING).toString());
    }

    @Test
    public void testSetValueStringIgnoresCase03() {
        MimeHeaders mh = new MimeHeaders();

        mh.setValue(HEADER_NAME_UC_STRING).setString(HEADER_NAME_UC_STRING);
        mh.setValue(HEADER_NAME_MIXED_STRING).setString(HEADER_NAME_MIXED_STRING);

        Assert.assertEquals(HEADER_NAME_MIXED_STRING, mh.getValue(HEADER_NAME_LC_STRING).toString());
        Assert.assertEquals(HEADER_NAME_MIXED_STRING, mh.getValue(HEADER_NAME_UC_STRING).toString());
        Assert.assertEquals(HEADER_NAME_MIXED_STRING, mh.getValue(HEADER_NAME_MIXED_STRING).toString());
    }

}
