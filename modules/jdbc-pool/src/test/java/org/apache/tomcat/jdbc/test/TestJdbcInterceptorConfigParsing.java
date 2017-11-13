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
package org.apache.tomcat.jdbc.test;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;
import org.apache.tomcat.jdbc.pool.TrapException;

/**
 * Test of JdbcInterceptor configuration parsing in the
 * {@link org.apache.tomcat.jdbc.pool.PoolProperties PoolProperties} class.
 * Added in context of bug 54395.
 */
public class TestJdbcInterceptorConfigParsing {

    @Test
    public void testBasic() throws Exception {
        String interceptorConfig = "FirstInterceptor;SecondInterceptor(parm1=value1,parm2=value2)";
        PoolProperties props = new PoolProperties();
        props.setJdbcInterceptors(interceptorConfig);
        InterceptorDefinition[] interceptorDefs = props.getJdbcInterceptorsAsArray();
        Assert.assertNotNull(interceptorDefs);

        // 3 items because parser automatically inserts TrapException interceptor to front of list
        Assert.assertEquals(interceptorDefs.length, 3);
        Assert.assertEquals(interceptorDefs[0].getClassName(), TrapException.class.getName());

        Assert.assertNotNull(interceptorDefs[1]);
        Assert.assertEquals(interceptorDefs[1].getClassName(), "FirstInterceptor");
        Assert.assertNotNull(interceptorDefs[2]);
        Assert.assertEquals(interceptorDefs[2].getClassName(), "SecondInterceptor");

        Map<String, InterceptorProperty> secondProps = interceptorDefs[2].getProperties();
        Assert.assertNotNull(secondProps);
        Assert.assertEquals(secondProps.size(), 2);
        Assert.assertNotNull(secondProps.get("parm1"));
        Assert.assertEquals(secondProps.get("parm1").getValue(), "value1");
        Assert.assertNotNull(secondProps.get("parm2"));
        Assert.assertEquals(secondProps.get("parm2").getValue(), "value2");
    }

    @Test
    public void testWhitespace() throws Exception {
        String interceptorConfig = "FirstInterceptor ; \n" +
            "SecondInterceptor (parm1  = value1 , parm2= value2 ) ; \n\n" +
            "\t org.cyb.ThirdInterceptor(parm1=value1);  \n" +
            "EmptyParmValInterceptor(parm1=  )";
        PoolProperties props = new PoolProperties();
        props.setJdbcInterceptors(interceptorConfig);
        InterceptorDefinition[] interceptorDefs = props.getJdbcInterceptorsAsArray();
        Assert.assertNotNull(interceptorDefs);

        // 5 items because parser automatically inserts TrapException interceptor to front of list
        Assert.assertEquals(interceptorDefs.length, 5);
        Assert.assertEquals(interceptorDefs[0].getClassName(), TrapException.class.getName());

        Assert.assertNotNull(interceptorDefs[1]);
        Assert.assertEquals(interceptorDefs[1].getClassName(), "FirstInterceptor");
        Assert.assertNotNull(interceptorDefs[2]);
        Assert.assertEquals(interceptorDefs[2].getClassName(), "SecondInterceptor");
        Assert.assertNotNull(interceptorDefs[3]);
        Assert.assertEquals(interceptorDefs[3].getClassName(), "org.cyb.ThirdInterceptor");

        Map<String, InterceptorProperty> secondProps = interceptorDefs[2].getProperties();
        Assert.assertNotNull(secondProps);
        Assert.assertEquals(secondProps.size(), 2);
        Assert.assertNotNull(secondProps.get("parm1"));
        Assert.assertEquals(secondProps.get("parm1").getValue(), "value1");
        Assert.assertNotNull(secondProps.get("parm2"));
        Assert.assertEquals(secondProps.get("parm2").getValue(), "value2"); // Bug 54395

        Map<String, InterceptorProperty> thirdProps = interceptorDefs[3].getProperties();
        Assert.assertNotNull(thirdProps);
        Assert.assertEquals(thirdProps.size(), 1);
        Assert.assertNotNull(thirdProps.get("parm1"));
        Assert.assertEquals(thirdProps.get("parm1").getValue(), "value1");

        Map<String, InterceptorProperty> emptyParmValProps = interceptorDefs[4].getProperties();
        Assert.assertNotNull(emptyParmValProps);
        Assert.assertEquals(emptyParmValProps.size(), 1);
        Assert.assertNotNull(emptyParmValProps.get("parm1"));
        Assert.assertEquals(emptyParmValProps.get("parm1").getValue(), "");
    }

    /*
     * Some of these should probably be handled more cleanly by the parser, but a few known
     * exception scenarios are presented here just to document current behavior.  In many cases
     * failure in parsing will just be propagated to a definition that will fail later
     * when instantiated.  Should we be failing faster (and with more detail)?
     */
    @Test
    public void testExceptionOrNot() throws Exception {
        PoolProperties props = null;

        String[] exceptionInducingConfigs = {
            "EmptyParmsInterceptor()",
            "WhitespaceParmsInterceptor(   )"
        };
        for (String badConfig : exceptionInducingConfigs) {
            props = new PoolProperties();
            props.setJdbcInterceptors(badConfig);
            try {
                props.getJdbcInterceptorsAsArray();
                Assert.fail("Expected exception.");
            } catch (Exception e) {
                // Expected
            }
        }

        String[] noExceptionButInvalidConfigs = {
            "MalformedParmsInterceptor(=   )",
            "MalformedParmsInterceptor(  =)",
            "MalformedParmsInterceptor(",
            "MalformedParmsInterceptor( ",
            "MalformedParmsInterceptor)",
            "MalformedParmsInterceptor) ",
            "MalformedParmsInterceptor )"
        };
        for (String badConfig : noExceptionButInvalidConfigs) {
            props = new PoolProperties();
            props.setJdbcInterceptors(badConfig);
            try {
                props.getJdbcInterceptorsAsArray();
            } catch (Exception e) {
                Assert.fail("Unexpected exception.");
            }
        }
    }

    @Test
    public void textExtraSemicolonBehavior() {

        // This one DOES get an extra/empty definition
        PoolProperties props = new PoolProperties();
        props.setJdbcInterceptors(";EmptyLeadingSemiInterceptor");
        InterceptorDefinition[] jiDefs = props.getJdbcInterceptorsAsArray();
        Assert.assertNotNull(jiDefs);
        Assert.assertEquals(jiDefs.length, 3);

        // This one does NOT get an extra/empty definition (no trailing whitespace)
        props = new PoolProperties();
        props.setJdbcInterceptors("EmptyTrailingSemiInterceptor;");
        jiDefs = props.getJdbcInterceptorsAsArray();
        Assert.assertNotNull(jiDefs);
        Assert.assertEquals(jiDefs.length, 2);

        // This one DOES get an extra/empty definition (with trailing whitespace)
        props = new PoolProperties();
        props.setJdbcInterceptors("EmptyTrailingSemiInterceptor; ");
        jiDefs = props.getJdbcInterceptorsAsArray();
        Assert.assertNotNull(jiDefs);
        Assert.assertEquals(jiDefs.length, 3);
    }
}