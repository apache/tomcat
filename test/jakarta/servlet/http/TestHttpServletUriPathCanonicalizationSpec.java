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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestHttpServletUriPathCanonicalizationSpec extends HttpServletUriPathCanonicalizationBaseTest {

    @Parameterized.Parameters(name = "{index}: {1} {2} {3}")
    public static Collection<Object[]> parameters() throws IOException {
        // Servlet 6.1 spec, section 3.5.3 examples
        List<Object[]> parameterSets = new ArrayList<>();
        try (InputStream is = new FileInputStream("test/conf/TestHttpServletUriPathCanonicalizationSpec.txt")) {

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            List<String> lineList = br.lines().collect(Collectors.toList());
            String[] lines = lineList.toArray(new String[0]);

            for (int i = 1; i < lines.length; i++) {
                if(lines[i].startsWith("#")) {
                    continue;
                }
                String[] cols = lines[i].split(" ");
                String caseIndex = cols[0];
                if(Arrays.binarySearch(new String[] {"19","34"}, caseIndex)>=0) {
                    continue;
                }
                String srcUri = cols[1];
                String destUri = "/";
                Integer sc = null;

                if (cols.length >= 3) {
                    StringBuffer destBuf = new StringBuffer(cols[2]);
                    for (int c = 3; c < cols.length; c++) {
                        try {
                            sc = Integer.valueOf(cols[c]);
                            break;
                        } catch (Exception e) {
                            destBuf.append(" ").append(cols[c]);
                        }
                    }
                    destUri = destBuf.toString();
                }
                parameterSets.add(new Object[] { caseIndex, srcUri, destUri, sc });
            }
        }
        return parameterSets;
    }
}
