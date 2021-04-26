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
        Assert.assertEquals("[{0}] a''a", Utils.formatValue("[{0}] a'a"));
    }

    @Test
    public void testQuoteReplacement02() {
        Assert.assertEquals("[{0}] a''", Utils.formatValue("[{0}] a'"));
    }


    @Test
    public void testQuoteReplacement03() {
        Assert.assertEquals("''a [{0}]", Utils.formatValue("'a [{0}]"));
    }

    @Test
    public void testQuoteReplacement05() {
        Assert.assertEquals("[{0}] ''a'' bbb", Utils.formatValue("[{0}] 'a' bbb"));
    }

    @Test
    public void testQuoteReplacement06() {
        Assert.assertEquals("[{0}] ''aa'' bbb", Utils.formatValue("[{0}] 'aa' bbb"));
    }

}
