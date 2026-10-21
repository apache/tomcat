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
package org.apache.catalina.storeconfig;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class TestStoreFactoryBase {

    @Test
    public void testStoreElementArrayPropagatesIOExceptionAfterRemainingElements() {
        Object failingElement = new Object();
        Object remainingElement = new Object();
        IOException failure = new IOException("Test failure");
        List<Object> attemptedElements = new ArrayList<>();
        StoreFactoryBase factory = new StoreFactoryBase() {

            @Override
            protected void storeElement(PrintWriter writer, int indent, Object element) throws Exception {
                attemptedElements.add(element);
                if (element == failingElement) {
                    throw failure;
                }
            }
        };

        IOException actual = Assert.assertThrows(IOException.class,
                () -> factory.storeElementArray(null, 0, new Object[] { failingElement, remainingElement }));

        Assert.assertSame(failure, actual);
        Assert.assertEquals(Arrays.asList(failingElement, remainingElement), attemptedElements);
    }
}
