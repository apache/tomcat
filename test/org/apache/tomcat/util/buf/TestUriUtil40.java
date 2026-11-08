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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

public class TestUriUtil40 extends TesterUriUtilBase {

    public TestUriUtil40() {
        super("@");
    }

    @Test
    public void testBuildJarUrlPreservesSeparatorSlash() throws IOException {
        // Use the String overload with a literal file: URL so the expected
        // value does not depend on File.toURI() behaviour (which varies
        // with platform)
        String result = UriUtil.buildJarUrl("file:/patha/pathb@/pathc").toString();
        // The custom separator must be escaped but the trailing '/' of the
        // separator must be preserved, consistent with the default handling
        // of "*/" being replaced with "%2a/"
        Assert.assertEquals("jar:file:/patha/pathb%40/pathc!/", result);
    }
}
