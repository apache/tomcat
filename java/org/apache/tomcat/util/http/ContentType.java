/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.tomcat.util.http;


/**
 * Usefull methods for Content-Type processing
 * 
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author costin@eng.sun.com
 */
public class ContentType {

    // Basically return everything after ";charset="
    // If no charset specified, use the HTTP default (ASCII) character set.
    public static String getCharsetFromContentType(String type) {
        if (type == null) {
            return null;
        }
        int semi = type.indexOf(";");
        if (semi == -1) {
            return null;
        }
        int charsetLocation = type.indexOf("charset=", semi);
        if (charsetLocation == -1) {
            return null;
        }
	String afterCharset = type.substring(charsetLocation + 8);
        // The charset value in a Content-Type header is allowed to be quoted
        // and charset values can't contain quotes.  Just convert any quote
        // chars into spaces and let trim clean things up.
        afterCharset = afterCharset.replace('"', ' ');
        String encoding = afterCharset.trim();
        return encoding;
    }


    /**
     * Returns true if the given content type contains a charset component,
     * false otherwise.
     *
     * @param type Content type
     * @return true if the given content type contains a charset component,
     * false otherwise
     */
    public static boolean hasCharset(String type) {

        boolean hasCharset = false;

        int len = type.length();
        int index = type.indexOf(';');
        while (index != -1) {
            index++;
            while (index < len && Character.isSpace(type.charAt(index))) {
                index++;
            }
            if (index+8 < len
                    && type.charAt(index) == 'c'
                    && type.charAt(index+1) == 'h'
                    && type.charAt(index+2) == 'a'
                    && type.charAt(index+3) == 'r'
                    && type.charAt(index+4) == 's'
                    && type.charAt(index+5) == 'e'
                    && type.charAt(index+6) == 't'
                    && type.charAt(index+7) == '=') {
                hasCharset = true;
                break;
            }
            index = type.indexOf(';', index);
        }

        return hasCharset;
    }

}
