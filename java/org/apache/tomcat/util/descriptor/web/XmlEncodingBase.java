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
package org.apache.tomcat.util.descriptor.web;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Base class for those elements that need to track the encoding used in the
 * source XML.
 */
public abstract class XmlEncodingBase {

    private Charset charset = StandardCharsets.UTF_8;


    public void setCharset(Charset charset) {
        this.charset = charset;
    }


    /**
     * Obtain the character encoding of the XML source that was used to
     * populated this object.
     *
     * @return The character encoding of the associated XML source or
     *         <code>UTF-8</code> if the encoding could not be determined
     */
    public Charset getCharset() {
        return charset;
    }
}
