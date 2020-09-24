/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.http2;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.res.StringManager;

/**
 * Encoder for HPACK frames.
 */
public class HpackEncoder {

    private static final Log log = LogFactory.getLog(HpackEncoder.class);
    private static final StringManager sm = StringManager.getManager(HpackEncoder.class);

    public static final HpackHeaderFunction DEFAULT_HEADER_FUNCTION = new HpackHeaderFunction() {
        @Override
        public boolean shouldUseIndexing(String headerName, String value) {
            //content length and date change all the time
            //no need to index them, or they will churn the table
            switch (headerName) {
                case "content-length":
                case "date":
                    return false;
                default:
                    return true;
            }
        }

        @Override
        public boolean shouldUseHuffman(String header, String value) {
            return value.length() > 5; //TODO: figure out a good value for this
        }

        @Override
        public boolean shouldUseHuffman(String header) {
            return header.length() > 5; //TODO: figure out a good value for this
        }


    };

    private int headersIterator = -1;
    private boolean firstPass = true;

    private MimeHeaders currentHeaders;

    private int entryPositionCounter;

    private int newMaxHeaderSize = -1; //if the max header size has been changed
    private int minNewMaxHeaderSize = -1; //records the smallest value of newMaxHeaderSize, as per section 4.1

    private static final Map<String, TableEntry[]> ENCODING_STATIC_TABLE;

    private final Deque<TableEntry> evictionQueue = new ArrayDeque<>();
    private final Map<String, List<TableEntry>> dynamicTable = new HashMap<>(); //TODO: use a custom data structure to reduce allocations

    static {
        Map<String, TableEntry[]> map = new HashMap<>();
        for (int i = 1; i < Hpack.STATIC_TABLE.length; ++i) {
            Hpack.HeaderField m = Hpack.STATIC_TABLE[i];
            TableEntry[] existing = map.get(m.name);
            if (existing == null) {
                map.put(m.name, new TableEntry[]{new TableEntry(m.name, m.value, i)});
            } else {
                TableEntry[] newEntry = new TableEntry[existing.length + 1];
                System.arraycopy(existing, 0, newEntry, 0, existing.length);
                newEntry[existing.length] = new TableEntry(m.name, m.value, i);
                map.put(m.name, newEntry);
            }
        }
        ENCODING_STATIC_TABLE = Collections.unmodifiableMap(map);
    }

    /**
     * The maximum table size
     */
    private int maxTableSize = Hpack.DEFAULT_TABLE_SIZE;

    /**
     * The current table size
     */
    private int currentTableSize;

    private final HpackHeaderFunction hpackHeaderFunction;

    HpackEncoder() {
        this.hpackHeaderFunction = DEFAULT_HEADER_FUNCTION;
    }

    /**
     * Encodes the headers into a buffer.
     *
     * @param headers The headers to encode
     * @param target  The buffer to which to write the encoded headers
     *
     * @return The state of the encoding process
     */
    public State encode(MimeHeaders headers, ByteBuffer target) {
        int it = headersIterator;
        if (headersIterator == -1) {
            handleTableSizeChange(target);
            //new headers map
            it = 0;
            currentHeaders = headers;
        } else {
            if (headers != currentHeaders) {
                throw new IllegalStateException();
            }
        }
        while (it < currentHeaders.size()) {
            // FIXME: Review lowercase policy
            String headerName = headers.getName(it).toString().toLowerCase(Locale.US);
            boolean skip = false;
            if (firstPass) {
                if (headerName.charAt(0) != ':') {
                    skip = true;
                }
            } else {
                if (headerName.charAt(0) == ':') {
                    skip = true;
                }
            }
            if (!skip) {
                    String val = headers.getValue(it).toString();

                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("hpackEncoder.encodeHeader", headerName, val));
                    }
                    TableEntry tableEntry = findInTable(headerName, val);

                    // We use 11 to make sure we have enough room for the
                    // variable length integers
                    int required = 11 + headerName.length() + 1 + val.length();

                    if (target.remaining() < required) {
                        this.headersIterator = it;
                        return State.UNDERFLOW;
                    }
                    // Only index if it will fit
                    boolean canIndex = hpackHeaderFunction.shouldUseIndexing(headerName, val) &&
                            (headerName.length() + val.length() + 32) < maxTableSize;
                    if (tableEntry == null && canIndex) {
                        //add the entry to the dynamic table
                        target.put((byte) (1 << 6));
                        writeHuffmanEncodableName(target, headerName);
                        writeHuffmanEncodableValue(target, headerName, val);
                        addToDynamicTable(headerName, val);
                    } else if (tableEntry == null) {
                        //literal never indexed
                        target.put((byte) (1 << 4));
                        writeHuffmanEncodableName(target, headerName);
                        writeHuffmanEncodableValue(target, headerName, val);
                    } else {
                        //so we know something is already in the table
                        if (val.equals(tableEntry.value)) {
                            //the whole thing is in the table
                            target.put((byte) (1 << 7));
                            Hpack.encodeInteger(target, tableEntry.getPosition(), 7);
                        } else {
                            if (canIndex) {
                                //add the entry to the dynamic table
                                target.put((byte) (1 << 6));
                                Hpack.encodeInteger(target, tableEntry.getPosition(), 6);
                                writeHuffmanEncodableValue(target, headerName, val);
                                addToDynamicTable(headerName, val);

                            } else {
                                target.put((byte) (1 << 4));
                                Hpack.encodeInteger(target, tableEntry.getPosition(), 4);
                                writeHuffmanEncodableValue(target, headerName, val);
                            }
                        }
                    }

            }
            if (++it == currentHeaders.size() && firstPass) {
                firstPass = false;
                it = 0;
            }
        }
        headersIterator = -1;
        firstPass = true;
        return State.COMPLETE;
    }

    private void writeHuffmanEncodableName(ByteBuffer target, String headerName) {
        if (hpackHeaderFunction.shouldUseHuffman(headerName)) {
            if(HPackHuffman.encode(target, headerName, true)) {
                return;
            }
        }
        target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
        Hpack.encodeInteger(target, headerName.length(), 7);
        for (int j = 0; j < headerName.length(); ++j) {
            target.put((byte) Hpack.toLower(headerName.charAt(j)));
        }

    }

    private void writeHuffmanEncodableValue(ByteBuffer target, String headerName, String val) {
        if (hpackHeaderFunction.shouldUseHuffman(headerName, val)) {
            if (!HPackHuffman.encode(target, val, false)) {
                writeValueString(target, val);
            }
        } else {
            writeValueString(target, val);
        }
    }

    private void writeValueString(ByteBuffer target, String val) {
        target.put((byte) 0); //to use encodeInteger we need to place the first byte in the buffer.
        Hpack.encodeInteger(target, val.length(), 7);
        for (int j = 0; j < val.length(); ++j) {
            target.put((byte) val.charAt(j));
        }
    }

    private void addToDynamicTable(String headerName, String val) {
        int pos = entryPositionCounter++;
        DynamicTableEntry d = new DynamicTableEntry(headerName, val, -pos);
        List<TableEntry> existing = dynamicTable.get(headerName);
        if (existing == null) {
            dynamicTable.put(headerName, existing = new ArrayList<>(1));
        }
        existing.add(d);
        evictionQueue.add(d);
        currentTableSize += d.size;
        runEvictionIfRequired();
        if (entryPositionCounter == Integer.MAX_VALUE) {
            //prevent rollover
            preventPositionRollover();
        }

    }


    private void preventPositionRollover() {
        //if the position counter is about to roll over we iterate all the table entries
        //and set their position to their actual position
        for (List<TableEntry> tableEntries : dynamicTable.values()) {
            for (TableEntry t : tableEntries) {
                t.position = t.getPosition();
            }
        }
        entryPositionCounter = 0;
    }

    private void runEvictionIfRequired() {

        while (currentTableSize > maxTableSize) {
            TableEntry next = evictionQueue.poll();
            if (next == null) {
                return;
            }
            currentTableSize -= next.size;
            List<TableEntry> list = dynamicTable.get(next.name);
            list.remove(next);
            if (list.isEmpty()) {
                dynamicTable.remove(next.name);
            }
        }
    }

    private TableEntry findInTable(String headerName, String value) {
        TableEntry[] staticTable = ENCODING_STATIC_TABLE.get(headerName);
        if (staticTable != null) {
            for (TableEntry st : staticTable) {
                if (st.value != null && st.value.equals(value)) { //todo: some form of lookup?
                    return st;
                }
            }
        }
        List<TableEntry> dynamic = dynamicTable.get(headerName);
        if (dynamic != null) {
            for (TableEntry st : dynamic) {
                if (st.value.equals(value)) { //todo: some form of lookup?
                    return st;
                }
            }
        }
        if (staticTable != null) {
            return staticTable[0];
        }
        return null;
    }

    public void setMaxTableSize(int newSize) {
        this.newMaxHeaderSize = newSize;
        if (minNewMaxHeaderSize == -1) {
            minNewMaxHeaderSize = newSize;
        } else {
            minNewMaxHeaderSize = Math.min(newSize, minNewMaxHeaderSize);
        }
    }

    private void handleTableSizeChange(ByteBuffer target) {
        if (newMaxHeaderSize == -1) {
            return;
        }
        if (minNewMaxHeaderSize != newMaxHeaderSize) {
            target.put((byte) (1 << 5));
            Hpack.encodeInteger(target, minNewMaxHeaderSize, 5);
        }
        target.put((byte) (1 << 5));
        Hpack.encodeInteger(target, newMaxHeaderSize, 5);
        maxTableSize = newMaxHeaderSize;
        runEvictionIfRequired();
        newMaxHeaderSize = -1;
        minNewMaxHeaderSize = -1;
    }

    public enum State {
        COMPLETE,
        UNDERFLOW,

    }

    static class TableEntry {
        final String name;
        final String value;
        final int size;
        int position;

        TableEntry(String name, String value, int position) {
            this.name = name;
            this.value = value;
            this.position = position;
            if (value != null) {
                this.size = 32 + name.length() + value.length();
            } else {
                this.size = -1;
            }
        }

        public int getPosition() {
            return position;
        }
    }

    class DynamicTableEntry extends TableEntry {

        DynamicTableEntry(String name, String value, int position) {
            super(name, value, position);
        }

        @Override
        public int getPosition() {
            return super.getPosition() + entryPositionCounter + Hpack.STATIC_TABLE_LENGTH;
        }
    }

    public interface HpackHeaderFunction {
        boolean shouldUseIndexing(String header, String value);

        /**
         * Returns true if huffman encoding should be used on the header value
         *
         * @param header The header name
         * @param value  The header value to be encoded
         * @return <code>true</code> if the value should be encoded
         */
        boolean shouldUseHuffman(String header, String value);

        /**
         * Returns true if huffman encoding should be used on the header name
         *
         * @param header The header name to be encoded
         * @return <code>true</code> if the value should be encoded
         */
        boolean shouldUseHuffman(String header);
    }
}
