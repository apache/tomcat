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
package org.apache.catalina.ant.jmx;

import org.junit.Assert;
import org.junit.Test;

import org.apache.tools.ant.BuildException;

public class TestJMXAccessorCondition {

    @Test
    public void testEvalUnsupportedOperation() {
        JMXAccessorCondition condition = createCondition();
        condition.setOperation(">>=");

        try {
            condition.eval();
            Assert.fail("Expected a BuildException for an unsupported operation");
        } catch (BuildException expected) {
            // Expected
        }
    }


    @Test
    public void testEvalUnsupportedTypeForRelationalOperation() {
        JMXAccessorCondition condition = createCondition();
        condition.setOperation("<");
        condition.setType("string");

        try {
            condition.eval();
            Assert.fail("Expected a BuildException for an unsupported type");
        } catch (BuildException expected) {
            // Expected
        }
    }


    private static JMXAccessorCondition createCondition() {
        JMXAccessorCondition condition = new JMXAccessorCondition();
        condition.setName("Catalina:type=Test");
        condition.setAttribute("startupTime");
        condition.setValue("250");
        return condition;
    }
}
