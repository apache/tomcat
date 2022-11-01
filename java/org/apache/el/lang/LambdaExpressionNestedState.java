/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.el.lang;

/**
 * Stores the state required for correct evaluation of lambda expressions.
 * Lambda expressions may be nested. Correct evaluation requires knowledge not
 * just of the current lambda expression, but also of any nested and nesting
 * expressions.
 * <p>
 * The sets of nodes for parsed expressions are cached and, as a result, a set
 * of nodes may be being used by multiple concurrent threads. This means any
 * state relating to evaluation cannot be stored in the nodes. State is
 * therefore stored in the {@link EvaluationContext} which is created, used for
 * a single evaluation and then discarded.
 */
public final class LambdaExpressionNestedState {

    private int nestingCount = 0;
    private boolean hasFormalParameters = false;

    public void incrementNestingCount() {
        nestingCount++;
    }

    public int getNestingCount() {
        return nestingCount;
    }

    public void setHasFormalParameters() {
        hasFormalParameters = true;
    }

    public boolean getHasFormalParameters() {
        return hasFormalParameters;
    }
}
