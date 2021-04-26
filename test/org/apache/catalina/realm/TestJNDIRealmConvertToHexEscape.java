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
package org.apache.catalina.realm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class TestJNDIRealmConvertToHexEscape {

    @Parameterized.Parameters(name = "{index}: in[{0}], out[{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new String[] { "none", "none" });
        parameterSets.add(new String[] { "\\", "\\" });
        parameterSets.add(new String[] { "\\\\", "\\5C" });
        parameterSets.add(new String[] { "\\5C", "\\5C" });
        parameterSets.add(new String[] { "\\ ", "\\20" });
        parameterSets.add(new String[] { "\\20", "\\20" });
        parameterSets.add(new String[] { "\\ foo", "\\20foo" });
        parameterSets.add(new String[] { "\\20foo", "\\20foo" });
        parameterSets.add(new String[] { "\\  foo", "\\20 foo" });
        parameterSets.add(new String[] { "\\20 foo", "\\20 foo" });
        parameterSets.add(new String[] { "\\ \\ foo", "\\20\\20foo" });
        parameterSets.add(new String[] { "\\20\\20foo", "\\20\\20foo" });
        parameterSets.add(new String[] { "foo\\ ", "foo\\20" });
        parameterSets.add(new String[] { "foo\\20", "foo\\20" });
        parameterSets.add(new String[] { "foo \\ ", "foo \\20" });
        parameterSets.add(new String[] { "foo \\20", "foo \\20" });
        parameterSets.add(new String[] { "foo\\ \\ ", "foo\\20\\20" });
        parameterSets.add(new String[] { "foo\\20\\20", "foo\\20\\20" });

        return parameterSets;
    }


    @Parameter(0)
    public String in;
    @Parameter(1)
    public String out;


    @Test
    public void testConvertToHexEscape() throws Exception {
        String result = JNDIRealm.convertToHexEscape(in);
        Assert.assertEquals(out, result);
    }
}
