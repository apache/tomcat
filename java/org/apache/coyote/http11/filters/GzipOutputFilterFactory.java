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

package org.apache.coyote.http11.filters;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.coyote.http11.OutputFilter;

/**
 * Factory for creating GzipOutputFilter instances with protocol configuration.
 * This is the default output filter factory used by Tomcat.
 */
public class GzipOutputFilterFactory implements OutputFilterFactory{

    @Override
    public OutputFilter createFilter(AbstractHttp11Protocol<?> protocol) {
        GzipOutputFilter filter = new GzipOutputFilter();

        // Apply configuration from protocol
        filter.setLevel(protocol.getGzipLevel());
        filter.setBufferSize(protocol.getGzipBufferSize());

        return filter;
    }

    @Override
    public String getEncodingName() {
        return "gzip";
    }
}
