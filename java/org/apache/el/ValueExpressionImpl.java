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
package org.apache.el;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.FunctionMapper;
import jakarta.el.PropertyNotFoundException;
import jakarta.el.PropertyNotWritableException;
import jakarta.el.ValueExpression;
import jakarta.el.ValueReference;
import jakarta.el.VariableMapper;

import org.apache.el.lang.EvaluationContext;
import org.apache.el.lang.ExpressionBuilder;
import org.apache.el.parser.AstLiteralExpression;
import org.apache.el.parser.Node;
import org.apache.el.util.ReflectionUtil;


/**
 * An <code>Expression</code> that can get or set a value.
 *
 * <p>
 * In previous incarnations of this API, expressions could only be read.
 * <code>ValueExpression</code> objects can now be used both to retrieve a
 * value and to set a value. Expressions that can have a value set on them are
 * referred to as l-value expressions. Those that cannot are referred to as
 * r-value expressions. Not all r-value expressions can be used as l-value
 * expressions (e.g. <code>"${1+1}"</code> or
 * <code>"${firstName} ${lastName}"</code>). See the EL Specification for
 * details. Expressions that cannot be used as l-values must always return
 * <code>true</code> from <code>isReadOnly()</code>.
 * </p>
 *
 * <p>
 * The {@link jakarta.el.ExpressionFactory#createValueExpression} method
 * can be used to parse an expression string and return a concrete instance
 * of <code>ValueExpression</code> that encapsulates the parsed expression.
 * The {@link FunctionMapper} is used at parse time, not evaluation time,
 * so one is not needed to evaluate an expression using this class.
 * However, the {@link ELContext} is needed at evaluation time.</p>
 *
 * <p>The {@link #getValue}, {@link #setValue}, {@link #isReadOnly} and
 * {@link #getType} methods will evaluate the expression each time they are
 * called. The {@link jakarta.el.ELResolver} in the <code>ELContext</code> is used
 * to resolve the top-level variables and to determine the behavior of the
 * <code>.</code> and <code>[]</code> operators. For any of the four methods,
 * the {@link jakarta.el.ELResolver#getValue} method is used to resolve all
 * properties up to but excluding the last one. This provides the
 * <code>base</code> object. At the last resolution, the
 * <code>ValueExpression</code> will call the corresponding
 * {@link jakarta.el.ELResolver#getValue}, {@link jakarta.el.ELResolver#setValue},
 * {@link jakarta.el.ELResolver#isReadOnly} or {@link jakarta.el.ELResolver#getType}
 * method, depending on which was called on the <code>ValueExpression</code>.
 * </p>
 *
 * <p>See the notes about comparison, serialization and immutability in
 * the {@link jakarta.el.Expression} javadocs.
 *
 * @see jakarta.el.ELResolver
 * @see jakarta.el.Expression
 * @see jakarta.el.ExpressionFactory
 * @see jakarta.el.ValueExpression
 *
 * @author Jacob Hookom [jacob@hookom.net]
 */
public final class ValueExpressionImpl extends ValueExpression implements
        Externalizable {

    private Class<?> expectedType;

    private String expr;

    private FunctionMapper fnMapper;

    private VariableMapper varMapper;

    private transient Node node;

    public ValueExpressionImpl() {
        super();
    }

    public ValueExpressionImpl(String expr, Node node, FunctionMapper fnMapper,
            VariableMapper varMapper, Class<?> expectedType) {
        this.expr = expr;
        this.node = node;
        this.fnMapper = fnMapper;
        this.varMapper = varMapper;
        this.expectedType = expectedType;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ValueExpressionImpl)) {
            return false;
        }
        if (obj.hashCode() != this.hashCode()) {
            return false;
        }

        return this.getNode().equals(((ValueExpressionImpl) obj).getNode());
    }

    @Override
    public Class<?> getExpectedType() {
        return this.expectedType;
    }

    /**
     * Returns the type the result of the expression will be coerced to after
     * evaluation.
     *
     * @return the <code>expectedType</code> passed to the
     *         <code>ExpressionFactory.createValueExpression</code> method
     *         that created this <code>ValueExpression</code>.
     *
     * @see jakarta.el.Expression#getExpressionString()
     */
    @Override
    public String getExpressionString() {
        return this.expr;
    }

    private Node getNode() throws ELException {
        if (this.node == null) {
            this.node = ExpressionBuilder.createNode(this.expr);
        }
        return this.node;
    }

    @Override
    public Class<?> getType(ELContext context) throws PropertyNotFoundException,
            ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        Class<?> result = this.getNode().getType(ctx);
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValue(ELContext context) throws PropertyNotFoundException,
            ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        Object value = this.getNode().getValue(ctx);
        if (this.expectedType != null) {
            value = context.convertToType(value, this.expectedType);
        }
        context.notifyAfterEvaluation(getExpressionString());
        return (T) value;
    }

    @Override
    public int hashCode() {
        return this.getNode().hashCode();
    }

    @Override
    public boolean isLiteralText() {
        try {
            return this.getNode() instanceof AstLiteralExpression;
        } catch (ELException ele) {
            return false;
        }
    }

    @Override
    public boolean isReadOnly(ELContext context)
            throws PropertyNotFoundException, ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        boolean result = this.getNode().isReadOnly(ctx);
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        this.expr = in.readUTF();
        String type = in.readUTF();
        if (!type.isEmpty()) {
            this.expectedType = ReflectionUtil.forName(type);
        }
        this.fnMapper = (FunctionMapper) in.readObject();
        this.varMapper = (VariableMapper) in.readObject();
    }

    @Override
    public void setValue(ELContext context, Object value)
            throws PropertyNotFoundException, PropertyNotWritableException,
            ELException {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        this.getNode().setValue(ctx, value);
        context.notifyAfterEvaluation(getExpressionString());
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(this.expr);
        out.writeUTF((this.expectedType != null) ? this.expectedType.getName()
                : "");
        out.writeObject(this.fnMapper);
        out.writeObject(this.varMapper);
    }

    @Override
    public String toString() {
        return "ValueExpression["+this.expr+"]";
    }

    @Override
    public ValueReference getValueReference(ELContext context) {
        EvaluationContext ctx = new EvaluationContext(context, this.fnMapper,
                this.varMapper);
        context.notifyBeforeEvaluation(getExpressionString());
        ValueReference result = this.getNode().getValueReference(ctx);
        context.notifyAfterEvaluation(getExpressionString());
        return result;
    }
}
