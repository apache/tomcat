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
package org.apache.catalina.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class TestContextNameExtractFromPath {

    @Parameterized.Parameters(name = "{index}: path[{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] {"/foo/bar", "/bar", ""});
        parameterSets.add(new Object[] {"C:\\foo\\bar", "/bar", ""});
        parameterSets.add(new Object[] {"/foo/bar.war", "/bar", ""});
        parameterSets.add(new Object[] {"C:\\foo\\bar.war", "/bar", ""});
        parameterSets.add(new Object[] {"/foo/bar.xml", "/bar", ""});
        parameterSets.add(new Object[] {"C:\\foo\\bar.xml", "/bar", ""});
        parameterSets.add(new Object[] {"/foo/bar////", "/bar", ""});
        parameterSets.add(new Object[] {"C:\\foo\\bar\\\\", "/bar", ""});
        parameterSets.add(new Object[] {"/foo/bar##4", "/bar", "4"});
        parameterSets.add(new Object[] {"C:\\foo\\bar##4", "/bar", "4"});
        parameterSets.add(new Object[] {"/foo/bar#foo##4", "/bar/foo", "4"});
        parameterSets.add(new Object[] {"C:\\foo\\bar#foo##4", "/bar/foo", "4"});
        parameterSets.add(new Object[] {"/foo/ROOT", "", ""});
        parameterSets.add(new Object[] {"C:\\foo\\ROOT", "", ""});

        return parameterSets;
    }

    @Parameter(0)
    public String path;

    @Parameter(1)
    public String expectedPath;

    @Parameter(2)
    public String expectedVersion;


    @Test
    public void testConextNameExtractFromPath() throws Exception {
        ContextName cn = ContextName.extractFromPath(path);
        Assert.assertEquals(expectedPath,  cn.getPath());
        Assert.assertEquals(expectedVersion, cn.getVersion());
    }
}
