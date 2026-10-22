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
package org.apache.juli.logging;

/*
 * Service provider for the tests of LogFactory that offers only the no-argument
 * constructor required by the ServiceLoader itself, but not the constructor
 * taking the log name, so LogFactory must ignore it.
 */
public class TesterLogNoStringConstructor implements Log {

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public boolean isErrorEnabled() {
        return false;
    }

    @Override
    public boolean isFatalEnabled() {
        return false;
    }

    @Override
    public boolean isInfoEnabled() {
        return false;
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public boolean isWarnEnabled() {
        return false;
    }

    @Override
    public void trace(Object message) {
        // NO-OP
    }

    @Override
    public void trace(Object message, Throwable t) {
        // NO-OP
    }

    @Override
    public void debug(Object message) {
        // NO-OP
    }

    @Override
    public void debug(Object message, Throwable t) {
        // NO-OP
    }

    @Override
    public void info(Object message) {
        // NO-OP
    }

    @Override
    public void info(Object message, Throwable t) {
        // NO-OP
    }

    @Override
    public void warn(Object message) {
        // NO-OP
    }

    @Override
    public void warn(Object message, Throwable t) {
        // NO-OP
    }

    @Override
    public void error(Object message) {
        // NO-OP
    }

    @Override
    public void error(Object message, Throwable t) {
        // NO-OP
    }

    @Override
    public void fatal(Object message) {
        // NO-OP
    }

    @Override
    public void fatal(Object message, Throwable t) {
        // NO-OP
    }
}
