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
package org.apache.catalina.tribes.group.interceptors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestGzipInterceptor {

    @Parameters(name = "{index}: bufferSize[{0}]")
    public static Collection<Object[]> inputs() {
        List<Object[]> result = new ArrayList<>();
        result.add(new Object[] { Integer.valueOf(GzipInterceptor.DEFAULT_BUFFER_SIZE / 2) });
        result.add(new Object[] { Integer.valueOf(GzipInterceptor.DEFAULT_BUFFER_SIZE - 1) });
        result.add(new Object[] { Integer.valueOf(GzipInterceptor.DEFAULT_BUFFER_SIZE) });
        result.add(new Object[] { Integer.valueOf(GzipInterceptor.DEFAULT_BUFFER_SIZE + 1) });
        result.add(new Object[] { Integer.valueOf(GzipInterceptor.DEFAULT_BUFFER_SIZE * 2) });
        result.add(new Object[] { Integer.valueOf(GzipInterceptor.DEFAULT_BUFFER_SIZE * 4) });
        result.add(new Object[] { Integer.valueOf(GzipInterceptor.DEFAULT_BUFFER_SIZE * 10 + 1000) });
        return result;
    }

    @Parameter(0)
    public int bufferSize;

    @Test
    public void testCompressDecompress() throws Exception {
        byte[] data = new byte[bufferSize];
        Arrays.fill(data, (byte)1);
        byte[] compress = GzipInterceptor.compress(data);
        byte[] result = GzipInterceptor.decompress(compress);
        Assert.assertTrue(Arrays.equals(data, result));
    }
}
