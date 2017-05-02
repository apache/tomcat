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
package org.apache.tomcat.util.digester;

/**
 *
 * A collection of interfaces, one per property, that enables the object being
 * populated by the digester to signal to the digester that it supports the
 * given property and that the digester should populate that property if
 * available.
 */
public interface DocumentProperties {

    /**
     * The character encoding used by the source XML document.
     */
    public interface Charset {
        public void setCharset(java.nio.charset.Charset charset);
    }
}
