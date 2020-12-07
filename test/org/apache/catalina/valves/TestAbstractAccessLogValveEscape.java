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
package org.apache.catalina.valves;

import java.io.CharArrayWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;


@RunWith(Parameterized.class)
public class TestAbstractAccessLogValveEscape {

    @Parameters(name = "{index}: [{0}]")
    public static Collection<Object[]> parameters() {

        List<Object[]> parameters = new ArrayList<>();

        parameters.add(new String[] { null, "-" });
        parameters.add(new String[] { "", "-" });
        parameters.add(new String[] { "ok", "ok" });
        parameters.add(new String[] { "o\tk", "o\\tk" });
        parameters.add(new String[] { "o\u0002k", "o\\u0002k" });
        parameters.add(new String[] { "o\u007fk", "o\\u007fk" });
        parameters.add(new String[] { "o\u0080k", "o\\u0080k" });
        parameters.add(new String[] { "o\u00ffk", "o\\u00ffk" });
        parameters.add(new String[] { "o\"k", "o\\\"k" });

        return parameters;
    }

    @Parameter(0)
    public String input;

    @Parameter(1)
    public String expected;


    @Test
    public void testEscape() {
        CharArrayWriter actual = new CharArrayWriter();
        AbstractAccessLogValve.escapeAndAppend(input, actual);
        Assert.assertEquals(expected, actual.toString());
    }
}
