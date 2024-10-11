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

package org.apache.catalina.util;

import java.util.concurrent.atomic.LongAdder;

public interface Counter {
    void increment();
    long get();

    static Counter of(boolean enabled) {
        return enabled ? new Simple() : Noop.INSTANCE;
    }

    class Noop implements Counter {
        private static final Counter INSTANCE = new Noop();

        @Override
        public void increment() {
            // no-op
        }

        @Override
        public long get() {
            return 0;
        }
    }

    class Simple implements Counter {
        private final LongAdder adder = new LongAdder();

        @Override
        public void increment() {
            adder.increment();
        }

        @Override
        public long get() {
            return adder.sum();
        }
    }
}
