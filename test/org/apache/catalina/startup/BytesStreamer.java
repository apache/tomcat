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
package org.apache.catalina.startup;

/**
 *
 * Used by {@link TomcatBaseTest}
 *
 *
 */
public interface BytesStreamer {
    /**
     * Get the length of the content about to be streamed.
     *
     * @return the length if known, else -1 and chucked encoding should be used
     */
    int getLength();

    /**
     * @return  the number of bytes available in next chunk
     */
    int available();

    /**
     * @return  returns the next byte to write if {@link #available()} returns
     *          > 0
     */
    byte[] next();
}
