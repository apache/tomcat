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
package org.apache.el.lang;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.FunctionMapper;
import jakarta.el.MethodExpression;
import jakarta.el.ValueExpression;
import jakarta.el.VariableMapper;

import org.apache.el.MethodExpressionImpl;
import org.apache.el.MethodExpressionLiteral;
import org.apache.el.ValueExpressionImpl;
import org.apache.el.parser.AstDeferredExpression;
import org.apache.el.parser.AstDynamicExpression;
import org.apache.el.parser.AstFunction;
import org.apache.el.parser.AstIdentifier;
import org.apache.el.parser.AstLiteralExpression;
import org.apache.el.parser.AstValue;
import org.apache.el.parser.ELParser;
import org.apache.el.parser.Node;
import org.apache.el.parser.NodeVisitor;
import org.apache.el.util.ConcurrentCache;
import org.apache.el.util.MessageFactory;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 */
public final class ExpressionBuilder implements NodeVisitor {

    private static final SynchronizedStack<ELParser> parserCache = new SynchronizedStack<>();

    private static final int CACHE_SIZE;
    private static final String CACHE_SIZE_PROP =
        "org.apache.el.ExpressionBuilder.CACHE_SIZE";

    static {
        String cacheSizeStr;
        if (System.getSecurityManager() == null) {
            cacheSizeStr = System.getProperty(CACHE_SIZE_PROP, "5000");
        } else {
            cacheSizeStr = AccessController.doPrivileged(
                    (PrivilegedAction<String>) () -> System.getProperty(CACHE_SIZE_PROP, "5000"));
        }
        CACHE_SIZE = Integer.parseInt(cacheSizeStr);
    }

    private static final ConcurrentCache<String, Node> expressionCache =
            new ConcurrentCache<>(CACHE_SIZE);

    private FunctionMapper fnMapper;

    private VariableMapper varMapper;

    private final String expression;

    public ExpressionBuilder(String expression, ELContext ctx)
            throws ELException {
        this.expression = expression;

        FunctionMapper ctxFn = ctx.getFunctionMapper();
        VariableMapper ctxVar = ctx.getVariableMapper();

        if (ctxFn != null) {
            this.fnMapper = new FunctionMapperFactory(ctxFn);
        }
        if (ctxVar != null) {
            this.varMapper = new VariableMapperFactory(ctxVar);
        }
    }

    public static final Node createNode(String expr) throws ELException {
        Node n = createNodeInternal(expr);
        return n;
    }

    private static final Node createNodeInternal(String expr)
            throws ELException {
        if (expr == null) {
            throw new ELException(MessageFactory.get("error.null"));
        }

        Node n = expressionCache.get(expr);
        if (n == null) {
            ELParser parser = parserCache.pop();
            try {
                if (parser == null) {
                    parser = new ELParser(new StringReader(expr));
                } else {
                    parser.ReInit(new StringReader(expr));
                }
                n = parser.CompositeExpression();

                // validate composite expression
                int numChildren = n.jjtGetNumChildren();
                if (numChildren == 1) {
                    n = n.jjtGetChild(0);
                } else {
                    Class<?> type = null;
                    Node child = null;
                    for (int i = 0; i < numChildren; i++) {
                        child = n.jjtGetChild(i);
                        if (child instanceof AstLiteralExpression)
                            continue;
                        if (type == null)
                            type = child.getClass();
                        else {
                            if (!type.equals(child.getClass())) {
                                throw new ELException(MessageFactory.get(
                                        "error.mixed", expr));
                            }
                        }
                    }
                }

                if (n instanceof AstDeferredExpression
                        || n instanceof AstDynamicExpression) {
                    n = n.jjtGetChild(0);
                }
                expressionCache.put(expr, n);
            } catch (Exception e) {
                throw new ELException(
                        MessageFactory.get("error.parseFail", expr), e);
            } finally {
                if (parser != null) {
                    parserCache.push(parser);
                }
            }
        }
        return n;
    }

    private void prepare(Node node) throws ELException {
        try {
            node.accept(this);
        } catch (Exception e) {
            if (e instanceof ELException) {
                throw (ELException) e;
            } else {
                throw (new ELException(e));
            }
        }
        if (this.fnMapper instanceof FunctionMapperFactory) {
            this.fnMapper = ((FunctionMapperFactory) this.fnMapper).create();
        }
        if (this.varMapper instanceof VariableMapperFactory) {
            this.varMapper = ((VariableMapperFactory) this.varMapper).create();
        }
    }

    private Node build() throws ELException {
        Node n = createNodeInternal(this.expression);
        this.prepare(n);
        if (n instanceof AstDeferredExpression
                || n instanceof AstDynamicExpression) {
            n = n.jjtGetChild(0);
        }
        return n;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.sun.el.parser.NodeVisitor#visit(com.sun.el.parser.Node)
     */
    @Override
    public void visit(Node node) throws ELException {
        if (node instanceof AstFunction) {

            AstFunction funcNode = (AstFunction) node;

            Method m = null;

            if (this.fnMapper != null) {
                m = fnMapper.resolveFunction(funcNode.getPrefix(), funcNode
                        .getLocalName());
            }

            // References to variables that refer to lambda expressions will be
            // parsed as functions. This is handled at runtime but at this point
            // need to treat it as a variable rather than a function.
            if (m == null && this.varMapper != null &&
                    funcNode.getPrefix().length() == 0) {
                this.varMapper.resolveVariable(funcNode.getLocalName());
                return;
            }

            if (this.fnMapper == null) {
                throw new ELException(MessageFactory.get("error.fnMapper.null"));
            }

            if (m == null) {
                throw new ELException(MessageFactory.get(
                        "error.fnMapper.method", funcNode.getOutputName()));
            }

            int methodParameterCount = m.getParameterTypes().length;
            // AstFunction->MethodParameters->Parameters()
            int inputParameterCount = node.jjtGetChild(0).jjtGetNumChildren();
            if (m.isVarArgs() && inputParameterCount < methodParameterCount - 1 ||
                    !m.isVarArgs() && inputParameterCount != methodParameterCount) {
                throw new ELException(MessageFactory.get(
                        "error.fnMapper.paramcount", funcNode.getOutputName(),
                        "" + methodParameterCount, "" + node.jjtGetChild(0).jjtGetNumChildren()));
            }
        } else if (node instanceof AstIdentifier && this.varMapper != null) {
            String variable = node.getImage();

            // simply capture it
            this.varMapper.resolveVariable(variable);
        }
    }

    public ValueExpression createValueExpression(Class<?> expectedType)
            throws ELException {
        Node n = this.build();
        return new ValueExpressionImpl(this.expression, n, this.fnMapper,
                this.varMapper, expectedType);
    }

    public MethodExpression createMethodExpression(Class<?> expectedReturnType,
            Class<?>[] expectedParamTypes) throws ELException {
        Node n = this.build();
        if (!n.isParametersProvided() && expectedParamTypes == null) {
            throw new NullPointerException(MessageFactory
                    .get("error.method.nullParms"));
        }
        if (n instanceof AstValue || n instanceof AstIdentifier) {
            return new MethodExpressionImpl(expression, n, this.fnMapper,
                    this.varMapper, expectedReturnType, expectedParamTypes);
        } else if (n instanceof AstLiteralExpression) {
            return new MethodExpressionLiteral(expression, expectedReturnType,
                    expectedParamTypes);
        } else {
            throw new ELException(MessageFactory.get("error.invalidMethodExpression", expression));
        }
    }

    /*
     * Copied from org.apache.tomcat.util.collections.SynchronizedStack since
     * we don't want the EL implementation to depend on the JAR where that
     * class resides.
     */
    private static class SynchronizedStack<T> {

        public static final int DEFAULT_SIZE = 128;
        private static final int DEFAULT_LIMIT = -1;

        private int size;
        private final int limit;

        /*
         * Points to the next available object in the stack
         */
        private int index = -1;

        private Object[] stack;


        public SynchronizedStack() {
            this(DEFAULT_SIZE, DEFAULT_LIMIT);
        }

        public SynchronizedStack(int size, int limit) {
            this.size = size;
            this.limit = limit;
            stack = new Object[size];
        }


        public synchronized boolean push(T obj) {
            index++;
            if (index == size) {
                if (limit == -1 || size < limit) {
                    expand();
                } else {
                    index--;
                    return false;
                }
            }
            stack[index] = obj;
            return true;
        }

        @SuppressWarnings("unchecked")
        public synchronized T pop() {
            if (index == -1) {
                return null;
            }
            T result = (T) stack[index];
            stack[index--] = null;
            return result;
        }

        private void expand() {
            int newSize = size * 2;
            if (limit != -1 && newSize > limit) {
                newSize = limit;
            }
            Object[] newStack = new Object[newSize];
            System.arraycopy(stack, 0, newStack, 0, size);
            // This is the only point where garbage is created by throwing away the
            // old array. Note it is only the array, not the contents, that becomes
            // garbage.
            stack = newStack;
            size = newSize;
        }
    }

}
