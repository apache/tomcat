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
package jakarta.servlet.http;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.apache.coyote.http2.Http2TestBase;

/*
 * Split into multiple tests as a single test takes so long it impacts the time
 * of an entire test run.
 */
@RunWith(Parameterized.class)
public class TestHttpServletDoHeadValidWrite1023 extends HttpServletDoHeadBaseTest {

    @Parameterized.Parameters(name = "{index}: {0} {1} {2} {3} {4} {5} {6} {7} {8}")
    public static Collection<Object[]> parameters() {
        Collection<Object[]> baseData = Http2TestBase.data();

        List<Object[]> parameterSets = new ArrayList<>();
        for (Object[] base : baseData) {
            for (Boolean l : booleans) {
                for (Integer buf : BUFFERS) {
                    for (Boolean w : booleans) {
                        for (Integer c1 : COUNTS) {
                            for (ResetType rt : ResetType.values()) {
                                for (Boolean f : booleans) {
                                    parameterSets.add(new Object[] {
                                            base[0], base[1],
                                            l, buf, w, c1, rt, Integer.valueOf(1023), f });
                                }
                            }
                        }
                    }
                }
            }
        }
        return parameterSets;
    }
}
