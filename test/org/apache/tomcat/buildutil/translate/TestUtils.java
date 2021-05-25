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
package org.apache.tomcat.buildutil.translate;

import org.junit.Assert;
import org.junit.Test;

public class TestUtils {

    @Test
    public void testQuoteReplacement01() {
        Assert.assertEquals("[{0}] a''a", Utils.formatValueImport("[{0}] a'a"));
    }

    @Test
    public void testQuoteReplacement02() {
        Assert.assertEquals("[{0}] a''", Utils.formatValueImport("[{0}] a'"));
    }


    @Test
    public void testQuoteReplacement03() {
        Assert.assertEquals("''a [{0}]", Utils.formatValueImport("'a [{0}]"));
    }

    @Test
    public void testQuoteReplacement05() {
        Assert.assertEquals("[{0}] ''a'' bbb", Utils.formatValueImport("[{0}] 'a' bbb"));
    }

    @Test
    public void testQuoteReplacement06() {
        Assert.assertEquals("[{0}] ''aa'' bbb", Utils.formatValueImport("[{0}] 'aa' bbb"));
    }

    @Test
    public void testFormatValue01() {
        // Import from Tomcat
        Assert.assertEquals("\\n\\\n</web-fragment>\\n", Utils.formatValueImport("\\n\\\n</web-fragment>\\n"));
    }

    @Test
    public void testFormatValue02() {
        // Import from POEditor
        Assert.assertEquals("\\n\\\n</web-fragment>\\n", Utils.formatValueImport("\\n</web-fragment>\\n"));
    }

    @Test
    public void testFormatValue03() {
        // Export from Tomcat
        Assert.assertEquals("line1\\n\\\nline2\\n\\\nline3", Utils.formatValueExport("line1\nline2\nline3"));
    }

    @Test
    public void testFormatValue04() {
        // Export from Tomcat
        Assert.assertEquals(Utils.PADDING + "\\n\\\nline2\\n\\\nline3", Utils.formatValueExport("\nline2\nline3"));
    }

    @Test
    public void testFormatValue05() {
        // Export from Tomcat
        Assert.assertEquals("line1\\n\\\n\\tline2\\n\\\n\\tline3", Utils.formatValueExport("line1\n\tline2\n\tline3"));
    }
}
