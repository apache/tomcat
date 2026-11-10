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
import org.apache.tomcat.jdbc.pool.util.ExceptionUtils;
import org.apache.tomcat.jdbc.pool.util.MessageFactory;

/**
 * Utility class for loading classes using multiple class loaders.
 */
public class ClassLoaderUtil {
    private static final Log log = LogFactory.getLog(ClassLoaderUtil.class);

    static {
        // handleThrowable() first runs inside a catch block below; load and
        // initialize the class up front so a failure to load it cannot mask
        // the error it is called to handle.
        ExceptionUtils.preload();
    }

    /**
     * The kill switch is sampled once at class initialization: setting the
     * property after the pool classes have loaded has no effect.
     */
    private static final boolean onlyAttemptFirstLoader = Boolean
            .getBoolean("org.apache.tomcat.jdbc.pool.onlyAttemptCurrentClassLoader");

    /**
     * Private constructor to prevent instantiation.
     */
    private ClassLoaderUtil() {
    }

    /**
     * Attempts to load the specified class using the given class loaders in order.
     * <p>
     * A loader that cannot see the class, or that sees a copy which fails to
     * link or initialize, does not end the search: the next loader is tried
     * and the fallback is logged. Fatal errors propagated by
     * {@code ExceptionUtils.handleThrowable} abort the chain.
     *
     * @param className    the fully qualified class name
     * @param classLoaders the class loaders to attempt, in order
     *
     * @return the loaded class
     *
     * @throws ClassNotFoundException if no class loader was supplied, or none
     *         of them could load the class; the failure of the last loader
     *         attempted is the cause
     */
    public static Class<?> loadClass(String className, ClassLoader... classLoaders) throws ClassNotFoundException {
        if (classLoaders == null || classLoaders.length == 0) {
            throw new ClassNotFoundException(
                    MessageFactory.get("classLoaderUtil.classLoadersNone", className));
        }
        Throwable last = null;
        StringBuilder errorMsg = null;
        for (ClassLoader cl : classLoaders) {
            try {
                if (cl != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Attempting to load class[" + className + "] from " + cl);
                    }
                    return Class.forName(className, true, cl);
                } else {
                    throw new ClassNotFoundException(MessageFactory.get("classLoaderUtil.classLoaderNull"));
                }
            } catch (ClassNotFoundException x) {
                last = x;
                errorMsg = appendFailure(errorMsg, cl, null);
            } catch (Throwable x) {
                // A visible but broken copy does not end the search: record
                // the failure and try the remaining loaders.
                ExceptionUtils.handleThrowable(x);
                last = x;
                errorMsg = appendFailure(errorMsg, cl, x);
                log.warn(MessageFactory.get("classLoaderUtil.classLoadError", className, cl), x);
            }
            if (onlyAttemptFirstLoader) {
                break;
            }
        }
        throw new ClassNotFoundException(MessageFactory.get("classLoaderUtil.classLoadFailed", className, errorMsg),
                last);
    }

    /**
     * Records one loader's failure for the summary message of the eventual
     * ClassNotFoundException.
     *
     * @param errorMsg The summary built so far, or null
     * @param cl The loader that failed
     * @param failure The failure if the class was visible but broken, or null
     *
     * @return The updated summary
     */
    private static StringBuilder appendFailure(StringBuilder errorMsg, ClassLoader cl, Throwable failure) {
        if (errorMsg == null) {
            errorMsg = new StringBuilder();
        } else {
            errorMsg.append(';');
        }
        errorMsg.append("ClassLoader:");
        errorMsg.append(cl);
        if (failure != null) {
            errorMsg.append(" -> ").append(failure);
        }
        return errorMsg;
    }
}
