/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.http.parser;

import java.io.StringReader;
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
public class TestHttpParserHost {

    private static final Class<? extends Exception> IAE = IllegalArgumentException.class;

    @Parameter(0)
    public TestType testType;

    @Parameter(1)
    public String input;

    @Parameter(2)
    public Integer expectedResult;

    @Parameter(3)
    public Class<? extends Exception> expectedException;


    @Parameters(name="{index}: host {1}")
    public static Collection<Object[]> inputs() {
        List<Object[]> result = new ArrayList<>();
        // IPv4 - valid
        result.add(new Object[] { TestType.IPv4, "127.0.0.1", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "127.0.0.1:8080", Integer.valueOf(9), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0.0", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0.0:8080", Integer.valueOf(7), null} );
        // IPv4 - invalid
        result.add(new Object[] { TestType.IPv4, ".0.0.0", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "0..0.0", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "0]", Integer.valueOf(-1), IAE} );
        // Domain Name - valid
        result.add(new Object[] { TestType.IPv4, "0", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.0", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.0:8080", Integer.valueOf(3), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0:8080", Integer.valueOf(5), null} );
        result.add(new Object[] { TestType.IPv4, "0.00.0.0", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.00.0.0:8080", Integer.valueOf(8), null} );
        result.add(new Object[] { TestType.IPv4, "256.0.0.0", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "256.0.0.0:8080", Integer.valueOf(9), null} );
        result.add(new Object[] { TestType.IPv4, "0.256.0.0", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.256.0.0:8080", Integer.valueOf(9), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.256.0", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.256.0:8080", Integer.valueOf(9), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0.256", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0.256:8080", Integer.valueOf(9), null} );
        result.add(new Object[] { TestType.IPv4, "0.a.0.0", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.a.0.0:8080", Integer.valueOf(7), null} );
        result.add(new Object[] { TestType.IPv4, "localhost", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "localhost:8080", Integer.valueOf(9), null} );
        result.add(new Object[] { TestType.IPv4, "4294967295.localhost", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "4294967295.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "tomcat.apache.org", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "tomcat.apache.org:8080", Integer.valueOf(17), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0.com:8080", Integer.valueOf(9), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0.0.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0.0.com:8080", Integer.valueOf(11), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.com:8080", Integer.valueOf(11), null} );
        result.add(new Object[] { TestType.IPv4, "1foo.0.0.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "1foo.0.0.com:8080", Integer.valueOf(12), null} );
        result.add(new Object[] { TestType.IPv4, "foo1.0.0.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "foo1.0.0.com:8080", Integer.valueOf(12), null} );
        result.add(new Object[] { TestType.IPv4, "1foo1.0.0.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "1foo1.0.0.com:8080", Integer.valueOf(13), null} );
        result.add(new Object[] { TestType.IPv4, "1-foo.0.0.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "1-foo.0.0.com:8080", Integer.valueOf(13), null} );
        result.add(new Object[] { TestType.IPv4, "1--foo.0.0.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "1--foo.0.0.com:8080", Integer.valueOf(14), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.1com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.1com:8080", Integer.valueOf(12), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.com1", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.com1:8080", Integer.valueOf(12), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.1com1", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.1com1:8080", Integer.valueOf(13), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.1-com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.1-com:8080", Integer.valueOf(13), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.1--com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "foo.0.0.1--com:8080", Integer.valueOf(14), null} );
        result.add(new Object[] { TestType.IPv4, "com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "com:8080", Integer.valueOf(3), null} );
        result.add(new Object[] { TestType.IPv4, "0com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0com:8080", Integer.valueOf(4), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0com:8080", Integer.valueOf(8), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0.0com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "0.0.0.0com:8080", Integer.valueOf(10), null} );
        result.add(new Object[] { TestType.IPv4, "123", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "123:8080", Integer.valueOf(3), null} );
        result.add(new Object[] { TestType.IPv4, "foo.bar.0com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "foo.bar.0com:8080", Integer.valueOf(12), null} );
        result.add(new Object[] { TestType.IPv4, "myapp-t.mydomain.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "myapp-t.mydomain.com:8080", Integer.valueOf(20), null} );
        result.add(new Object[] { TestType.IPv4, "myapp-t.my-domain.com", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "myapp-t.my-domain.com:8080", Integer.valueOf(21), null} );
        result.add(new Object[] { TestType.IPv4, "myapp-t.my-domain.c-om", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "myapp-t.my-domain.c-om:8080", Integer.valueOf(22), null} );
        result.add(new Object[] { TestType.IPv4, "gateway.demo-ilt-latest-demo:9000", Integer.valueOf(28), null} );
        // Domain Name with trailing dot - valid
        result.add(new Object[] { TestType.IPv4, "0.0.0.", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "myapp-t.mydomain.com.", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "myapp-t.mydomain.com.:8080", Integer.valueOf(21), null} );
        result.add(new Object[] { TestType.IPv4, "foo.bar.", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv4, "foo.bar.:8080", Integer.valueOf(8), null} );
        // Domain Name - invalid
        result.add(new Object[] { TestType.IPv4, ".", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, ".:8080", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, ".foo.bar", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "-foo.bar", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "foo.bar-", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "foo.bar-:8080", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "^foo.bar", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "foo-.bar", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "f*oo.bar", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "foo..bar", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "foo.-bar", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "foo.^bar", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "foo.b*ar", Integer.valueOf(-1), IAE} );
        // IPv6 - valid
        result.add(new Object[] { TestType.IPv6, "[::1]", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[::1]:8080", Integer.valueOf(5), null} );
        result.add(new Object[] { TestType.IPv6, "[1::1]", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[1::1]:8080", Integer.valueOf(6), null} );
        result.add(new Object[] { TestType.IPv6, "[A::A]", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[A::A]:8080", Integer.valueOf(6), null} );
        result.add(new Object[] { TestType.IPv6, "[A:0::A]", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[A:0::A]:8080", Integer.valueOf(8), null} );
        result.add(new Object[] { TestType.IPv6, "[1234:5678:90AB:CDEF:1234:5678:90AB:CDEF]",
                Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[1234:5678:90AB:CDEF:1234:5678:90AB:CDEF]:8080",
                Integer.valueOf(41), null} );
        result.add(new Object[] { TestType.IPv6, "[::5678:90AB:CDEF:1234:5678:90AB:CDEF]:8080",
                Integer.valueOf(38), null} );
        result.add(new Object[] { TestType.IPv6, "[1234:5678:90AB:CDEF:1234:5678:90AB::]:8080",
                Integer.valueOf(38), null} );
        result.add(new Object[] { TestType.IPv6, "[0:0:0:0:0:0:0:0]", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[0:0:0:0:0:0:0:0]:8080",
                Integer.valueOf(17), null} );
        result.add(new Object[] { TestType.IPv6, "[::127.0.0.1]", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[::127.0.0.1]:8080", Integer.valueOf(13), null} );
        result.add(new Object[] { TestType.IPv6, "[1::127.0.0.1]", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[1::127.0.0.1]:8080", Integer.valueOf(14), null} );
        result.add(new Object[] { TestType.IPv6, "[A::127.0.0.1]", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[A::127.0.0.1]:8080", Integer.valueOf(14), null} );
        result.add(new Object[] { TestType.IPv6, "[A:0::127.0.0.1]", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[A:0::127.0.0.1]:8080", Integer.valueOf(16), null} );
        result.add(new Object[] { TestType.IPv6, "[1234:5678:90AB:CDEF:1234:5678:127.0.0.1]",
                Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[1234:5678:90AB:CDEF:1234:5678:127.0.0.1]:8080",
                Integer.valueOf(41), null} );
        result.add(new Object[] { TestType.IPv6, "[::5678:90AB:CDEF:1234:5678:127.0.0.1]:8080",
                Integer.valueOf(38), null} );
        result.add(new Object[] { TestType.IPv6, "[0:0:0:0:0:0:127.0.0.1]", Integer.valueOf(-1), null} );
        result.add(new Object[] { TestType.IPv6, "[0:0:0:0:0:0:127.0.0.1]:8080",
                Integer.valueOf(23), null} );
        result.add(new Object[] { TestType.IPv6, "[::1.2.3.4]", Integer.valueOf(-1), null} );
        // IPv6 - invalid
        result.add(new Object[] { TestType.IPv6, "[1234:5678:90AB:CDEF:1234:127.0.0.1]",
                Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1234:5678:90AB:CDEF:1234:5678:127.0.0.1",
                Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[0::0::0]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[0:0:G:0:0:0:0:0]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[00000:0:0:0:0:0:0:0]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1234:5678:90AB:CDEF:1234:5678:90AB:]",
                Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1234:5678:90AB:CDEF:1234:5678:90AB:CDEF",
                Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[::127.00.0.1]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[0::0::127.0.0.1]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[0:0:G:0:0:0:127.0.0.1]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[00000:0:0:0:0:0:127.0.0.1]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1::127..0.1]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1::127..0.1]:8080", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1::127.a.0.1]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1::127.a.0.1]:8080", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1::127.-.0.1]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1::127.-.0.1]:8080", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[::1]'", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[:2222:3333:4444:5555:6666:7777:8888]",
                Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1111:::3333:4444:5555:6666:7777:8888]",
                Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "::1]", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1111:2222:3333:4444:5555:6666:7777:8888:9999]",
                Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1111:2222:3333:4444:5555:6666:7777:1.2.3.4]",
            Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv6, "[1111:2222:3333]",
            Integer.valueOf(-1), IAE} );
        // Domain name - invalid port
        result.add(new Object[] { TestType.IPv4, "localhost:x", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "localhost:-1", Integer.valueOf(-1), IAE} );
        // IPv4 - invalid port
        result.add(new Object[] { TestType.IPv4, "127.0.0.1:x", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "127.0.0.1:-1", Integer.valueOf(-1), IAE} );
        // IPv6 - invalid port
        result.add(new Object[] { TestType.IPv4, "[::1]:x", Integer.valueOf(-1), IAE} );
        result.add(new Object[] { TestType.IPv4, "[::1]:-1", Integer.valueOf(-1), IAE} );
        return result;
    }


    @Test
    public void testHost() {
        Class<? extends Exception> exceptionClass = null;
        int result = -1;
        try {
            result = Host.parse(input);
        } catch (Exception e) {
            exceptionClass = e.getClass();
        }
        Assert.assertEquals(input, expectedResult.intValue(), result);
        if (expectedException == null) {
            Assert.assertNull(input, exceptionClass);
        } else {
            Assert.assertNotNull(exceptionClass);
            Assert.assertTrue(input, expectedException.isAssignableFrom(exceptionClass));
        }
    }


    @Test
    public void testHostType() {
        Class<? extends Exception> exceptionClass = null;
        int result = -1;
        try {
            StringReader sr = new StringReader(input);
            switch(testType) {
                case IPv4:
                    result = HttpParser.readHostIPv4(sr, false);
                    break;
                case IPv6:
                    result = HttpParser.readHostIPv6(sr);
                    break;

            }
        } catch (Exception e) {
            exceptionClass = e.getClass();
        }
        Assert.assertEquals(input, expectedResult.intValue(), result);
        if (expectedException == null) {
            Assert.assertNull(input, exceptionClass);
        } else {
            Assert.assertNotNull(exceptionClass);
            Assert.assertTrue(input, expectedException.isAssignableFrom(exceptionClass));
        }
    }


    private enum TestType {
        IPv4,
        IPv6
    }
}
