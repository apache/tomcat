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
package jakarta.servlet.jsp;

import java.util.HashMap;
import java.util.Map;

import jakarta.el.ELContext;

import org.apache.jasper.compiler.Localizer;

public class TesterPageContextWithAttributes extends TesterPageContext {
    private final Map<String, Object> applicationAttributes = new HashMap<>();
    private final Map<String, Object> pageAttributes = new HashMap<>();
    private final Map<String, Object> requestAttributes = new HashMap<>();
    private final Map<String, Object> sessionAttributes = new HashMap<>();

    public TesterPageContextWithAttributes() {
        super();
    }

    public TesterPageContextWithAttributes(ELContext elContext) {
        super(elContext);
    }

    @Override
    public Object getAttribute(String name) {
        return getAttribute(name, PAGE_SCOPE);
    }

    @Override
    public Object getAttribute(String name, int scope) {
        if (name == null) {
            throw new NullPointerException(Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        return switch (scope) {
        case PAGE_SCOPE -> pageAttributes.get(name);
        case REQUEST_SCOPE -> requestAttributes.get(name);
        case SESSION_SCOPE -> sessionAttributes.get(name);
        case APPLICATION_SCOPE -> applicationAttributes.get(name);
        default -> throw new IllegalArgumentException(Localizer.getMessage("jsp.error.page.invalid.scope"));
        };
    }

    @Override
    public void removeAttribute(String name) {
        removeAttribute(name, PAGE_SCOPE);
        removeAttribute(name, REQUEST_SCOPE);
        removeAttribute(name, SESSION_SCOPE);
        removeAttribute(name, APPLICATION_SCOPE);
    }

    @Override
    public void removeAttribute(String name, int scope) {
        switch (scope) {
        case PageContext.APPLICATION_SCOPE:
            applicationAttributes.remove(name);
            break;
        case PageContext.PAGE_SCOPE:
            pageAttributes.remove(name);
            break;
        case PageContext.REQUEST_SCOPE:
            requestAttributes.remove(name);
            break;
        case PageContext.SESSION_SCOPE:
            sessionAttributes.remove(name);
            break;
        default:
            throw new IllegalArgumentException(Localizer.getMessage("jsp.error.page.invalid.scope"));
        }
    }

    @Override
    public void setAttribute(String name, Object value) {
        setAttribute(name, value, PAGE_SCOPE);
    }

    @Override
    public void setAttribute(String name, Object value, int scope) {
        if (value == null) {
            removeAttribute(name, scope);
        } else {
            switch (scope) {
            case PAGE_SCOPE:
                pageAttributes.put(name, value);
                break;

            case REQUEST_SCOPE:
                requestAttributes.put(name, value);
                break;

            case SESSION_SCOPE:
                sessionAttributes.put(name, value);
                break;

            case APPLICATION_SCOPE:
                applicationAttributes.put(name, value);
                break;

            default:
                throw new IllegalArgumentException(Localizer.getMessage("jsp.error.page.invalid.scope"));
            }
        }
    }

}
