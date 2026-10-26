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
package org.apache.tomcat.jdbc.pool;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorDefinition;
import org.apache.tomcat.jdbc.pool.PoolProperties.InterceptorProperty;

/**
 * Configuration only, no database and no pool: how the
 * {@code jdbcInterceptors} string is turned into interceptor definitions.
 * The order matters (the trap interceptor is always first) and so does
 * whitespace handling, because the string is what ends up in a resource
 * definition.
 *
 * <p>Ported from the retired legacy
 * {@code TestJdbcInterceptorConfigParsing} (added for bug 54395).</p>
 */
class TestJdbcInterceptorConfigParsing {

    @Test
    void basicNamesAndPropertiesAreParsed() {
        String interceptorConfig = "FirstInterceptor;SecondInterceptor(parm1=value1,parm2=value2)";
        PoolProperties props = new PoolProperties();
        props.setJdbcInterceptors(interceptorConfig);
        InterceptorDefinition[] interceptorDefs = props.getJdbcInterceptorsAsArray();
        Assertions.assertNotNull(interceptorDefs);

        // 3 items because parser automatically inserts TrapException interceptor to front of list
        Assertions.assertEquals(3, interceptorDefs.length);
        Assertions.assertEquals(TrapException.class.getName(), interceptorDefs[0].getClassName());

        Assertions.assertNotNull(interceptorDefs[1]);
        Assertions.assertEquals("FirstInterceptor", interceptorDefs[1].getClassName());
        Assertions.assertNotNull(interceptorDefs[2]);
        Assertions.assertEquals("SecondInterceptor", interceptorDefs[2].getClassName());

        Map<String, InterceptorProperty> secondProps = interceptorDefs[2].getProperties();
        Assertions.assertNotNull(secondProps);
        Assertions.assertEquals(2, secondProps.size());
        Assertions.assertNotNull(secondProps.get("parm1"));
        Assertions.assertEquals("value1", secondProps.get("parm1").getValue());
        Assertions.assertNotNull(secondProps.get("parm2"));
        Assertions.assertEquals("value2", secondProps.get("parm2").getValue());
    }

    @Test
    void whitespaceAndNewlinesAroundNamesAndPropertiesAreIgnored() {
        String interceptorConfig = "FirstInterceptor ; \n" +
                "SecondInterceptor (parm1  = value1 , parm2= value2 ) ; \n\n" +
                "\t org.cyb.ThirdInterceptor(parm1=value1);  \n" +
                "EmptyParmValInterceptor(parm1=  )";
        PoolProperties props = new PoolProperties();
        props.setJdbcInterceptors(interceptorConfig);
        InterceptorDefinition[] interceptorDefs = props.getJdbcInterceptorsAsArray();
        Assertions.assertNotNull(interceptorDefs);

        // 5 items because parser automatically inserts TrapException interceptor to front of list
        Assertions.assertEquals(5, interceptorDefs.length);
        Assertions.assertEquals(TrapException.class.getName(), interceptorDefs[0].getClassName());

        Assertions.assertNotNull(interceptorDefs[1]);
        Assertions.assertEquals("FirstInterceptor", interceptorDefs[1].getClassName());
        Assertions.assertNotNull(interceptorDefs[2]);
        Assertions.assertEquals("SecondInterceptor", interceptorDefs[2].getClassName());
        Assertions.assertNotNull(interceptorDefs[3]);
        Assertions.assertEquals("org.cyb.ThirdInterceptor", interceptorDefs[3].getClassName());

        Map<String, InterceptorProperty> secondProps = interceptorDefs[2].getProperties();
        Assertions.assertNotNull(secondProps);
        Assertions.assertEquals(2, secondProps.size());
        Assertions.assertNotNull(secondProps.get("parm1"));
        Assertions.assertEquals("value1", secondProps.get("parm1").getValue());
        Assertions.assertNotNull(secondProps.get("parm2"));
        // Bug 54395: the value kept its leading space
        Assertions.assertEquals("value2", secondProps.get("parm2").getValue());

        Map<String, InterceptorProperty> thirdProps = interceptorDefs[3].getProperties();
        Assertions.assertNotNull(thirdProps);
        Assertions.assertEquals(1, thirdProps.size());
        Assertions.assertNotNull(thirdProps.get("parm1"));
        Assertions.assertEquals("value1", thirdProps.get("parm1").getValue());

        Map<String, InterceptorProperty> emptyParmValProps = interceptorDefs[4].getProperties();
        Assertions.assertNotNull(emptyParmValProps);
        Assertions.assertEquals(1, emptyParmValProps.size());
        Assertions.assertNotNull(emptyParmValProps.get("parm1"));
        Assertions.assertEquals("", emptyParmValProps.get("parm1").getValue());
    }

    /*
     * Some of these should probably be handled more cleanly by the parser, but a few known
     * exception scenarios are presented here just to document current behavior.  In many cases
     * failure in parsing will just be propagated to a definition that will fail later
     * when instantiated.  Should we be failing faster (and with more detail)?
     */
    @Test
    void someMalformedConfigsFailAtParsingAndSomeOnlyLater() {
        String[] exceptionInducingConfigs = {
            "EmptyParmsInterceptor()",
            "WhitespaceParmsInterceptor(   )"
        };
        for (String badConfig : exceptionInducingConfigs) {
            PoolProperties props = new PoolProperties();
            props.setJdbcInterceptors(badConfig);
            Assertions.assertThrows(Exception.class, props::getJdbcInterceptorsAsArray,
                    "expected a failure for " + badConfig);
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
            PoolProperties props = new PoolProperties();
            props.setJdbcInterceptors(badConfig);
            props.getJdbcInterceptorsAsArray();
        }
    }

    @Test
    void extraSemicolonsProduceAnExtraDefinitionOnlyInSomePositions() {
        // This one DOES get an extra/empty definition
        PoolProperties props = new PoolProperties();
        props.setJdbcInterceptors(";EmptyLeadingSemiInterceptor");
        InterceptorDefinition[] jiDefs = props.getJdbcInterceptorsAsArray();
        Assertions.assertNotNull(jiDefs);
        Assertions.assertEquals(3, jiDefs.length);

        // This one does NOT get an extra/empty definition (no trailing whitespace)
        props = new PoolProperties();
        props.setJdbcInterceptors("EmptyTrailingSemiInterceptor;");
        jiDefs = props.getJdbcInterceptorsAsArray();
        Assertions.assertNotNull(jiDefs);
        Assertions.assertEquals(2, jiDefs.length);

        // This one DOES get an extra/empty definition (with trailing whitespace)
        props = new PoolProperties();
        props.setJdbcInterceptors("EmptyTrailingSemiInterceptor; ");
        jiDefs = props.getJdbcInterceptorsAsArray();
        Assertions.assertNotNull(jiDefs);
        Assertions.assertEquals(3, jiDefs.length);
    }
}
