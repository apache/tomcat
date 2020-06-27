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
package jakarta.websocket;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

import org.junit.Test;

import org.apache.tomcat.unittest.TesterThreadedPerformance;

public class TesterContainerProviderPerformance {

    @Test
    public void testGetWebSocketContainer() throws Exception {
        for (int i = 1; i < 9; i++) {
            TesterThreadedPerformance test =
                    new TesterThreadedPerformance(i, 250000, new TestInstanceSupplier());
            long duration = test.doTest();
            System.out.println(i + " threads completed in " + duration + "ns");
        }
    }


    private static class TestInstanceSupplier implements Supplier<IntConsumer> {

        @Override
        public IntConsumer get() {
            return new TestInstance();
        }
    }


    private static class TestInstance implements IntConsumer {

        @Override
        public void accept(int value) {
            ContainerProvider.getWebSocketContainer();
        }
    }
}
