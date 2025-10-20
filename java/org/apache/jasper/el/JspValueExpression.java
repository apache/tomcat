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
package org.apache.jasper.el;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import jakarta.el.ELContext;
import jakarta.el.ELException;
import jakarta.el.PropertyNotFoundException;
import jakarta.el.PropertyNotWritableException;
import jakarta.el.ValueExpression;

/**
 * Wrapper for providing context to ValueExpressions.
 */
public final class JspValueExpression extends ValueExpression implements Externalizable {

    private ValueExpression target;

    private String mark;

    public JspValueExpression() {
        super();
    }

    public JspValueExpression(String mark, ValueExpression target) {
        this.target = target;
        this.mark = mark;
    }

    @Override
    public Class<?> getExpectedType() {
        return this.target.getExpectedType();
    }

    @Override
    public Class<?> getType(ELContext context) throws NullPointerException, PropertyNotFoundException, ELException {
        context.notifyBeforeEvaluation(getExpressionString());
        try {
            Class<?> result = this.target.getType(context);
            context.notifyAfterEvaluation(getExpressionString());
            return result;
        } catch (PropertyNotFoundException e) {
            if (e instanceof JspPropertyNotFoundException) {
                throw e;
            }
            throw new JspPropertyNotFoundException(this.mark, e);
        } catch (ELException e) {
            if (e instanceof JspELException) {
                throw e;
            }
            throw new JspELException(this.mark, e);
        }
    }

    @Override
    public boolean isReadOnly(ELContext context) throws NullPointerException, PropertyNotFoundException, ELException {
        context.notifyBeforeEvaluation(getExpressionString());
        try {
            boolean result = this.target.isReadOnly(context);
            context.notifyAfterEvaluation(getExpressionString());
            return result;
        } catch (PropertyNotFoundException e) {
            if (e instanceof JspPropertyNotFoundException) {
                throw e;
            }
            throw new JspPropertyNotFoundException(this.mark, e);
        } catch (ELException e) {
            if (e instanceof JspELException) {
                throw e;
            }
            throw new JspELException(this.mark, e);
        }
    }

    @Override
    public void setValue(ELContext context, Object value)
            throws NullPointerException, PropertyNotFoundException, PropertyNotWritableException, ELException {
        context.notifyBeforeEvaluation(getExpressionString());
        try {
            this.target.setValue(context, value);
            context.notifyAfterEvaluation(getExpressionString());
        } catch (PropertyNotWritableException e) {
            if (e instanceof JspPropertyNotWritableException) {
                throw e;
            }
            throw new JspPropertyNotWritableException(this.mark, e);
        } catch (PropertyNotFoundException e) {
            if (e instanceof JspPropertyNotFoundException) {
                throw e;
            }
            throw new JspPropertyNotFoundException(this.mark, e);
        } catch (ELException e) {
            if (e instanceof JspELException) {
                throw e;
            }
            throw new JspELException(this.mark, e);
        }
    }

    @Override
    public <T> T getValue(ELContext context) throws NullPointerException, PropertyNotFoundException, ELException {
        context.notifyBeforeEvaluation(getExpressionString());
        try {
            T result = this.target.getValue(context);
            context.notifyAfterEvaluation(getExpressionString());
            return result;
        } catch (PropertyNotFoundException e) {
            if (e instanceof JspPropertyNotFoundException) {
                throw e;
            }
            throw new JspPropertyNotFoundException(this.mark, e);
        } catch (ELException e) {
            if (e instanceof JspELException) {
                throw e;
            }
            throw new JspELException(this.mark, e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return this.target.equals(obj);
    }

    @Override
    public int hashCode() {
        return this.target.hashCode();
    }

    @Override
    public String getExpressionString() {
        return this.target.getExpressionString();
    }

    @Override
    public boolean isLiteralText() {
        return this.target.isLiteralText();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(this.mark);
        out.writeObject(this.target);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.mark = in.readUTF();
        this.target = (ValueExpression) in.readObject();
    }
}
