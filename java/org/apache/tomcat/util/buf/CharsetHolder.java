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
package org.apache.tomcat.util.buf;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/**
 * Represents a character encoding to be used for a request or response.
 * <p>
 * Historically the Servlet API has used Strings for this information with lazy conversion to Charset. Sometimes the API
 * required that an invalid name triggered an exception. Sometimes the invalid name was treated as if it had never been
 * set. This resulted in classes storing both the String and, if the name was valid, the Charset with validation and
 * conversion logic spread throughout those classes.
 * <p>
 * This class is an attempt to encapsulate that behaviour.
 */
public class CharsetHolder {

    public static final CharsetHolder EMPTY = new CharsetHolder(null, null);

    public static CharsetHolder getInstance(String name) {
        if (name == null) {
            return EMPTY;
        }

        Charset charset;
        try {
            charset = B2CConverter.getCharset(name);
        } catch (UnsupportedEncodingException e) {
            charset = null;
        }

        return new CharsetHolder(name, charset);
    }


    public static CharsetHolder getInstance(Charset encoding) {
        if (encoding == null) {
            return EMPTY;
        }

        return new CharsetHolder(encoding.name(), encoding);
    }


    private final String name;
    private final Charset charset;


    private CharsetHolder(String name, Charset charset) {
        this.name = name;
        this.charset = charset;
    }


    public String getName() {
        return name;
    }


    /**
     * Returns the Charset, {@code null} if no Charset has been specified, or {@code null} if the holder was created
     * using the name of a Charset that the JRE does not recognise.
     *
     * @return The Charset or {@code null} it is not set or invalid
     */
    public Charset getCharset() {
        return charset;
    }


    /**
     * Returns the Charset or {@code null} if no Charset has been specified.
     *
     * @return The validated Charset or {@code null} if is not set
     *
     * @throws UnsupportedEncodingException if the holder was created using the name of a Charset that the JRE does not
     *                                          recognise
     */
    public Charset getValidatedCharset() throws UnsupportedEncodingException {
        validate();
        return charset;
    }


    /**
     * Throws an exception if the instance holds a name that without a matching Charset.
     *
     * @throws UnsupportedEncodingException if the holder contains a name without a matching Charset
     */
    public void validate() throws UnsupportedEncodingException {
        if (name != null && charset == null) {
            throw new UnsupportedEncodingException(name);
        }
    }
}
