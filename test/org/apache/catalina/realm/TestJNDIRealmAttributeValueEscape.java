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
public class TestJNDIRealmAttributeValueEscape {

    @Parameterized.Parameters(name = "{index}: in[{0}], out[{1}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        // No escaping required
        parameterSets.add(new String[] { "none", "none" });
        // Simple cases (same order as RFC 4512 section 2)
        // Each appearing at the beginning, middle and ent
        parameterSets.add(new String[] { " test", "\\20test" });
        parameterSets.add(new String[] { "te st", "te st" });
        parameterSets.add(new String[] { "test ", "test\\20" });
        parameterSets.add(new String[] { "#test", "\\23test" });
        parameterSets.add(new String[] { "te#st", "te#st" });
        parameterSets.add(new String[] { "test#", "test#" });
        parameterSets.add(new String[] { "\"test", "\\22test" });
        parameterSets.add(new String[] { "te\"st", "te\\22st" });
        parameterSets.add(new String[] { "test\"", "test\\22" });
        parameterSets.add(new String[] { "+test", "\\2Btest" });
        parameterSets.add(new String[] { "te+st", "te\\2Bst" });
        parameterSets.add(new String[] { "test+", "test\\2B" });
        parameterSets.add(new String[] { ",test", "\\2Ctest" });
        parameterSets.add(new String[] { "te,st", "te\\2Cst" });
        parameterSets.add(new String[] { "test,", "test\\2C" });
        parameterSets.add(new String[] { ";test", "\\3Btest" });
        parameterSets.add(new String[] { "te;st", "te\\3Bst" });
        parameterSets.add(new String[] { "test;", "test\\3B" });
        parameterSets.add(new String[] { "<test", "\\3Ctest" });
        parameterSets.add(new String[] { "te<st", "te\\3Cst" });
        parameterSets.add(new String[] { "test<", "test\\3C" });
        parameterSets.add(new String[] { ">test", "\\3Etest" });
        parameterSets.add(new String[] { "te>st", "te\\3Est" });
        parameterSets.add(new String[] { "test>", "test\\3E" });
        parameterSets.add(new String[] { "\\test", "\\5Ctest" });
        parameterSets.add(new String[] { "te\\st", "te\\5Cst" });
        parameterSets.add(new String[] { "test\\", "test\\5C" });
        parameterSets.add(new String[] { "\u0000test", "\\00test" });
        parameterSets.add(new String[] { "te\u0000st", "te\\00st" });
        parameterSets.add(new String[] { "test\u0000", "test\\00" });
        return parameterSets;
    }


    @Parameter(0)
    public String in;
    @Parameter(1)
    public String out;

    private JNDIRealm realm = new JNDIRealm();

    @Test
    public void testConvertToHexEscape() throws Exception {
        String result = realm.doAttributeValueEscaping(in);
        Assert.assertEquals(out, result);
    }
}