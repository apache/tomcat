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
package jakarta.el;

import org.junit.Test;

/*
 * This is an absolute performance test. There is no benefit it running it as part of a standard test run so it is
 * excluded due to the name starting Tester...
 */
public class TesterCompositeELResolverPerformance {

    /*
     * https://bz.apache.org/bugzilla/show_bug.cgi?id=68119
     */
    @Test
    public void testConvertToType() throws Exception {
        ELManager manager = new ELManager();
        ELContext context = manager.getELContext();
        ELResolver resolver = context.getELResolver();

        // Warm-up
        doConversion(context, resolver);

        long start = System.nanoTime();
        doConversion(context, resolver);
        long duration = System.nanoTime() - start;

        System.out.println("convertToType performance test complete in " + duration + "ns");
    }


    private void doConversion(ELContext context, ELResolver resolver) {
        for (int i = 0; i < 10000000; i++) {
            resolver.convertToType(context, "This is a String", String.class);
        }
    }
}
