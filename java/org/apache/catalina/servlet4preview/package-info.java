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

/**
 * This package provides early access to some of the new features in the Servlet
 * 4.0 API. This package exists in Tomcat 8.5 only. The package will not exist
 * in Tomcat 9 and applications depending on classes in this package will need
 * to be modified before they will work with Tomcat 9. It is intended that any
 * such modifications will be limited to replacing
 * <code>import org.apache.catalina.servlet4preview...</code> with
 * <code>import javax.servlet...</code> and removing casts.
 * <p>
 * This package is not a complete copy of the proposed Servlet 4.0 API. It
 * contains only a sub-set of those classes that are new or modified in Servlet
 * 4.0. Users may request the inclusion of additional Servlet 4.0 API changes in
 * this package via a Bugzilla enhancement request against Tomcat 8.5.
 * <p>
 * The Servlet 4.0 API is a work in progress. The public API of classes in this
 * package may change in incompatible ways - including classes being renamed or
 * deleted - between point releases of Tomcat 8.5.
 */
package org.apache.catalina.servlet4preview;
