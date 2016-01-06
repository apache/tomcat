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

/**
 * @deprecated Originally provided an optional implementation that used Java 5+
 *             features. Now the minimum Java version is >=5, those features
 *             have been added to {@link MessageDispatchInterceptor} which
 *             should be used instead. This class will be removed in Tomcat
 *             9.0.x onwards.
 */
@Deprecated
public class MessageDispatch15Interceptor extends MessageDispatchInterceptor {
}