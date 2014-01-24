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
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 1999, International
 * Business Machines, Inc., http://www.apache.org.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.jasper.xmlparser;

/**
 * This class is a symbol table implementation that guarantees that
 * strings used as identifiers are unique references. Multiple calls
 * to <code>addSymbol</code> will always return the same string
 * reference.
 * <p>
 * The symbol table performs the same task as <code>String.intern()</code>
 * with the following differences:
 * <ul>
 *  <li>
 *   A new string object does not need to be created in order to
 *   retrieve a unique reference. Symbols can be added by using
 *   a series of characters in a character array.
 *  </li>
 *  <li>
 *   Users of the symbol table can provide their own symbol hashing
 *   implementation. For example, a simple string hashing algorithm
 *   may fail to produce a balanced set of hashcodes for symbols
 *   that are <em>mostly</em> unique. Strings with similar leading
 *   characters are especially prone to this poor hashing behavior.
 *  </li>
 * </ul>
 *
 * @author Andy Clark
 */
public class SymbolTable {

    //
    // Constants
    //

    /** Default table size. */
    private static final int TABLE_SIZE = 101;

    //
    // Data
    //

    /** Buckets. */
    private final Entry[] fBuckets;

    // actual table size
    private final int fTableSize;

    //
    // Constructors
    //

    /** Constructs a symbol table with a default number of buckets. */
    public SymbolTable() {
        this(TABLE_SIZE);
    }

    /** Constructs a symbol table with a specified number of buckets. */
    public SymbolTable(int tableSize) {
        fTableSize = tableSize;
        fBuckets = new Entry[fTableSize];
    }

    //
    // Public methods
    //

    /**
     * Adds the specified symbol to the symbol table and returns a
     * reference to the unique symbol. If the symbol already exists,
     * the previous symbol reference is returned instead, in order
     * guarantee that symbol references remain unique.
     *
     * @param buffer The buffer containing the new symbol.
     * @param offset The offset into the buffer of the new symbol.
     * @param length The length of the new symbol in the buffer.
     */
    public String addSymbol(char[] buffer, int offset, int length) {

        // search for identical symbol
        int bucket = hash(buffer, offset, length) % fTableSize;
        OUTER: for (Entry entry = fBuckets[bucket]; entry != null; entry = entry.next) {
            if (length == entry.characters.length) {
                for (int i = 0; i < length; i++) {
                    if (buffer[offset + i] != entry.characters[i]) {
                        continue OUTER;
                    }
                }
                return entry.symbol;
            }
        }

        // add new entry
        Entry entry = new Entry(buffer, offset, length, fBuckets[bucket]);
        fBuckets[bucket] = entry;
        return entry.symbol;

    } // addSymbol(char[],int,int):String

    /**
     * Returns a hashcode value for the specified symbol information.
     * The value returned by this method must be identical to the value
     * returned by the <code>hash(String)</code> method when called
     * with the string object created from the symbol information.
     *
     * @param buffer The character buffer containing the symbol.
     * @param offset The offset into the character buffer of the start
     *               of the symbol.
     * @param length The length of the symbol.
     */
    public int hash(char[] buffer, int offset, int length) {

        int code = 0;
        for (int i = 0; i < length; i++) {
            code = code * 37 + buffer[offset + i];
        }
        return code & 0x7FFFFFF;

    } // hash(char[],int,int):int

    //
    // Classes
    //

    /**
     * This class is a symbol table entry. Each entry acts as a node
     * in a linked list.
     */
    private static final class Entry {

        //
        // Data
        //

        /** Symbol. */
        private final String symbol;

        /**
         * Symbol characters. This information is duplicated here for
         * comparison performance.
         */
        private final char[] characters;

        /** The next entry. */
        private final Entry next;

        //
        // Constructors
        //

        /**
         * Constructs a new entry from the specified symbol information and
         * next entry reference.
         */
        public Entry(char[] ch, int offset, int length, Entry next) {
            characters = new char[length];
            System.arraycopy(ch, offset, characters, 0, length);
            symbol = new String(characters).intern();
            this.next = next;
        }

    } // class Entry

} // class SymbolTable
