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
package org.apache.tomcat.util.modeler;

import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

public class TestUtil {

    @Test
    public void testObjectNameValueNeedsQuote() {
        Assert.assertFalse(Util.objectNameValueNeedsQuote("plain"));
        Assert.assertFalse(Util.objectNameValueNeedsQuote(""));

        Assert.assertTrue(Util.objectNameValueNeedsQuote("a,b"));
        Assert.assertTrue(Util.objectNameValueNeedsQuote("a=b"));
        Assert.assertTrue(Util.objectNameValueNeedsQuote("a:b"));
        Assert.assertTrue(Util.objectNameValueNeedsQuote("a*b"));
        Assert.assertTrue(Util.objectNameValueNeedsQuote("a?b"));
        Assert.assertTrue(Util.objectNameValueNeedsQuote("a\"b"));
        Assert.assertTrue(Util.objectNameValueNeedsQuote("a\nb"));
    }

    @Test
    public void testObjectNameValueWithQuoteParsesWhenQuoted() throws Exception {
        String[] values = { "a\"b", "a\nb", "a,\"b\n:c" };

        for (String value : values) {
            Assert.assertTrue(Util.objectNameValueNeedsQuote(value));

            String onameStr = "tester:type=Tester,name=" + ObjectName.quote(value);
            ObjectName oname = new ObjectName(onameStr);

            // A quoted value is retained with its quoting in the key properties
            Assert.assertEquals(ObjectName.quote(value), oname.getKeyPropertyList().get("name"));
        }
    }
}
