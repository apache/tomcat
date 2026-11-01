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
package org.apache.tomcat.jdbc.pool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ClassLoaderUtil}. The load chain must fall through a
 * loader that cannot see the class, and also past a loader that sees a copy
 * which fails to link or initialize, so that a broken copy in an early
 * loader cannot mask a working copy in a later one. When nothing loads, the
 * declared failure type is {@code ClassNotFoundException} regardless of how
 * each individual loader failed, and a fatal {@code VirtualMachineError}
 * from a static initializer propagates unconverted.
 */
class TestClassLoaderUtil {

    /**
     * A loader that stands for a stale or partially deployed jar: the class
     * is visible to it, but attempting to load it fails with the given
     * error, the way an unusable copy's linkage or initialization fails.
     */
    private static final class BrokenCopyLoader extends ClassLoader {

        private final String target;
        private final Error failure;

        BrokenCopyLoader(String target, Error failure) {
            super(ClassLoader.getSystemClassLoader());
            this.target = target;
            this.failure = failure;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (target.equals(name)) {
                throw failure;
            }
            return super.loadClass(name, resolve);
        }
    }

    /**
     * A loader that stands for a security policy refusing the load.
     */
    private static final class DeniedLoader extends ClassLoader {

        private final String target;

        DeniedLoader(String target) {
            super(ClassLoader.getSystemClassLoader());
            this.target = target;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (target.equals(name)) {
                throw new SecurityException("policy denied");
            }
            return super.loadClass(name, resolve);
        }
    }

    /**
     * A copy that is visible to the first loader but throws from its static
     * initializer - the classic stale JDBC driver jar - must not prevent the
     * second loader's working copy from being used.
     */
    @Test
    void brokenCopyInFirstLoaderFallsBackToSecondLoader() throws Exception {
        ClassLoader broken = new BrokenCopyLoader("java.sql.Driver",
                new ExceptionInInitializerError(new RuntimeException("broken stale copy")));
        Class<?> loaded = ClassLoaderUtil.loadClass("java.sql.Driver",
                broken, ClassLoader.getSystemClassLoader());
        Assertions.assertEquals(java.sql.Driver.class, loaded);
    }

    /**
     * A policy refusal from the first loader is likewise not fatal to the
     * chain.
     */
    @Test
    void securityExceptionInFirstLoaderFallsBackToSecondLoader() throws Exception {
        ClassLoader denied = new DeniedLoader("java.sql.Driver");
        Class<?> loaded = ClassLoaderUtil.loadClass("java.sql.Driver",
                denied, ClassLoader.getSystemClassLoader());
        Assertions.assertEquals(java.sql.Driver.class, loaded);
    }

    /**
     * When every loader fails, the caller gets the declared
     * ClassNotFoundException even though no loader actually reported one,
     * with the linkage failure as the cause and named in the message, where
     * the historical not-found format lists only the loaders.
     */
    @Test
    void allLoadersBrokenStillThrowsTheDeclaredContract() {
        ClassLoader broken = new BrokenCopyLoader("java.sql.Driver",
                new ExceptionInInitializerError(new RuntimeException("broken stale copy")));
        ClassNotFoundException cnfe = Assertions.assertThrows(ClassNotFoundException.class,
                () -> ClassLoaderUtil.loadClass("java.sql.Driver", broken));
        Assertions.assertInstanceOf(LinkageError.class, cnfe.getCause());
        Assertions.assertTrue(cnfe.getMessage().contains("ExceptionInInitializerError"),
                () -> "message should name the failure, but was: " + cnfe.getMessage());
    }

    /**
     * A class whose static initializer dies of a VirtualMachineError, used to
     * test the real JVM shapes: the error raw on the first initialization
     * attempt, the unlinked "Could not initialize class" marker on every
     * attempt after that.
     */
    static class FatalInit {

        private static final Object NOT_A_CONSTANT = new Object();

        static {
            // Condition is not a compile-time constant, so javac accepts the
            // initializer; at runtime the fatal error always wins.
            if (NOT_A_CONSTANT != null) {
                throw new OutOfMemoryError("fatal-from-initializer");
            }
        }
    }

    /**
     * A VirtualMachineError that reaches the load attempt itself - a dying
     * JVM - must abort the chain unconverted: the working copy in the second
     * loader must not be reached.
     */
    @Test
    void virtualMachineErrorFromALoaderAbortsTheChain() {
        ClassLoader fatal = new BrokenCopyLoader("java.sql.Driver",
                new OutOfMemoryError("fatal"));
        OutOfMemoryError thrown = Assertions.assertThrows(OutOfMemoryError.class,
                () -> ClassLoaderUtil.loadClass("java.sql.Driver", fatal,
                        ClassLoader.getSystemClassLoader()));
        Assertions.assertEquals("fatal", thrown.getMessage());
    }

    /**
     * A StackOverflowError is recoverable and ExceptionUtils.handleThrowable
     * deliberately swallows it: the attempt is then an ordinary dead copy and
     * the fallback must proceed.
     */
    @Test
    void stackOverflowErrorFallsBackAsADeadCopy() throws Exception {
        ClassLoader overflowed = new BrokenCopyLoader("java.sql.Driver",
                new StackOverflowError());
        Class<?> loaded = ClassLoaderUtil.loadClass("java.sql.Driver",
                overflowed, ClassLoader.getSystemClassLoader());
        Assertions.assertEquals(java.sql.Driver.class, loaded);
    }

    /**
     * The shape the JVM really produces: the first initialization attempt of
     * a class dies of a VirtualMachineError and propagates it unconverted to
     * the caller that made the attempt. Every attempt after that sees the
     * JVM's standing "Could not initialize class" NoClassDefFoundError,
     * which carries no fatal error to rethrow and is indistinguishable from
     * any other dead copy - exactly the case the documented fallback exists
     * for. The later attempt therefore ends in the declared
     * ClassNotFoundException, naming the dead copy.
     */
    @Test
    void virtualMachineErrorPropagatesOnFirstAttemptAndDeadCopyOnLaterOnes() {
        ClassLoader loader = TestClassLoaderUtil.class.getClassLoader();
        String name = FatalInit.class.getName();
        OutOfMemoryError first = Assertions.assertThrows(OutOfMemoryError.class,
                () -> ClassLoaderUtil.loadClass(name, loader, loader));
        Assertions.assertEquals("fatal-from-initializer", first.getMessage());
        ClassNotFoundException later = Assertions.assertThrows(ClassNotFoundException.class,
                () -> ClassLoaderUtil.loadClass(name, loader, loader));
        Assertions.assertTrue(later.getMessage().contains("Could not initialize class"),
                () -> "message should name the dead copy, but was: " + later.getMessage());
    }

    /**
     * Regression: a class no loader can see still ends in a
     * ClassNotFoundException reporting both loaders, with the last loader's
     * not-found failure as the cause.
     */
    @Test
    void notFoundByEveryLoaderEndsInClassNotFound() {
        ClassNotFoundException cnfe = Assertions.assertThrows(ClassNotFoundException.class,
                () -> ClassLoaderUtil.loadClass("com.example.NotThere",
                        ClassLoader.getSystemClassLoader(), ClassLoader.getSystemClassLoader()));
        Assertions.assertInstanceOf(ClassNotFoundException.class, cnfe.getCause());
        Assertions.assertTrue(cnfe.getMessage().contains("com.example.NotThere"),
                () -> "message should name the class, but was: " + cnfe.getMessage());
    }

    /**
     * Regression: a not-found in the first loader falls through to the
     * second, and a null loader element is skipped rather than fatal.
     */
    @Test
    void notFoundAndNullLoadersSkipToTheNextLoader() throws Exception {
        Class<?> loaded = ClassLoaderUtil.loadClass("java.sql.Driver",
                null, ClassLoader.getSystemClassLoader());
        Assertions.assertEquals(java.sql.Driver.class, loaded);
    }

    /**
     * With nothing to attempt, the caller gets the declared
     * ClassNotFoundException naming the missing supply, not an exception
     * formatted around a null.
     */
    @Test
    void withoutAnyClassLoadersTheFailureSaysSo() {
        ClassNotFoundException empty = Assertions.assertThrows(ClassNotFoundException.class,
                () -> ClassLoaderUtil.loadClass("java.sql.Driver"));
        Assertions.assertTrue(empty.getMessage().contains("no class loaders supplied"),
                () -> "message should say nothing was supplied, but was: " + empty.getMessage());
        Assertions.assertThrows(ClassNotFoundException.class,
                () -> ClassLoaderUtil.loadClass("java.sql.Driver", (ClassLoader[]) null));
    }
}
