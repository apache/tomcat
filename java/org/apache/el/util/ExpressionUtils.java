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
package org.apache.el.util;

import java.lang.reflect.Method;

import jakarta.el.FunctionMapper;

import org.apache.el.parser.AstFunction;
import org.apache.el.parser.Node;

/**
 * Compares expression trees using their captured function mappings. Parsed nodes are shared between expressions, so
 * function mappings must be considered separately rather than stored on the nodes.
 */
public final class ExpressionUtils {

    private ExpressionUtils() {
        // Utility class. Hide default constructor.
    }


    /**
     * Compare parsed expressions and the functions they reference.
     *
     * @param first        First expression tree
     * @param firstMapper  First expression's captured function mapper
     * @param second       Second expression tree
     * @param secondMapper Second expression's captured function mapper
     *
     * @return {@code true} if the expressions have identical parsed representations and function mappings
     */
    public static boolean equals(Node first, FunctionMapper firstMapper, Node second, FunctionMapper secondMapper) {
        return first.equals(second) && equalsFunctions(first, firstMapper, second, secondMapper);
    }


    private static boolean equalsFunctions(Node first, FunctionMapper firstMapper, Node second,
            FunctionMapper secondMapper) {
        if (first instanceof AstFunction && second instanceof AstFunction &&
                !getFunction((AstFunction) first, firstMapper)
                        .equals(getFunction((AstFunction) second, secondMapper))) {
            return false;
        }
        for (int i = 0; i < first.jjtGetNumChildren(); i++) {
            if (!equalsFunctions(first.jjtGetChild(i), firstMapper, second.jjtGetChild(i), secondMapper)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Hash a parsed expression and the functions it references.
     *
     * @param node   Expression tree
     * @param mapper Expression's captured function mapper
     *
     * @return Hash consistent with function-aware expression equality
     */
    public static int hashCode(Node node, FunctionMapper mapper) {
        return node.hashCode() + hashFunctions(node, mapper);
    }


    private static int hashFunctions(Node node, FunctionMapper mapper) {
        int result = node instanceof AstFunction ? getFunction((AstFunction) node, mapper).hashCode() : 0;
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            result = 31 * result + hashFunctions(node.jjtGetChild(i), mapper);
        }
        return result;
    }


    private static Object getFunction(AstFunction function, FunctionMapper mapper) {
        Method method = mapper == null ? null : mapper.resolveFunction(function.getPrefix(), function.getLocalName());
        // Unmapped calls (such as lambdas and imported constructors) are resolved at evaluation time. Compare their
        // names so distinct unresolved calls are not treated as aliases.
        return method == null ? function.getOutputName() : method;
    }
}
