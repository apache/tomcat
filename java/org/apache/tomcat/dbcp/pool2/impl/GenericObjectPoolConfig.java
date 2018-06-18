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
package org.apache.tomcat.dbcp.pool2.impl;

/**
 * A simple "struct" encapsulating the configuration for a
 * {@link GenericObjectPool}.
 *
 * <p>
 * This class is not thread-safe; it is only intended to be used to provide
 * attributes used when creating a pool.
 * </p>
 *
 * @param <T> Type of element pooled.
 * @since 2.0
 */
public class GenericObjectPoolConfig<T> extends BaseObjectPoolConfig<T> {

    /**
     * The default value for the {@code maxTotal} configuration attribute.
     * @see GenericObjectPool#getMaxTotal()
     */
    public static final int DEFAULT_MAX_TOTAL = 8;

    /**
     * The default value for the {@code maxIdle} configuration attribute.
     * @see GenericObjectPool#getMaxIdle()
     */
    public static final int DEFAULT_MAX_IDLE = 8;

    /**
     * The default value for the {@code minIdle} configuration attribute.
     * @see GenericObjectPool#getMinIdle()
     */
    public static final int DEFAULT_MIN_IDLE = 0;


    private int maxTotal = DEFAULT_MAX_TOTAL;

    private int maxIdle = DEFAULT_MAX_IDLE;

    private int minIdle = DEFAULT_MIN_IDLE;

    /**
     * Get the value for the {@code maxTotal} configuration attribute
     * for pools created with this configuration instance.
     *
     * @return  The current setting of {@code maxTotal} for this
     *          configuration instance
     *
     * @see GenericObjectPool#getMaxTotal()
     */
    public int getMaxTotal() {
        return maxTotal;
    }

    /**
     * Set the value for the {@code maxTotal} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param maxTotal The new setting of {@code maxTotal}
     *        for this configuration instance
     *
     * @see GenericObjectPool#setMaxTotal(int)
     */
    public void setMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
    }


    /**
     * Get the value for the {@code maxIdle} configuration attribute
     * for pools created with this configuration instance.
     *
     * @return  The current setting of {@code maxIdle} for this
     *          configuration instance
     *
     * @see GenericObjectPool#getMaxIdle()
     */
    public int getMaxIdle() {
        return maxIdle;
    }

    /**
     * Set the value for the {@code maxIdle} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param maxIdle The new setting of {@code maxIdle}
     *        for this configuration instance
     *
     * @see GenericObjectPool#setMaxIdle(int)
     */
    public void setMaxIdle(final int maxIdle) {
        this.maxIdle = maxIdle;
    }


    /**
     * Get the value for the {@code minIdle} configuration attribute
     * for pools created with this configuration instance.
     *
     * @return  The current setting of {@code minIdle} for this
     *          configuration instance
     *
     * @see GenericObjectPool#getMinIdle()
     */
    public int getMinIdle() {
        return minIdle;
    }

    /**
     * Set the value for the {@code minIdle} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param minIdle The new setting of {@code minIdle}
     *        for this configuration instance
     *
     * @see GenericObjectPool#setMinIdle(int)
     */
    public void setMinIdle(final int minIdle) {
        this.minIdle = minIdle;
    }

    @SuppressWarnings("unchecked")
    @Override
    public GenericObjectPoolConfig<T> clone() {
        try {
            return (GenericObjectPoolConfig<T>) super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError(); // Can't happen
        }
    }

    @Override
    protected void toStringAppendFields(final StringBuilder builder) {
        super.toStringAppendFields(builder);
        builder.append(", maxTotal=");
        builder.append(maxTotal);
        builder.append(", maxIdle=");
        builder.append(maxIdle);
        builder.append(", minIdle=");
        builder.append(minIdle);
    }
}
