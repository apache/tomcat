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
package org.apache.el.parser;

import jakarta.el.ELProcessor;

import org.junit.Assert;
import org.junit.Test;

public class TestAstIdentifier {

    @Test
    public void testImport01() {
        ELProcessor processor = new ELProcessor();
        Object result =
                processor.getValue("Integer.MAX_VALUE",
                        Integer.class);
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), result);
    }


    @Test
    public void testImport02() {
        ELProcessor processor = new ELProcessor();
        processor.getELManager().getELContext().getImportHandler().importStatic(
                "java.lang.Integer.MAX_VALUE");
        Object result =
                processor.getValue("MAX_VALUE",
                        Integer.class);
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), result);
    }
}
