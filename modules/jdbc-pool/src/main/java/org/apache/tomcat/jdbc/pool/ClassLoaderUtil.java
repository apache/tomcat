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
     * The chain continues past a loader that cannot see the class at all, and
     * also past a loader that sees a copy which fails to link or initialize:
     * the name may still resolve to a working copy further along the chain,
     * as when a stale jar sits in the container's class path while the
     * working copy ships elsewhere. A loader that sees a broken copy is
     * recorded in the failure, and the fallback is logged as a warning, so
     * loading the name from a later loader never happens silently. The fatal
     * errors that {@code ExceptionUtils.handleThrowable} propagates - a
     * dying JVM's {@code VirtualMachineError} - abort the chain unconverted;
     * every other failure of a visible copy is a dead copy, including the
     * JVM's standing "Could not initialize class" marker for a class whose
     * initialization already failed.
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
                // ExceptionUtils.handleThrowable() propagates the fatal
                // errors - a VirtualMachineError from a dying JVM - and
                // swallows the recoverable ones; it is the same treatment
                // the container gives a Throwable before acting on it.
                ExceptionUtils.handleThrowable(x);
                // The class is visible to this loader but the copy is broken:
                // it fails to link (class file version, missing dependency),
                // its static initializer throws, or a policy refused it. Only
                // the not-found case used to continue the chain, so a broken
                // copy in the first loader masked a working copy in a later
                // one and a raw Error or RuntimeException escaped to callers
                // that only plan for ClassNotFoundException. Record the
                // failure and attempt the remaining loaders: the JVM's
                // standing "Could not initialize class" marker for a class
                // whose initialization already failed carries no fatal error
                // to rethrow and is as dead a copy as any.
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
     * Record one loader's failure for the summary message of the eventual
     * ClassNotFoundException. A not-found failure (no reason given) keeps the
     * historical format, which is just the loader's identity; a broken-copy
     * failure names it too, so the operator can tell a dead copy from an
     * absent class in the message alone - the failure's own stack trace,
     * carried to the log with the warning, shows its cause.
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
