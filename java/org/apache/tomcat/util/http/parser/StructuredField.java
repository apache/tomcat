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
package org.apache.tomcat.util.http.parser;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.tomcat.util.res.StringManager;

/**
 * Parsing of structured fields as per RFC 8941.
 * <p>
 * The parsing implementation is complete but not all elements are currently exposed via getters. Additional getters
 * will be added as required as the use of structured fields expands.
 * <p>
 * The serialization of structured fields has not been implemented.
 */
public class StructuredField {

    private static final StringManager sm = StringManager.getManager(StructuredField.class);

    private static final int ARRAY_SIZE = 128;

    private static final boolean[] IS_KEY_FIRST = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_KEY = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_OWS = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_BASE64 = new boolean[ARRAY_SIZE];
    private static final boolean[] IS_TOKEN = new boolean[ARRAY_SIZE];

    static {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            if (i == '*' || i >= 'a' && i <= 'z') {
                IS_KEY_FIRST[i] = true;
                IS_KEY[i] = true;
            } else if (i >= '0' && i <= '9' || i == '_' || i == '-' || i == '.') {
                IS_KEY[i] = true;
            }
        }

        for (int i = 0; i < ARRAY_SIZE; i++) {
            if (i == 9 || i == ' ') {
                IS_OWS[i] = true;
            }
        }

        for (int i = 0; i < ARRAY_SIZE; i++) {
            if (i == '+' || i == '/' || i >= '0' && i <= '9' || i == '=' || i >= 'A' && i <= 'Z' ||
                    i >= 'a' && i <= 'z') {
                IS_BASE64[i] = true;
            }
        }

        for (int i = 0; i < ARRAY_SIZE; i++) {
            if (HttpParser.isToken(i) || i == ':' || i == '/') {
                IS_TOKEN[i] = true;
            }
        }
    }


    static SfList parseSfList(Reader input) throws IOException {
        skipSP(input);

        SfList result = new SfList();

        if (peek(input) != -1) {
            while (true) {
                SfListMember listMember = parseSfListMember(input);
                result.addListMember(listMember);
                skipOWS(input);
                if (peek(input) == -1) {
                    break;
                }
                requireChar(input, ',');
                skipOWS(input);
                requireNotChar(input, -1);
            }
        }

        skipSP(input);
        requireChar(input, -1);
        return result;
    }


    // Item or inner list
    static SfListMember parseSfListMember(Reader input) throws IOException {
        SfListMember listMember;
        if (peek(input) == '(') {
            listMember = parseSfInnerList(input);
        } else {
            listMember = parseSfBareItem(input);
        }
        parseSfParameters(input, listMember);
        return listMember;
    }


    static SfInnerList parseSfInnerList(Reader input) throws IOException {
        requireChar(input, '(');

        SfInnerList innerList = new SfInnerList();

        while (true) {
            skipSP(input);
            if (peek(input) == ')') {
                break;
            }
            SfItem<?> item = parseSfBareItem(input);
            parseSfParameters(input, item);
            innerList.addListItem(item);
            input.mark(1);
            requireChar(input, ' ', ')');
            input.reset();
        }
        requireChar(input, ')');

        return innerList;
    }


    static SfDictionary parseSfDictionary(Reader input) throws IOException {
        skipSP(input);

        SfDictionary result = new SfDictionary();

        if (peek(input) != -1) {
            while (true) {
                String key = parseSfKey(input);
                SfListMember listMember;
                input.mark(1);
                int c = input.read();
                if (c == '=') {
                    listMember = parseSfListMember(input);
                } else {
                    listMember = new SfBoolean(true);
                    input.reset();
                }
                parseSfParameters(input, listMember);
                result.addDictionaryMember(key, listMember);
                skipOWS(input);
                if (peek(input) == -1) {
                    break;
                }
                requireChar(input, ',');
                skipOWS(input);
                requireNotChar(input, -1);
            }
        }

        skipSP(input);
        requireChar(input, -1);
        return result;
    }


    static SfItem<?> parseSfItem(Reader input) throws IOException {
        skipSP(input);

        SfItem<?> item = parseSfBareItem(input);
        parseSfParameters(input, item);

        skipSP(input);
        requireChar(input, -1);
        return item;
    }


    static SfItem<?> parseSfBareItem(Reader input) throws IOException {
        int c = input.read();

        SfItem<?> item;
        if (c == '-' || HttpParser.isNumeric(c)) {
            item = parseSfNumeric(input, c);
        } else if (c == '\"') {
            item = parseSfString(input);
        } else if (c == '*' || HttpParser.isAlpha(c)) {
            item = parseSfToken(input, c);
        } else if (c == ':') {
            item = parseSfByteSequence(input);
        } else if (c == '?') {
            item = parseSfBoolean(input);
        } else {
            throw new IllegalArgumentException(
                    sm.getString("sf.bareitem.invalidCharacter", String.format("\\u%40X", Integer.valueOf(c))));
        }

        return item;
    }


    static void parseSfParameters(Reader input, SfListMember listMember) throws IOException {
        while (true) {
            if (peek(input) != ';') {
                break;
            }
            requireChar(input, ';');
            skipSP(input);
            String key = parseSfKey(input);
            SfItem<?> item;
            input.mark(1);
            int c = input.read();
            if (c == '=') {
                item = parseSfBareItem(input);
            } else {
                item = new SfBoolean(true);
                input.reset();
            }
            listMember.addParameter(key, item);
        }
    }


    static String parseSfKey(Reader input) throws IOException {
        StringBuilder result = new StringBuilder();

        input.mark(1);
        int c = input.read();
        if (!isKeyFirst(c)) {
            throw new IllegalArgumentException(
                    sm.getString("sf.key.invalidFirstCharacter", String.format("\\u%40X", Integer.valueOf(c))));
        }

        while (c != -1 && isKey(c)) {
            result.append((char) c);
            input.mark(1);
            c = input.read();
        }
        input.reset();
        return result.toString();
    }


    static SfItem<?> parseSfNumeric(Reader input, int first) throws IOException {
        int sign = 1;
        boolean integer = true;
        int decimalPos = 0;

        StringBuilder result = new StringBuilder();

        int c;
        if (first == '-') {
            sign = -1;
            c = input.read();
        } else {
            c = first;
        }

        if (!HttpParser.isNumeric(c)) {
            throw new IllegalArgumentException(
                    sm.getString("sf.numeric.invalidCharacter", String.format("\\u%40X", Integer.valueOf(c))));
        }
        result.append((char) c);
        input.mark(1);
        c = input.read();

        while (c != -1) {
            if (HttpParser.isNumeric(c)) {
                result.append((char) c);
            } else if (integer && c == '.') {
                if (result.length() > 12) {
                    throw new IllegalArgumentException(sm.getString("sf.numeric.integralPartTooLong"));
                }
                integer = false;
                result.append((char) c);
                decimalPos = result.length();
            } else {
                input.reset();
                break;
            }
            if (integer && result.length() > 15) {
                throw new IllegalArgumentException(sm.getString("sf.numeric.integerTooLong"));
            }
            if (!integer && result.length() > 16) {
                throw new IllegalArgumentException(sm.getString("sf.numeric.decimalTooLong"));
            }
            input.mark(1);
            c = input.read();
        }

        if (integer) {
            return new SfInteger(Long.parseLong(result.toString()) * sign);
        }

        if (result.charAt(result.length() - 1) == '.') {
            throw new IllegalArgumentException(sm.getString("sf.numeric.decimalInvalidFinal"));
        }

        if (result.length() - decimalPos > 3) {
            throw new IllegalArgumentException(sm.getString("sf.numeric.decimalPartTooLong"));
        }

        return new SfDecimal(Double.parseDouble(result.toString()) * sign);
    }


    static SfString parseSfString(Reader input) throws IOException {
        // It is known first character was '"'
        StringBuilder result = new StringBuilder();

        while (true) {
            int c = input.read();
            if (c == '\\') {
                requireNotChar(input, -1);
                c = input.read();
                if (c != '\\' && c != '\"') {
                    throw new IllegalArgumentException(
                            sm.getString("sf.string.invalidEscape", String.format("\\u%40X", Integer.valueOf(c))));
                }
            } else {
                if (c == '\"') {
                    break;
                }
                // This test also covers unexpected EOF
                if (c < 32 || c > 126) {
                    throw new IllegalArgumentException(
                            sm.getString("sf.string.invalidCharacter", String.format("\\u%40X", Integer.valueOf(c))));
                }
            }
            result.append((char) c);
        }

        return new SfString(result.toString());
    }


    static SfToken parseSfToken(Reader input, int first) throws IOException {
        // It is known first character is valid
        StringBuilder result = new StringBuilder();

        result.append((char) first);
        while (true) {
            input.mark(1);
            int c = input.read();
            if (!isToken(c)) {
                input.reset();
                break;
            }
            result.append((char) c);
        }

        return new SfToken(result.toString());
    }


    static SfByteSequence parseSfByteSequence(Reader input) throws IOException {
        // It is known first character was ':'
        StringBuilder base64 = new StringBuilder();

        while (true) {
            int c = input.read();

            if (c == ':') {
                break;
            } else if (isBase64(c)) {
                base64.append((char) c);
            } else {
                throw new IllegalArgumentException(
                        sm.getString("sf.base64.invalidCharacter", String.format("\\u%40X", Integer.valueOf(c))));
            }
        }

        return new SfByteSequence(Base64.getDecoder().decode(base64.toString()));
    }


    static SfBoolean parseSfBoolean(Reader input) throws IOException {
        // It is known first character was '?'
        int c = input.read();

        if (c == '1') {
            return new SfBoolean(true);
        } else if (c == '0') {
            return new SfBoolean(false);
        } else {
            throw new IllegalArgumentException(
                    sm.getString("sf.boolean.invalidCharacter", String.format("\\u%40X", Integer.valueOf(c))));
        }
    }


    static void skipSP(Reader input) throws IOException {
        input.mark(1);
        int c = input.read();
        while (c == 32) {
            input.mark(1);
            c = input.read();
        }
        input.reset();
    }


    static void skipOWS(Reader input) throws IOException {
        input.mark(1);
        int c = input.read();
        while (isOws(c)) {
            input.mark(1);
            c = input.read();
        }
        input.reset();
    }


    static void requireChar(Reader input, int... required) throws IOException {
        int c = input.read();
        for (int r : required) {
            if (c == r) {
                return;
            }
        }
        throw new IllegalArgumentException(
                sm.getString("sf.invalidCharacter", String.format("\\u%40X", Integer.valueOf(c))));
    }


    static void requireNotChar(Reader input, int required) throws IOException {
        input.mark(1);
        int c = input.read();
        if (c == required) {
            throw new IllegalArgumentException(
                    sm.getString("sf.invalidCharacter", String.format("\\u%40X", Integer.valueOf(c))));
        }
        input.reset();
    }


    static int peek(Reader input) throws IOException {
        input.mark(1);
        int c = input.read();
        input.reset();
        return c;
    }


    static boolean isKeyFirst(int c) {
        try {
            return IS_KEY_FIRST[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    static boolean isKey(int c) {
        try {
            return IS_KEY[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    static boolean isOws(int c) {
        try {
            return IS_OWS[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    static boolean isBase64(int c) {
        try {
            return IS_BASE64[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    static boolean isToken(int c) {
        try {
            return IS_TOKEN[c];
        } catch (ArrayIndexOutOfBoundsException ex) {
            return false;
        }
    }


    private StructuredField() {
        // Utility class. Hide default constructor.
    }


    static class SfDictionary {
        private Map<String,SfListMember> dictionary = new LinkedHashMap<>();

        void addDictionaryMember(String key, SfListMember value) {
            dictionary.put(key, value);
        }

        SfListMember getDictionaryMember(String key) {
            return dictionary.get(key);
        }
    }

    static class SfList {
        private List<SfListMember> listMembers = new ArrayList<>();

        void addListMember(SfListMember listMember) {
            listMembers.add(listMember);
        }
    }

    static class SfListMember {
        private Map<String,SfItem<?>> parameters = null;

        void addParameter(String key, SfItem<?> value) {
            if (parameters == null) {
                parameters = new LinkedHashMap<>();
            }
            parameters.put(key, value);
        }
    }

    static class SfInnerList extends SfListMember {
        List<SfItem<?>> listItems = new ArrayList<>();

        SfInnerList() {
            // Default constructor is NO-OP
        }

        void addListItem(SfItem<?> item) {
            listItems.add(item);
        }

        List<SfItem<?>> getListItem() {
            return listItems;
        }
    }

    abstract static class SfItem<T> extends SfListMember {
        private final T value;

        SfItem(T value) {
            this.value = value;
        }

        T getVaue() {
            return value;
        }
    }

    static class SfInteger extends SfItem<Long> {
        SfInteger(long value) {
            super(Long.valueOf(value));
        }
    }

    static class SfDecimal extends SfItem<Double> {
        SfDecimal(double value) {
            super(Double.valueOf(value));
        }
    }

    static class SfString extends SfItem<String> {
        SfString(String value) {
            super(value);
        }
    }

    static class SfToken extends SfItem<String> {
        SfToken(String value) {
            super(value);
        }
    }

    static class SfByteSequence extends SfItem<byte[]> {
        SfByteSequence(byte[] value) {
            super(value);
        }
    }

    static class SfBoolean extends SfItem<Boolean> {
        SfBoolean(boolean value) {
            super(Boolean.valueOf(value));
        }
    }
}
