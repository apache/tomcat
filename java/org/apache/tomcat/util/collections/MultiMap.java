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

package org.apache.tomcat.util.collections;

import org.apache.tomcat.util.buf.MessageBytes;

// Originally MimeHeaders

/**
 * An efficient representation for certain type of map. The keys 
 * can have a single or multi values, but most of the time there are
 * single values.
 *
 * The data is of "MessageBytes" type, meaning bytes[] that can be
 * converted to Strings ( if needed, and encoding is lazy-binded ).
 *
 * This is a base class for MimeHeaders, Parameters and Cookies.
 *
 * Data structures: each field is a single-valued key/value.
 * The fields are allocated when needed, and are recycled.
 * The current implementation does linear search, in future we'll
 * also use the hashkey.
 * 
 * @author dac@eng.sun.com
 * @author James Todd [gonzo@eng.sun.com]
 * @author Costin Manolache
 */
public class MultiMap {

    protected Field[] fields;
    // fields in use
    protected int count;

    /**
     * 
     */
    public MultiMap(int initial_size) {
	fields=new Field[initial_size];
    }

    /**
     * Clears all header fields.
     */
    public void recycle() {
	for (int i = 0; i < count; i++) {
	    fields[i].recycle();
	}
	count = 0;
    }

    // -------------------- Idx access to headers ----------
    // This allows external iterators.
    
    /**
     * Returns the current number of header fields.
     */
    public int size() {
	return count;
    }

    /**
     * Returns the Nth header name
     * This may be used to iterate through all header fields.
     *
     * An exception is thrown if the index is not valid ( <0 or >size )
     */
    public MessageBytes getName(int n) {
	// n >= 0 && n < count ? headers[n].getName() : null
	return fields[n].name;
    }

    /**
     * Returns the Nth header value
     * This may be used to iterate through all header fields.
     */
    public MessageBytes getValue(int n) {
	return fields[n].value;
    }

    /** Find the index of a field with the given name.
     */
    public int find( String name, int starting ) {
	// We can use a hash - but it's not clear how much
	// benefit you can get - there is an  overhead 
	// and the number of headers is small (4-5 ?)
	// Another problem is that we'll pay the overhead
	// of constructing the hashtable

	// A custom search tree may be better
        for (int i = starting; i < count; i++) {
	    if (fields[i].name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** Find the index of a field with the given name.
     */
    public int findIgnoreCase( String name, int starting ) {
	// We can use a hash - but it's not clear how much
	// benefit you can get - there is an  overhead 
	// and the number of headers is small (4-5 ?)
	// Another problem is that we'll pay the overhead
	// of constructing the hashtable

	// A custom search tree may be better
        for (int i = starting; i < count; i++) {
	    if (fields[i].name.equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Removes the field at the specified position.  
     *
     * MultiMap will preserve the order of field add unless remove()
     * is called. This is not thread-safe, and will invalidate all
     * iterators. 
     *
     * This is not a frequent operation for Headers and Parameters -
     * there are better ways ( like adding a "isValid" field )
     */
    public void remove( int i ) {
	// reset and swap with last header
	Field mh = fields[i];
	// reset the field
	mh.recycle();
	
	fields[i] = fields[count - 1];
	fields[count - 1] = mh;
	count--;
    }

    /** Create a new, unitialized entry. 
     */
    public int addField() {
	int len = fields.length;
	int pos=count;
	if (count >= len) {
	    // expand header list array
	    Field tmp[] = new Field[pos * 2];
	    System.arraycopy(fields, 0, tmp, 0, len);
	    fields = tmp;
	}
	if (fields[pos] == null) {
	    fields[pos] = new Field();
	}
	count++;
	return pos;
    }

    public MessageBytes get( String name) {
        for (int i = 0; i < count; i++) {
	    if (fields[i].name.equals(name)) {
		return fields[i].value;
	    }
	}
        return null;
    }

    public int findFirst( String name ) {
        for (int i = 0; i < count; i++) {
	    if (fields[i].name.equals(name)) {
		return i;
	    }
	}
        return -1;
    }

    public int findNext( int startPos ) {
	int next= fields[startPos].nextPos;
	if( next != MultiMap.NEED_NEXT ) {
	    return next;
	}

	// next==NEED_NEXT, we never searched for this header
	MessageBytes name=fields[startPos].name;
        for (int i = startPos; i < count; i++) {
	    if (fields[i].name.equals(name)) {
		// cache the search result
		fields[startPos].nextPos=i;
		return i;
	    }
	}
	fields[startPos].nextPos= MultiMap.LAST;
        return -1;
    }

    // workaround for JDK1.1.8/solaris
    static final int NEED_NEXT=-2;
    static final int LAST=-1;

    // -------------------- Internal representation --------------------
    final class Field {
	MessageBytes name;
	MessageBytes value;

	// Extra info for speed
	
	//  multiple fields with same name - a linked list will
	// speed up multiple name enumerations and search.
	int nextPos;

	// hashkey
	int hash;
	Field nextSameHash;

	Field() {
	    nextPos=MultiMap.NEED_NEXT;
	}
	
	void recycle() {
	    name.recycle();
	    value.recycle();
	    nextPos=MultiMap.NEED_NEXT;
	}
    }
}
