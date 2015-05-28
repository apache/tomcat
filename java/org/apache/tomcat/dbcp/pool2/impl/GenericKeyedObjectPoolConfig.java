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
 * {@link GenericKeyedObjectPool}.
 *
 * <p>
 * This class is not thread-safe; it is only intended to be used to provide
 * attributes used when creating a pool.
 *
 * @since 2.0
 */
public class GenericKeyedObjectPoolConfig extends BaseObjectPoolConfig {

    /**
     * The default value for the {@code maxTotalPerKey} configuration attribute.
     * @see GenericKeyedObjectPool#getMaxTotalPerKey()
     */
    public static final int DEFAULT_MAX_TOTAL_PER_KEY = 8;

    /**
     * The default value for the {@code maxTotal} configuration attribute.
     * @see GenericKeyedObjectPool#getMaxTotal()
     */
    public static final int DEFAULT_MAX_TOTAL = -1;

    /**
     * The default value for the {@code minIdlePerKey} configuration attribute.
     * @see GenericKeyedObjectPool#getMinIdlePerKey()
     */
    public static final int DEFAULT_MIN_IDLE_PER_KEY = 0;

    /**
     * The default value for the {@code maxIdlePerKey} configuration attribute.
     * @see GenericKeyedObjectPool#getMaxIdlePerKey()
     */
    public static final int DEFAULT_MAX_IDLE_PER_KEY = 8;


    private int minIdlePerKey = DEFAULT_MIN_IDLE_PER_KEY;

    private int maxIdlePerKey = DEFAULT_MAX_IDLE_PER_KEY;

    private int maxTotalPerKey = DEFAULT_MAX_TOTAL_PER_KEY;

    private int maxTotal = DEFAULT_MAX_TOTAL;

    /**
     * Create a new configuration with default settings.
     */
    public GenericKeyedObjectPoolConfig() {
    }

    /**
     * Get the value for the {@code maxTotal} configuration attribute
     * for pools created with this configuration instance.
     *
     * @return  The current setting of {@code maxTotal} for this
     *          configuration instance
     *
     * @see GenericKeyedObjectPool#getMaxTotal()
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
     * @see GenericKeyedObjectPool#setMaxTotal(int)
     */
    public void setMaxTotal(int maxTotal) {
        this.maxTotal = maxTotal;
    }

    /**
     * Get the value for the {@code maxTotalPerKey} configuration attribute
     * for pools created with this configuration instance.
     *
     * @return  The current setting of {@code maxTotalPerKey} for this
     *          configuration instance
     *
     * @see GenericKeyedObjectPool#getMaxTotalPerKey()
     */
    public int getMaxTotalPerKey() {
        return maxTotalPerKey;
    }

    /**
     * Set the value for the {@code maxTotalPerKey} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param maxTotalPerKey The new setting of {@code maxTotalPerKey}
     *        for this configuration instance
     *
     * @see GenericKeyedObjectPool#setMaxTotalPerKey(int)
     */
    public void setMaxTotalPerKey(int maxTotalPerKey) {
        this.maxTotalPerKey = maxTotalPerKey;
    }

    /**
     * Get the value for the {@code minIdlePerKey} configuration attribute
     * for pools created with this configuration instance.
     *
     * @return  The current setting of {@code minIdlePerKey} for this
     *          configuration instance
     *
     * @see GenericKeyedObjectPool#getMinIdlePerKey()
     */
    public int getMinIdlePerKey() {
        return minIdlePerKey;
    }

    /**
     * Set the value for the {@code minIdlePerKey} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param minIdlePerKey The new setting of {@code minIdlePerKey}
     *        for this configuration instance
     *
     * @see GenericKeyedObjectPool#setMinIdlePerKey(int)
     */
    public void setMinIdlePerKey(int minIdlePerKey) {
        this.minIdlePerKey = minIdlePerKey;
    }

    /**
     * Get the value for the {@code maxIdlePerKey} configuration attribute
     * for pools created with this configuration instance.
     *
     * @return  The current setting of {@code maxIdlePerKey} for this
     *          configuration instance
     *
     * @see GenericKeyedObjectPool#getMaxIdlePerKey()
     */
    public int getMaxIdlePerKey() {
        return maxIdlePerKey;
    }

    /**
     * Set the value for the {@code maxIdlePerKey} configuration attribute for
     * pools created with this configuration instance.
     *
     * @param maxIdlePerKey The new setting of {@code maxIdlePerKey}
     *        for this configuration instance
     *
     * @see GenericKeyedObjectPool#setMaxIdlePerKey(int)
     */
    public void setMaxIdlePerKey(int maxIdlePerKey) {
        this.maxIdlePerKey = maxIdlePerKey;
    }

    @Override
    public GenericKeyedObjectPoolConfig clone() {
        try {
            return (GenericKeyedObjectPoolConfig) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // Can't happen
        }
    }
}
