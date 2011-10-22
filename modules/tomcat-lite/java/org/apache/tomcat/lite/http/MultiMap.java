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

package org.apache.tomcat.lite.http;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.tomcat.lite.io.BBuffer;
import org.apache.tomcat.lite.io.CBucket;
import org.apache.tomcat.lite.io.CBuffer;

/**
 * Map used to represent headers and parameters ( could be used
 * for cookies too )
 *
 * It'll avoid garbage collection, like original tomcat classes,
 * by converting to chars and strings late.
 *
 * Not thread safe.
 */
public class MultiMap {

    public static class Entry {
        // Wrappers from the head message bytes.
        BBuffer nameB;
        BBuffer valueB;

        CBuffer key = CBuffer.newInstance();
        private CBuffer value = CBuffer.newInstance();

        /**
         * For the first entry with a given name: list of all
         * other entries, including this one, with same name.
         *
         * For second or more: empty list
         */
        public List<Entry> values = new ArrayList<Entry>();

        public void recycle() {
            key.recycle();
            value.recycle();
            //next=null;
            nameB = null;
            valueB = null;
            values.clear();
        }

        public CBuffer getName() {
            if (key.length() == 0 && nameB != null) {
                key.set(nameB);
            }
            return key;
        }

        public CBuffer getValue() {
            if (value.length() == 0 && valueB != null) {
                value.set(valueB);
            }
            return value;
        }

        /** Important - used by values iterator, returns strings
         * from each entry
         */
        public String toString() {
            return getValue().toString();
        }

    }

    // active entries
    protected int count;

    // The key will be converted to lower case
    boolean toLower = false;

    // Some may be inactive - up to count.
    protected List<Entry> entries = new ArrayList<Entry>();

    // 2 options: convert all header/param names to String
    // or use a temp CBuffer to map
    Map<CBuffer, Entry> map =
        new HashMap<CBuffer, Entry>();

    public void recycle() {
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            entry.recycle();
        }
        count = 0;
        map.clear();
    }

    // ----------- Mutations ------------------------

    protected Entry newEntry()  {
        return new Entry();
    }

    /**
     * Adds a partially constructed field entry.
     * Updates count - but will not affect the map.
     */
    private Entry getEntryForAdd() {
        Entry entry;
        if (count >= entries.size()) {
            entry = newEntry();
            entries.add(entry);
        } else {
            entry = entries.get(count);
        }
        count++;
        return entry;
    }


    /** Create a new named header , return the CBuffer
     *  container for the new value
     */
   public Entry addEntry(CharSequence name ) {
       Entry mh = getEntryForAdd();
       mh.getName().append(name);
       if (toLower) {
           mh.getName().toLower();
       }
       updateMap(mh);
       return mh;
   }

   /** Create a new named header , return the CBuffer
    *  container for the new value
    */
   public Entry addEntry(BBuffer name ) {
       Entry mh = getEntryForAdd();
       mh.nameB = name;
       if (toLower) {
           mh.getName().toLower();
       }
       updateMap(mh);

       return mh;
   }

   private void updateMap(Entry mh) {
       Entry topEntry = map.get(mh.getName());

       if (topEntry == null) {
           map.put(mh.getName(), mh);
           mh.values.add(mh);
       } else {
           topEntry.values.add(mh);
       }
   }



    public void remove(CharSequence key) {
        CBucket ckey = key(key);
        Entry entry = getEntry(ckey);
        if (entry != null) {
            map.remove(ckey);

            for (int i = count - 1; i >= 0; i--) {
                entry = entries.get(i);
                if (entry.getName().equals(key)) {
                    entry.recycle();
                    entries.remove(i);
                    count--;
                }
            }
        }
    }

    // --------------- Key-based access --------------
    CBuffer tmpKey = CBuffer.newInstance();

    /**
     * Finds and returns a header field with the given name.  If no such
     * field exists, null is returned.  If more than one such field is
     * in the header, an arbitrary one is returned.
     */
    public CBuffer getHeader(String name) {
        for (int i = 0; i < count; i++) {
            if (entries.get(i).getName().equalsIgnoreCase(name)) {
                return entries.get(i).getValue();
            }
        }
        return null;
    }

    private CBucket key(CharSequence key) {
        if (key instanceof CBucket) {
            CBucket res = (CBucket) key;
            if (!toLower || !res.hasUpper()) {
                return res;
            }
        }
        tmpKey.recycle();
        tmpKey.append(key);
        if (toLower) {
            tmpKey.toLower();
        }
        return tmpKey;
    }

    public Entry getEntry(CharSequence key) {
        Entry entry = map.get(key(key));
        return entry;
    }

    public Entry getEntry(CBucket buf) {
        // lowercase ?
        Entry entry = map.get(buf);
        return entry;
    }

    public Enumeration<String> names() {
        return new IteratorEnumerator(map.keySet().iterator());
    }

    // ----------- Index access --------------

    /**
     *  Number of entries ( including those with same key
     *
     * @return
     */
    public int size() {
        return count;
    }


    public CharSequence getKey(int idx) {
        return entries.get(idx).key;
    }

    public Entry getEntry(int idx) {
        return entries.get(idx);
    }

    /**
     * Returns the Nth header name, or null if there is no such header.
     * This may be used to iterate through all header fields.
     */
    public CBuffer getName(int n) {
        return n < count ? entries.get(n).getName() : null;
    }

    /**
     * Returns the Nth header value, or null if there is no such header.
     * This may be used to iterate through all header fields.
     */
    public CBuffer getValue(int n) {
        return n >= 0 && n < count ? entries.get(n).getValue() : null;
    }

    // ----------- Helpers --------------
    public void add(CharSequence key, CharSequence value) {
        Entry mh = addEntry(key);
        mh.value.append(value);
    }

    /** Create a new named header , return the CBuffer
     * container for the new value
     */
    public CBuffer addValue( String name ) {
        return addEntry(name).getValue();
    }

     public Entry setEntry( String name ) {
         remove(name);
         return addEntry(name);
     }

     public void set(CharSequence key, CharSequence value) {
         remove(key);
         add(key, value);
     }

     public CBuffer setValue( String name ) {
         remove(name);
         return addValue(name);
     }

     public CBuffer get(CharSequence key) {
         Entry entry = getEntry(key);
         return (entry == null) ? null : entry.value;
     }

     public String getString(CharSequence key) {
         Entry entry = getEntry(key);
         return (entry == null) ? null : entry.value.toString();
     }


    // -------------- support classes ----------------

    public static class IteratorEnumerator implements Enumeration<String> {
        private final Iterator keyI;

        public IteratorEnumerator(Iterator iterator) {
            this.keyI = iterator;
        }


        public boolean hasMoreElements() {
            return keyI.hasNext();
        }


        public String nextElement() {
            return keyI.next().toString();
        }

    }

    public static final Enumeration<String> EMPTY =
        new Enumeration<String>() {

            @Override
            public boolean hasMoreElements() {
                return false;
            }

            @Override
            public String nextElement() {
                return null;
            }

    };

    public MultiMap insensitive() {
        toLower = true;
        return this;
    }


}
