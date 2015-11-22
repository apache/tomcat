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
package org.apache.tomcat.jdbc.pool;


import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class ClassLoaderUtil {
    private static final Log log = LogFactory.getLog(ClassLoaderUtil.class);

    private static final boolean onlyAttemptFirstLoader =
        Boolean.getBoolean("org.apache.tomcat.jdbc.pool.onlyAttemptCurrentClassLoader");

    public static Class<?> loadClass(String className, ClassLoader... classLoaders) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        StringBuilder errorMsg = null;
        for (ClassLoader cl : classLoaders) {
            try {
                if (cl!=null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Attempting to load class["+className+"] from "+cl);
                    }
                    return Class.forName(className, true, cl);
                } else {
                    throw new ClassNotFoundException("Classloader is null");
                }
            } catch (ClassNotFoundException x) {
                last = x;
                if (errorMsg==null) {
                    errorMsg = new StringBuilder();
                } else {
                    errorMsg.append(';');
                }
                errorMsg.append("ClassLoader:");
                errorMsg.append(cl);
            }
            if (onlyAttemptFirstLoader) {
                break;
            }
        }
        throw new ClassNotFoundException("Unable to load class: "+className+" from "+errorMsg, last);
    }



}
