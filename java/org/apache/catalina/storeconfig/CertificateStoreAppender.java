/**
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
package org.apache.catalina.storeconfig;

import java.beans.PropertyDescriptor;

import org.apache.tomcat.util.IntrospectionUtils;

/**
 * Store the Certificate attributes.
 */
public class CertificateStoreAppender extends StoreAppender {

    @Override
    protected Object checkAttribute(StoreDescription desc, PropertyDescriptor descriptor, String attributeName,
            Object bean, Object bean2) {
        if (attributeName.equals("type")) {
            return IntrospectionUtils.getProperty(bean, descriptor.getName());
        } else {
            return super.checkAttribute(desc, descriptor, attributeName, bean, bean2);
        }
    }

}