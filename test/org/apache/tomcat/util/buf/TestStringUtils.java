/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.buf;

import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;

/*
 * None of these tests should throw a NPE.
 */
public class TestStringUtils {

    @Test
    public void testNullArray() {
        Assert.assertEquals("", StringUtils.join((String[]) null));
    }


    @Test
    public void testNullArrayCharStringBuilder() {
        StringBuilder sb = new StringBuilder();
        StringUtils.join((String[]) null, ',', sb);
        Assert.assertEquals("", sb.toString());
    }


    @Test
    public void testNullCollection() {
        Assert.assertEquals("", StringUtils.join((Collection<String>) null));
    }


    @Test
    public void testNullCollectionChar() {
        Assert.assertEquals("", StringUtils.join(null, ','));
    }


    @Test
    public void testNullIterableCharStringBuilder() {
        StringBuilder sb = new StringBuilder();
        StringUtils.join((Iterable<String>) null, ',', sb);
        Assert.assertEquals("", sb.toString());
    }


    @Test
    public void testNullArrayCharFunctionStringBuilder() {
        StringBuilder sb = new StringBuilder();
        StringUtils.join((String[]) null, ',', null, sb);
        Assert.assertEquals("", sb.toString());
    }


    @Test
    public void testNullIterableCharFunctionStringBuilder() {
        StringBuilder sb = new StringBuilder();
        StringUtils.join((Iterable<String>) null, ',', null, sb);
        Assert.assertEquals("", sb.toString());
    }
}
