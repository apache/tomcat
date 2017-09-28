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
package org.apache.catalina.servlet4preview;

/**
 * @deprecated This class is not included in Tomcat 9 onwards. Users of this
 *             class should normally upgrade to Tomcat 9 and switch to the
 *             Servlet 4.0 API. If the functionality is required in Tomcat 8.5,
 *             then the Tomcat implementation classes should be used directly.
 *             This class may be removed from Tomcat 8.5.x some time after 30
 *             September 2018.
 */
@Deprecated
public interface AsyncContext extends javax.servlet.AsyncContext {

    public static final String ASYNC_MAPPING = "javax.servlet.async.mapping";
}
