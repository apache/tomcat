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
package org.apache.el.util;

import java.text.Format;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @author Jacob Hookom [jacob@hookom.net]
 */
public final class MessageFactory {

    private static final ResourceBundle DEFAULT_BUNDLE = ResourceBundle.getBundle("org.apache.el.LocalStrings");

    private static final MessageFactory DEFAULT_MESSAGE_FACTORY = new MessageFactory(DEFAULT_BUNDLE);

    public static String get(final String key) {
        return DEFAULT_MESSAGE_FACTORY.getInternal(key);
    }

    public static String get(final String key, final Object... args) {
        return DEFAULT_MESSAGE_FACTORY.getInternal(key, args);
    }

    private final ResourceBundle bundle;

    public MessageFactory(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    protected String getInternal(final String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    protected String getInternal(final String key, final Object... args) {
        String value = getInternal(key);

        MessageFormat mf = new MessageFormat(value);
        Format[] formats = null;

        // Unless an argument has been explicitly configured to use a number
        // format, convert all Number arguments to String else MessageFormat may
        // try to format them in unexpected ways.
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Number) {
                    if (formats == null) {
                        formats = mf.getFormatsByArgumentIndex();
                    }
                    if (i < formats.length && !(formats[i] instanceof NumberFormat)) {
                        args[i] = args[i].toString();
                    }
                }
            }
        }

        return mf.format(args, new StringBuffer(), null).toString();
    }
}
