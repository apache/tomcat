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
import org.apache.tomcat.util.res.StringManager;

/**
 * Factory for creating {@link GzipOutputFilter} instances.
 * This is the default output filter factory used by Tomcat.
 * <p>
 * Configuration is held as JavaBean properties on this factory.
 * Allowing Digester to set them from server.xml attributes.
 */
public class GzipOutputFilterFactory implements OutputFilterFactory {

    private static final StringManager sm = StringManager.getManager(GzipOutputFilter.class);

    private int level = -1;
    private int bufferSize = GzipOutputFilter.DEFAULT_BUFFER_SIZE;

    public int getLevel() {
        return level;
    }

    /**
     * Set the gzip compression level.
     *
     * @param level The compression level (-1 for default, 1-9 for specific levels)
     *
     * @throws IllegalArgumentException if the level is out of range
     */
    public void setLevel(int level) {
        if (level < -1 || level > 9) {
            throw new IllegalArgumentException(
                sm.getString("gzipOutputFilterFactory.invalidLevel", level));
        }
        this.level = level;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Set the buffer size for gzip compression.
     *
     * @param bufferSize The buffer size in bytes (must be positive)
     *
     * @throws IllegalArgumentException if the buffer size is not positive
     */
    public void setBufferSize(int bufferSize) {
        if (bufferSize < 0) {
            throw new IllegalArgumentException(
                sm.getString("gzipOutputFilterFactory.invalidBufferSize", bufferSize));
        }
        this.bufferSize = bufferSize;
    }

    @Override
    public OutputFilter createFilter() {
        GzipOutputFilter filter = new GzipOutputFilter();

        // Apply configuration from protocol
        filter.setLevel(level);
        filter.setBufferSize(bufferSize);

        return filter;
    }

    @Override
    public String getEncodingName() {
        return "gzip";
    }
}
