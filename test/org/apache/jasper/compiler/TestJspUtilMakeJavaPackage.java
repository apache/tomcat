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
package org.apache.jasper.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class TestJspUtilMakeJavaPackage {

    @Parameterized.Parameters(name = "{index}: input[{0}], expected [{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { "/foo", "foo"});
        parameterSets.add(new Object[] { "//foo", "foo"});
        parameterSets.add(new Object[] { "//foo//", "foo"});
        parameterSets.add(new Object[] { "/foo//", "foo"});
        parameterSets.add(new Object[] { "/foo/", "foo"});
        parameterSets.add(new Object[] { "foo/", "foo"});

        parameterSets.add(new Object[] { "/foo/bar", "foo.bar"});
        parameterSets.add(new Object[] { "//foo/bar", "foo.bar"});
        parameterSets.add(new Object[] { "//foo//bar", "foo.bar"});
        parameterSets.add(new Object[] { "/foo//bar", "foo.bar"});
        parameterSets.add(new Object[] { "/foo/bar", "foo.bar"});
        parameterSets.add(new Object[] { "foo/bar", "foo.bar"});

        return parameterSets;
    }

    @Parameter(0)
    public String input;

    @Parameter(1)
    public String expected;

    @Test
    public void doTest() {
        Assert.assertEquals(expected, JspUtil.makeJavaPackage(input));
    }
}
