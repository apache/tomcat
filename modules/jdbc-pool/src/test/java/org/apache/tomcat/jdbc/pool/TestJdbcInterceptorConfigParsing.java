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
     * Empty parenthesis lists are tolerated (they spell "no properties");
     * a property segment without a '=' fails the parse with a named,
     * locating exception instead of the raw StringIndexOutOfBoundsException
     * the parser used to let substring(0,-1) produce. Other malformed
     * shapes still pass the parser and fail later when instantiated.
     */
    @Test
    void emptyParameterListIsTolerated() {
        String[][] tolerated = {
            { "EmptyParmsInterceptor()", "EmptyParmsInterceptor", "0" },
            { "WhitespaceParmsInterceptor(   )", "WhitespaceParmsInterceptor", "0" },
            { "RedundantCommaInterceptor(a=1,)", "RedundantCommaInterceptor", "1" },
            { "LeadingCommaInterceptor(,a=1)", "LeadingCommaInterceptor", "1" },
            { "MiddleCommaInterceptor(a=1,,b=2)", "MiddleCommaInterceptor", "2" },
            { "WhitespaceSegmentInterceptor(a=1, ,b=2)", "WhitespaceSegmentInterceptor", "2" },
        };
        for (String[] entry : tolerated) {
            PoolProperties props = new PoolProperties();
            props.setJdbcInterceptors(entry[0]);
            InterceptorDefinition[] defs = props.getJdbcInterceptorsAsArray();
            Assertions.assertEquals(entry[1], defs[1].getClassName(),
                    entry[0] + " must parse as a named interceptor");
            Assertions.assertEquals(Integer.parseInt(entry[2]),
                    defs[1].getProperties().size(),
                    entry[0] + " must keep only its real properties");
        }
    }

    @Test
    void propertySegmentWithoutEqualsFailsWithANamedException() {
        String[] badConfigs = {
            "MalformedParmsInterceptor(a=1,b)",
            "MalformedParmsInterceptor(just-a-name)",
        };
        for (String badConfig : badConfigs) {
            PoolProperties props = new PoolProperties();
            props.setJdbcInterceptors(badConfig);
            IllegalArgumentException thrown = Assertions.assertThrows(
                    IllegalArgumentException.class, props::getJdbcInterceptorsAsArray,
                    "expected a named failure for " + badConfig);
            Assertions.assertTrue(thrown.getMessage().contains(badConfig),
                    "the failure must name the configuration it came from, was: "
                            + thrown.getMessage());
        }
    }

    @Test
    void otherMalformedConfigsOnlyFailLater() {
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
