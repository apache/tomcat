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
package org.apache.tomcat.util.modeler;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;

/**
 * <p>Internal configuration information for an <code>Operation</code>
 * descriptor.</p>
 *
 * @author Craig R. McClanahan
 */
public class OperationInfo extends FeatureInfo {

    private static final long serialVersionUID = 4418342922072614875L;

    // ----------------------------------------------------------- Constructors

    /**
     * Standard zero-arguments constructor.
     */
    public OperationInfo() {
        super();
    }


    // ----------------------------------------------------- Instance Variables

    protected String impact = "UNKNOWN";
    protected String role = "operation";
    protected final ReadWriteLock parametersLock = new ReentrantReadWriteLock();
    protected ParameterInfo parameters[] = new ParameterInfo[0];


    // ------------------------------------------------------------- Properties

    /**
     * @return the "impact" of this operation, which should be
     *  a (case-insensitive) string value "ACTION", "ACTION_INFO",
     *  "INFO", or "UNKNOWN".
     */
    public String getImpact() {
        return this.impact;
    }

    public void setImpact(String impact) {
        if (impact == null) {
            this.impact = null;
        } else {
            this.impact = impact.toUpperCase(Locale.ENGLISH);
        }
        afterNodeChanged();
    }


    /**
     * @return the role of this operation ("getter", "setter", "operation", or
     * "constructor").
     */
    public String getRole() {
        return this.role;
    }

    public void setRole(String role) {
        this.role = role;
        afterNodeChanged();
    }


    /**
     * @return the fully qualified Java class name of the return type for this
     * operation.
     */
    public String getReturnType() {
        if(type == null) {
            type = "void";
        }
        return type;
    }

    public void setReturnType(String returnType) {
        this.type = returnType;
        afterNodeChanged();
    }

    /**
     * @return the set of parameters for this operation.
     */
    public ParameterInfo[] getSignature() {
        Lock readLock = parametersLock.readLock();
        readLock.lock();
        try {
            return Arrays.copyOf(this.parameters, this.parameters.length);
        } finally {
            readLock.unlock();
        }
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Add a new parameter to the set of arguments for this operation.
     *
     * @param parameter The new parameter descriptor
     */
    public void addParameter(ParameterInfo parameter) {

        Lock writeLock = parametersLock.writeLock();
        writeLock.lock();
        try {
            ParameterInfo results[] = new ParameterInfo[parameters.length + 1];
            System.arraycopy(parameters, 0, results, 0, parameters.length);
            results[parameters.length] = parameter;
            parameters = results;
            afterNodeChanged();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    protected MBeanOperationInfo buildMBeanInfo() {
        return createOperationInfo();
    }
    /**
     * Create and return a <code>ModelMBeanOperationInfo</code> object that
     * corresponds to the attribute described by this instance.
     * @return the operation info
     */
    private MBeanOperationInfo createOperationInfo() {
        // Create and return a new information object
        int opsImpact;
        if ("ACTION".equals(getImpact())) {
            opsImpact = MBeanOperationInfo.ACTION;
        } else if ("ACTION_INFO".equals(getImpact())) {
            opsImpact = MBeanOperationInfo.ACTION_INFO;
        } else if ("INFO".equals(getImpact())) {
            opsImpact = MBeanOperationInfo.INFO;
        } else {
            opsImpact = MBeanOperationInfo.UNKNOWN;
        }

        return new MBeanOperationInfo(getName(), getDescription(), getMBeanParameterInfo(), getReturnType(), opsImpact);
    }

    private MBeanParameterInfo[] getMBeanParameterInfo() {
        ParameterInfo params[] = getSignature();
        MBeanParameterInfo parameterBeans[] = new MBeanParameterInfo[params.length];
        for (int i = 0; i < params.length; i++) {
            parameterBeans[i] = (MBeanParameterInfo) params[i].getLatestMBeanInfo();
        }
        return parameterBeans;
    }
}
