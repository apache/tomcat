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
package org.apache.catalina.ssi;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;

@RunWith(Parameterized.class)
public class TestRegExpCapture extends TomcatBaseTest {

    @Parameters(name = "{index}: [{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        // d is always the empty string
        // Neither a nor b are set, c is empty
        parameterSets.add(new Object[] { "", "<p>a(none)b(none)cd</p>" });
        // a is set, b is not, c is empty
        parameterSets.add(new Object[] { "?a=1", "<p>a1b(none)cd</p>" });
        // a is not set, b is set, c is the same as b
        parameterSets.add(new Object[] { "?b=1", "<p>a(none)b1c1d</p>" });

        return parameterSets;
    }

    private final String queryString;
    private final String expectedInBody;


    public TestRegExpCapture(String queryString, String expectedInBody) {
        this.queryString = queryString;
        this.expectedInBody = expectedInBody;
    }

    @Test
    public void testBug53387() throws Exception {
        Tomcat tomcat = getTomcatInstance();

        File appDir = new File("test/webapp");
        Context ctx = tomcat.addWebapp(null, "/test", appDir.getAbsolutePath());

        // SSI requires a privileged Context
        ctx.setPrivileged(true);

        FilterDef ssiFilter = new FilterDef();
        ssiFilter.setFilterName("ssiFilter");
        ssiFilter.setFilterClass(SSIFilter.class.getName());
        FilterMap ssiFilterMap = new FilterMap();
        ssiFilterMap.setFilterName("ssiFilter");
        ssiFilterMap.addURLPatternDecoded("*.shtml");
        ctx.addFilterDef(ssiFilter);
        ctx.addFilterMap(ssiFilterMap);

        ctx.addMimeMapping("shtml", "text/x-server-parsed-html");

        tomcat.start();

        ByteChunk body = new ByteChunk();
        int rc = getUrl("http://localhost:" + getPort() +
                "/test/bug5nnnn/bug53387.shtml" + queryString, body, null);

        Assert.assertEquals(200,  rc);

        String text = body.toString();
        Assert.assertTrue(text, text.contains(expectedInBody));
    }
}
