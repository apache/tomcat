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

package org.apache.tomcat.lite;

import javax.servlet.ServletContext;

/**
 * Tomcat-lite specific interface ( could be moved to addons ).
 * This class will be called before initialization - implementations
 * can add servlets, filters, etc. In particular web.xml parsing
 * is done implementing this interface. 
 * 
 * On a small server you could remove web.xml support to reduce 
 * footprint, and either hardcode this class or use properties.
 * Same if you already use a framework and you inject settings
 * or use framework's registry (OSGI).
 * 
 * @author Costin Manolache
 */
public interface ContextPreinitListener {

    public void preInit(ServletContext ctx);
}
