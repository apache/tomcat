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

import java.io.Serial;

import jakarta.el.MethodNotFoundException;

/**
 * Exception wrapper that adds a JSP-specific mark to a MethodNotFoundException for better error tracking.
 */
public class JspMethodNotFoundException extends MethodNotFoundException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new JspMethodNotFoundException wrapping the given exception with a mark prefix.
     *
     * @param mark the mark prefix to prepend to the error message
     * @param e the underlying MethodNotFoundException
     */
    public JspMethodNotFoundException(String mark, MethodNotFoundException e) {
        super(mark + " " + e.getMessage(), e.getCause());
    }
}
