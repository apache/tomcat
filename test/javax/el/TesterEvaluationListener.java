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
package javax.el;

import java.util.ArrayList;
import java.util.List;

public class TesterEvaluationListener extends EvaluationListener {

    private final List<Pair> resolvedProperties = new ArrayList<>();


    @Override
    public void propertyResolved(ELContext context, Object base,
            Object property) {
        resolvedProperties.add(new Pair(base, property));
    }


    public List<Pair> getResolvedProperties() {
        return resolvedProperties;
    }

    public static class Pair {
        private final Object base;
        private final Object property;

        public Pair(Object base, Object property) {
            this.base = base;
            this.property = property;
        }

        public Object getBase() {
            return base;
        }

        public Object getProperty() {
            return property;
        }
    }
}
