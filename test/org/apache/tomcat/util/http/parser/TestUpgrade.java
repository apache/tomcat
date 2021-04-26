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
package org.apache.tomcat.util.http.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class TestUpgrade {

    private static final Integer FOUR = Integer.valueOf(4);

    @Parameterized.Parameters(name = "{index}: headers[{0}]")
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<>();

        // Valid example taken from RFC 7230
        parameterSets.add(new Object[] { Arrays.asList(new String[] { "HTTP/2.0, SHTTP/1.3, IRC/6.9, RTA/x11" }), FOUR } );
        parameterSets.add(new Object[] { Arrays.asList(new String[] { "HTTP/2.0", "SHTTP/1.3, IRC/6.9, RTA/x11" }),FOUR } );
        parameterSets.add(new Object[] { Arrays.asList(new String[] { "HTTP/2.0", "SHTTP/1.3", "IRC/6.9", "RTA/x11" }), FOUR } );

        // As above but without the version info
        parameterSets.add(new Object[] { Arrays.asList(new String[] { "HTTP", "SHTTP", "IRC", "RTA" }), FOUR } );

        // Empty version for final protocol
        parameterSets.add(new Object[] { Arrays.asList(new String[] { "HTTP/2.0", "SHTTP/1.3", "IRC/6.9", "RTA/" }), null} );

        // Empty name for final protocol
        parameterSets.add(new Object[] { Arrays.asList(new String[] { "HTTP/2.0", "SHTTP/1.3", "IRC/6.9", "/x11" }), null} );

        // Make final protocol a non-token
        parameterSets.add(new Object[] { Arrays.asList(new String[] { "HTTP/2.0", "SHTTP/1.3", "IRC/6.9", "RTA[/x11" }), null} );

        // Make final version a non-token
        parameterSets.add(new Object[] { Arrays.asList(new String[] { "HTTP/2.0", "SHTTP/1.3", "IRC/6.9", "RTA/x}11" }), null} );

        // Empty header isn't valid
        parameterSets.add(new Object[] { Arrays.asList(new String[] { "" }), null } );

        // Nulls shouldn't happen but check they are treated as invalid to be safe
        parameterSets.add(new Object[] { Arrays.asList(new String[] { null }), null } );

        return parameterSets;
    }


    @Parameter(0)
    public List<String> headers;

    @Parameter(1)
    public Integer count;

    @Test
    public void testUpgrade() {
        List<Upgrade> result = Upgrade.parse(Collections.enumeration(headers));
        if (count == null) {
            Assert.assertNull(result);
        } else {
            Assert.assertNotNull(result);
            Assert.assertEquals(count.intValue(), result.size());
        }
    }
}
