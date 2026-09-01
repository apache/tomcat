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

import org.apache.el.parser.Node;

/**
 * Stores the state required for correct evaluation of lambda expressions. Lambda expressions may be nested. Correct
 * evaluation requires knowledge not just of the current lambda expression, but also of any nested and nesting
 * expressions.
 * <p>
 * The sets of nodes for parsed expressions are cached and, as a result, a set of nodes may be being used by multiple
 * concurrent threads. This means any state relating to evaluation cannot be stored in the nodes. State is therefore
 * stored in the {@link EvaluationContext} which is created, used for a single evaluation and then discarded.
 */
public final class LambdaExpressionNestedState {

    private final Node root;

    private int nestingCount = 0;
    private boolean hasFormalParameters = false;

    /**
     * Constructor.
     *
     * @param root The lambda expression node that created this state
     */
    public LambdaExpressionNestedState(Node root) {
        this.root = root;
    }

    /**
     * Returns whether the given node is the node that created this state or a descendant of that node.
     *
     * @param node The node to check
     *
     * @return {@code true} if the given node is the node that created this state or a descendant of that node
     */
    public boolean contains(Node node) {
        for (Node n = node; n != null; n = n.jjtGetParent()) {
            if (n == root) {
                return true;
            }
        }
        return false;
    }

    /**
     * Increments the nesting count for nested lambda expressions.
     */
    public void incrementNestingCount() {
        nestingCount++;
    }

    /**
     * Returns the current nesting count.
     *
     * @return the nesting count
     */
    public int getNestingCount() {
        return nestingCount;
    }

    /**
     * Marks that the current lambda expression has formal parameters.
     */
    public void setHasFormalParameters() {
        hasFormalParameters = true;
    }

    /**
     * Returns whether the current lambda expression has formal parameters.
     *
     * @return {@code true} if the lambda has formal parameters
     */
    public boolean getHasFormalParameters() {
        return hasFormalParameters;
    }
}
