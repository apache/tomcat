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

package org.apache.coyote.servlet;


import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;



/**
 * One instance per Context. Holds the 
 * 
 * Utility class that attempts to map from a Locale to the corresponding
 * character set to be used for interpreting input text (or generating
 * output text) when the Content-Type header does not include one.  You
 * can customize the behavior of this class by modifying the mapping data
 * it loads, or by subclassing it (to change the algorithm) and then using
 * your own version for a particular web application.
 *
 * @author Craig R. McClanahan
 */
public class Locale2Charset {


    // ---------------------------------------------------- Manifest Constants


    /**
     * Default properties resource name.
     */
    public static final String DEFAULT_RESOURCE =
      "/org/apache/coyote/servlet/CharsetMapperDefault.properties";

    

    // ---------------------------------------------------------- Constructors


    /**
     * Construct a new CharsetMapper using the default properties resource.
     */
    public Locale2Charset() {
      String name = DEFAULT_RESOURCE;
      if (defaultMap == null) { // once !
        try {
          defaultMap = new Properties();
          InputStream stream =
            this.getClass().getResourceAsStream(name);
          defaultMap.load(stream);
          stream.close();
        } catch (Throwable t) {
          throw new IllegalArgumentException(t.toString());
        }
      }
      map = defaultMap;
    }


    // ---------------------------------------------------- Instance Variables


    private static Properties defaultMap; // shared for all apps
    
    /**
     * The mapping properties that have been initialized from the specified or
     * default properties resource.
     */
    private Properties map;


    // ------------------------------------------------------- Public Methods


    /**
     * Calculate the name of a character set to be assumed, given the specified
     * Locale and the absence of a character set specified as part of the
     * content type header.
     *
     * @param locale The locale for which to calculate a character set
     */
    public String getCharset(Locale locale) {
        // Match full language_country_variant first, then language_country, 
        // then language only
        String charset = map.getProperty(locale.toString());
        if (charset == null) {
            charset = map.getProperty(locale.getLanguage() + "_" 
                    + locale.getCountry());
            if (charset == null) {
                charset = map.getProperty(locale.getLanguage());
            }
        }
        return (charset);
    }

    
    /**
     * The deployment descriptor can have a
     * locale-encoding-mapping-list element which describes the
     * webapp's desired mapping from locale to charset.  This method
     * gets called when processing the web.xml file for a context
     *
     * @param locale The locale for a character set
     * @param charset The charset to be associated with the locale
     */
    public void addCharsetMapping(String locale, String charset) {
      if (map == defaultMap) { 
        // new copy, don't modify original
        map = new Properties(defaultMap);
      }
      map.put(locale, charset);
    }
}
