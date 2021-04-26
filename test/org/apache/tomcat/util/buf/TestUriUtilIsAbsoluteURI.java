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
package org.apache.tomcat.util.buf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;


@RunWith(Parameterized.class)
public class TestUriUtilIsAbsoluteURI {

    @Parameterized.Parameters(name = "{index}: path[{0}], expected[{1}]")
    public static Collection<Object[]> parameters() {

        List<Object[]> parameterSets = new ArrayList<>();

        parameterSets.add(new Object[] { "", Boolean.FALSE } );

        parameterSets.add(new Object[] { "h", Boolean.FALSE } );
        parameterSets.add(new Object[] { "ht", Boolean.FALSE } );
        parameterSets.add(new Object[] { "htt", Boolean.FALSE } );
        parameterSets.add(new Object[] { "http", Boolean.FALSE } );
        parameterSets.add(new Object[] { "http:", Boolean.FALSE } );
        parameterSets.add(new Object[] { "http:/", Boolean.FALSE } );
        parameterSets.add(new Object[] { "http://", Boolean.TRUE } );
        parameterSets.add(new Object[] { "http://foo", Boolean.TRUE } );

        parameterSets.add(new Object[] { "f", Boolean.FALSE } );
        parameterSets.add(new Object[] { "fi", Boolean.FALSE } );
        parameterSets.add(new Object[] { "fil", Boolean.FALSE } );
        parameterSets.add(new Object[] { "file", Boolean.FALSE } );
        parameterSets.add(new Object[] { "file:", Boolean.FALSE } );
        parameterSets.add(new Object[] { "file:/", Boolean.TRUE } );
        parameterSets.add(new Object[] { "file://", Boolean.TRUE } );

        parameterSets.add(new Object[] { "c", Boolean.FALSE } );
        parameterSets.add(new Object[] { "c:", Boolean.FALSE } );
        parameterSets.add(new Object[] { "c:/", Boolean.FALSE } );
        parameterSets.add(new Object[] { "c:/foo", Boolean.FALSE } );

        return parameterSets;
    }


    @Parameter(0)
    public String path;

    @Parameter(1)
    public Boolean valid;

    @Test
    public void test() {
        boolean result = UriUtil.isAbsoluteURI(path);
        Assert.assertEquals(path, valid, Boolean.valueOf(result));
    }
}
