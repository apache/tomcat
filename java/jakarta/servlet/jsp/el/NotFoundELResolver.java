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
package jakarta.servlet.jsp.el;

import java.util.Objects;
import java.util.ResourceBundle;

import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.PropertyNotFoundException;

/**
 * The final resolver of the Jakarta Server Pages ELResolver chain. It always
 * resolves the requested value, returning {@code null} when it does so.
 *
 * @since JSP 3.1
 */
public class NotFoundELResolver extends ELResolver {

    private static final String LSTRING_FILE = "jakarta.servlet.jsp.LocalStrings";
    private static final ResourceBundle lStrings = ResourceBundle.getBundle(LSTRING_FILE);

    /**
     * Default constructor.
     */
    public NotFoundELResolver() {
        super();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Resolves the property and always returns {@code null} unless the provided
     * context contains a Boolean object with value {@code Boolean.TRUE} as the
     * value associated with the key
     * {@code jakarta.servlet.jsp.el.NotFoundELResolver.class} in which case an
     * exception is thrown. This is to support implementation of the
     * {@code errorOnELNotFound} page/tag directive.
     *
     * @return Always {@code null}
     *
     * @throws PropertyNotFoundException if the provided context contains a
     *         Boolean object with value {@code Boolean.TRUE} as the value
     *         associated with the key
     *         {@code jakarta.servlet.jsp.el.NotFoundELResolver.class}
     */
    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);

        Object obj = context.getContext(this.getClass());
        if (obj instanceof Boolean && ((Boolean) obj).booleanValue()) {
            throw new PropertyNotFoundException(
                    lStrings.getString("el.unknown.identifier") + " [" + property.toString() + "]");
        }

        context.setPropertyResolved(base, property);
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * In normal usage, {@link ScopedAttributeELResolver} will have responded.
     *
     * @return Always {@code null}
     */
    @Override
    public Class<Object> getType(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * No-op. In normal usage, {@link ScopedAttributeELResolver} will have
     * responded.
     */
    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        Objects.requireNonNull(context);
    }


    /**
     * {@inheritDoc}
     * <p>
     * In normal usage, {@link ScopedAttributeELResolver} will have responded.
     *
     * @return Always {@code false}
     */
    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        Objects.requireNonNull(context);
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * In normal usage, {@link ScopedAttributeELResolver} will have responded.
     *
     * @return Always {@code null}
     */
    @Override
    public Class<String> getCommonPropertyType(ELContext context, Object base) {
        return null;
    }
}
