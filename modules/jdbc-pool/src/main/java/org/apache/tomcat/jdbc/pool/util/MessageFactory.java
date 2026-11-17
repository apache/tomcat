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
package org.apache.tomcat.jdbc.pool.util;

import java.text.Format;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Factory for creating localized JDBC pool messages.
 * <p>
 * The messages are read from the {@code LocalStrings} resource bundle of this
 * package, which the JVM resolves once, under the default locale in effect at
 * class initialization. A message is handed out unchanged by
 * {@link #get(String)} and passed through {@link java.text.MessageFormat} by
 * {@link #get(String, Object...)}, so bundle text must match how its key is
 * fetched: a key fetched without arguments is written literally, while a key
 * fetched with arguments follows the MessageFormat conventions, where a
 * literal single quote is written {@code ''}.
 */
public final class MessageFactory {

    private static final ResourceBundle DEFAULT_BUNDLE =
            ResourceBundle.getBundle("org.apache.tomcat.jdbc.pool.util.LocalStrings");

    private static final MessageFactory DEFAULT_MESSAGE_FACTORY = new MessageFactory(DEFAULT_BUNDLE);

    /**
     * Get a localized message. The message is returned exactly as written in
     * the bundle, with no argument substitution and no MessageFormat applied.
     *
     * @param key the message key
     *
     * @return the message
     */
    public static String get(final String key) {
        return DEFAULT_MESSAGE_FACTORY.getInternal(key);
    }

    /**
     * Get a localized message with arguments substituted by MessageFormat.
     * The argument array is never modified. A pattern that MessageFormat
     * cannot apply is returned unformatted rather than reported as a failure.
     *
     * @param key  the message key
     * @param args the arguments
     *
     * @return the message
     */
    public static String get(final String key, final Object... args) {
        return DEFAULT_MESSAGE_FACTORY.getInternal(key, args);
    }

    private final ResourceBundle bundle;

    /**
     * Constructor.
     *
     * @param bundle the resource bundle
     */
    public MessageFactory(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    /**
     * Get a message from the bundle.
     *
     * @param key the message key
     *
     * @return the message
     */
    protected String getInternal(final String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Get a message from the bundle with arguments.
     *
     * @param key  the message key
     * @param args the arguments
     *
     * @return the message
     */
    protected String getInternal(final String key, final Object... args) {
        String value = getInternal(key);

        try {
            MessageFormat mf = new MessageFormat(value);
            Format[] formats = null;

            // Convert Number arguments to String unless the pattern asks for a
            // number format at that index, so MessageFormat cannot group them
            // by locale. Work on a copy: the array belongs to the caller.
            Object[] formatted = args;
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Number) {
                        if (formats == null) {
                            formats = mf.getFormatsByArgumentIndex();
                        }
                        if (i < formats.length && !(formats[i] instanceof NumberFormat)) {
                            if (formatted == args) {
                                formatted = args.clone();
                            }
                            formatted[i] = args[i].toString();
                        }
                    }
                }
            }

            return mf.format(formatted, new StringBuffer(), null).toString();
        } catch (IllegalArgumentException e) {
            // A pattern MessageFormat cannot parse or apply - reachable only
            // through a malformed, typically translated, message - degrades to
            // the raw pattern rather than taking down the caller, which at
            // these sites is building an exception or a log line.
            return value;
        }
    }
}
