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
package org.apache.tomcat.util.http;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class TestRequestUtilNormalize {

    @Parameterized.Parameters(name = "{index}: input[{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new String[] { "//something", "/something" });
        parameterSets.add(new String[] { "some//thing", "/some/thing" });
        parameterSets.add(new String[] { "something//", "/something/" });
        parameterSets.add(new String[] { "//", "/" });
        parameterSets.add(new String[] { "///", "/" });
        parameterSets.add(new String[] { "////", "/" });
        parameterSets.add(new String[] { "/.", "/" });
        parameterSets.add(new String[] { "/./", "/" });
        parameterSets.add(new String[] { ".", "/" });
        parameterSets.add(new String[] { "/..", null });
        parameterSets.add(new String[] { "/../", null });
        parameterSets.add(new String[] { "..", null });
        parameterSets.add(new String[] { "//..", null });
        parameterSets.add(new String[] { "//../", null });
        parameterSets.add(new String[] { "/./..", null });
        parameterSets.add(new String[] { "/./../", null });
        parameterSets.add(new String[] { "/a/../..", null });
        parameterSets.add(new String[] { "/a/../../", null });
        parameterSets.add(new String[] { "/a/..", "/" });
        parameterSets.add(new String[] { "/a/.", "/a" });
        parameterSets.add(new String[] { "/a/../", "/" });
        parameterSets.add(new String[] { "/a/./", "/a/" });
        parameterSets.add(new String[] { "/a/b/..", "/a" });
        parameterSets.add(new String[] { "/a/b/.", "/a/b" });
        parameterSets.add(new String[] { "/a/b/../", "/a/" });
        parameterSets.add(new String[] { "/a/b/./", "/a/b/" });

        return parameterSets;
    }


    @Parameter(0)
    public String input;
    @Parameter(1)
    public String expected;


    @Test
    public void testNormalize() {
        Assert.assertEquals(expected,RequestUtil.normalize(input));
    }
}
