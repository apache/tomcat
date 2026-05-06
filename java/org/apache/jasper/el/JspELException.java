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

import javax.el.ELException;

/**
 * Exception wrapper that adds a JSP-specific mark to an ELException for better error tracking.
 */
public class JspELException extends ELException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new JspELException wrapping the given ELException with a mark prefix.
     *
     * @param mark the mark prefix to prepend to the error message
     * @param e the underlying ELException
     */
    public JspELException(String mark, ELException e) {
        super(mark + " " + e.getMessage(), e.getCause());
    }
}
