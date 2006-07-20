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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;

import org.apache.tomcat.util.buf.MessageBytes;

/* XXX XXX XXX Need a major rewrite  !!!!
 */

/**
 * This class is used to contain standard internet message headers,
 * used for SMTP (RFC822) and HTTP (RFC2068) messages as well as for
 * MIME (RFC 2045) applications such as transferring typed data and
 * grouping related items in multipart message bodies.
 *
 * <P> Message headers, as specified in RFC822, include a field name
 * and a field body.  Order has no semantic significance, and several
 * fields with the same name may exist.  However, most fields do not
 * (and should not) exist more than once in a header.
 *
 * <P> Many kinds of field body must conform to a specified syntax,
 * including the standard parenthesized comment syntax.  This class
 * supports only two simple syntaxes, for dates and integers.
 *
 * <P> When processing headers, care must be taken to handle the case of
 * multiple same-name fields correctly.  The values of such fields are
 * only available as strings.  They may be accessed by index (treating
 * the header as an array of fields), or by name (returning an array
 * of string values).
 */

/* Headers are first parsed and stored in the order they are
   received. This is based on the fact that most servlets will not
   directly access all headers, and most headers are single-valued.
   ( the alternative - a hash or similar data structure - will add
   an overhead that is not needed in most cases )
   
   Apache seems to be using a similar method for storing and manipulating
   headers.
       
   Future enhancements:
   - hash the headers the first time a header is requested ( i.e. if the
   servlet needs direct access to headers).
   - scan "common" values ( length, cookies, etc ) during the parse
   ( addHeader hook )
   
*/
    


/**
 *  Memory-efficient repository for Mime Headers. When the object is recycled, it
 *  will keep the allocated headers[] and all the MimeHeaderField - no GC is generated.
 *
 *  For input headers it is possible to use the MessageByte for Fileds - so no GC
 *  will be generated.
 *
 *  The only garbage is generated when using the String for header names/values -
 *  this can't be avoided when the servlet calls header methods, but is easy
 *  to avoid inside tomcat. The goal is to use _only_ MessageByte-based Fields,
 *  and reduce to 0 the memory overhead of tomcat.
 *
 *  TODO:
 *  XXX one-buffer parsing - for http ( other protocols don't need that )
 *  XXX remove unused methods
 *  XXX External enumerations, with 0 GC.
 *  XXX use HeaderName ID
 *  
 * 
 * @author dac@eng.sun.com
 * @author James Todd [gonzo@eng.sun.com]
 * @author Costin Manolache
 * @author kevin seguin
 */
public class MimeHeaders {
    /** Initial size - should be == average number of headers per request
     *  XXX  make it configurable ( fine-tuning of web-apps )
     */
    public static final int DEFAULT_HEADER_SIZE=8;
    
    /**
     * The header fields.
     */
    private MimeHeaderField[] headers = new
	MimeHeaderField[DEFAULT_HEADER_SIZE];

    /**
     * The current number of header fields.
     */
    private int count;

    /**
     * Creates a new MimeHeaders object using a default buffer size.
     */
    public MimeHeaders() {
    }

    /**
     * Clears all header fields.
     */
    // [seguin] added for consistency -- most other objects have recycle().
    public void recycle() {
        clear();
    }

    /**
     * Clears all header fields.
     */
    public void clear() {
	for (int i = 0; i < count; i++) {
	    headers[i].recycle();
	}
	count = 0;
    }

    /**
     * EXPENSIVE!!!  only for debugging.
     */
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=== MimeHeaders ===");
        Enumeration e = names();
        while (e.hasMoreElements()) {
            String n = (String)e.nextElement();
            pw.println(n + " = " + getHeader(n));
        }
        return sw.toString();
    }

    // -------------------- Idx access to headers ----------
    
    /**
     * Returns the current number of header fields.
     */
    public int size() {
	return count;
    }

    /**
     * Returns the Nth header name, or null if there is no such header.
     * This may be used to iterate through all header fields.
     */
    public MessageBytes getName(int n) {
	return n >= 0 && n < count ? headers[n].getName() : null;
    }

    /**
     * Returns the Nth header value, or null if there is no such header.
     * This may be used to iterate through all header fields.
     */
    public MessageBytes getValue(int n) {
	return n >= 0 && n < count ? headers[n].getValue() : null;
    }

    /** Find the index of a header with the given name.
     */
    public int findHeader( String name, int starting ) {
	// We can use a hash - but it's not clear how much
	// benefit you can get - there is an  overhead 
	// and the number of headers is small (4-5 ?)
	// Another problem is that we'll pay the overhead
	// of constructing the hashtable

	// A custom search tree may be better
        for (int i = starting; i < count; i++) {
	    if (headers[i].getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
    
    // -------------------- --------------------

    /**
     * Returns an enumeration of strings representing the header field names.
     * Field names may appear multiple times in this enumeration, indicating
     * that multiple fields with that name exist in this header.
     */
    public Enumeration names() {
	return new NamesEnumerator(this);
    }

    public Enumeration values(String name) {
	return new ValuesEnumerator(this, name);
    }

    // -------------------- Adding headers --------------------
    

    /**
     * Adds a partially constructed field to the header.  This
     * field has not had its name or value initialized.
     */
    private MimeHeaderField createHeader() {
	MimeHeaderField mh;
	int len = headers.length;
	if (count >= len) {
	    // expand header list array
	    MimeHeaderField tmp[] = new MimeHeaderField[count * 2];
	    System.arraycopy(headers, 0, tmp, 0, len);
	    headers = tmp;
	}
	if ((mh = headers[count]) == null) {
	    headers[count] = mh = new MimeHeaderField();
	}
	count++;
	return mh;
    }

    /** Create a new named header , return the MessageBytes
	container for the new value
    */
    public MessageBytes addValue( String name ) {
 	MimeHeaderField mh = createHeader();
	mh.getName().setString(name);
	return mh.getValue();
    }

    /** Create a new named header using un-translated byte[].
	The conversion to chars can be delayed until
	encoding is known.
     */
    public MessageBytes addValue(byte b[], int startN, int len)
    {
	MimeHeaderField mhf=createHeader();
	mhf.getName().setBytes(b, startN, len);
	return mhf.getValue();
    }

    /** Create a new named header using translated char[].
     */
    public MessageBytes addValue(char c[], int startN, int len)
    {
	MimeHeaderField mhf=createHeader();
	mhf.getName().setChars(c, startN, len);
	return mhf.getValue();
    }

    /** Allow "set" operations - 
        return a MessageBytes container for the
	header value ( existing header or new
	if this .
    */
    public MessageBytes setValue( String name ) {
        for ( int i = 0; i < count; i++ ) {
            if(headers[i].getName().equalsIgnoreCase(name)) {
                for ( int j=i+1; j < count; j++ ) {
                    if(headers[j].getName().equalsIgnoreCase(name)) {
                        removeHeader(j--);
                    }
                }
                return headers[i].getValue();
            }
        }
        MimeHeaderField mh = createHeader();
        mh.getName().setString(name);
        return mh.getValue();
    }

    //-------------------- Getting headers --------------------
    /**
     * Finds and returns a header field with the given name.  If no such
     * field exists, null is returned.  If more than one such field is
     * in the header, an arbitrary one is returned.
     */
    public MessageBytes getValue(String name) {
        for (int i = 0; i < count; i++) {
	    if (headers[i].getName().equalsIgnoreCase(name)) {
                return headers[i].getValue();
            }
        }
        return null;
    }

    // bad shortcut - it'll convert to string ( too early probably,
    // encoding is guessed very late )
    public String getHeader(String name) {
	MessageBytes mh = getValue(name);
	return mh != null ? mh.toString() : null;
    }

    // -------------------- Removing --------------------
    /**
     * Removes a header field with the specified name.  Does nothing
     * if such a field could not be found.
     * @param name the name of the header field to be removed
     */
    public void removeHeader(String name) {
        // XXX
        // warning: rather sticky code; heavily tuned

        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCase(name)) {
                removeHeader(i--);
            }
        }
    }

    /**
     * reset and swap with last header
     * @param idx the index of the header to remove.
     */
    private void removeHeader(int idx) {
        MimeHeaderField mh = headers[idx];
        
        mh.recycle();
        headers[idx] = headers[count - 1];
        headers[count - 1] = mh;
        count--;
    }

}

/** Enumerate the distinct header names.
    Each nextElement() is O(n) ( a comparation is
    done with all previous elements ).

    This is less frequesnt than add() -
    we want to keep add O(1).
*/
class NamesEnumerator implements Enumeration {
    int pos;
    int size;
    String next;
    MimeHeaders headers;

    NamesEnumerator(MimeHeaders headers) {
	this.headers=headers;
	pos=0;
	size = headers.size();
	findNext();
    }

    private void findNext() {
	next=null;
	for(  ; pos< size; pos++ ) {
	    next=headers.getName( pos ).toString();
	    for( int j=0; j<pos ; j++ ) {
		if( headers.getName( j ).equalsIgnoreCase( next )) {
		    // duplicate.
		    next=null;
		    break;
		}
	    }
	    if( next!=null ) {
		// it's not a duplicate
		break;
	    }
	}
	// next time findNext is called it will try the
	// next element
	pos++;
    }
    
    public boolean hasMoreElements() {
	return next!=null;
    }

    public Object nextElement() {
	String current=next;
	findNext();
	return current;
    }
}

/** Enumerate the values for a (possibly ) multiple
    value element.
*/
class ValuesEnumerator implements Enumeration {
    int pos;
    int size;
    MessageBytes next;
    MimeHeaders headers;
    String name;

    ValuesEnumerator(MimeHeaders headers, String name) {
        this.name=name;
	this.headers=headers;
	pos=0;
	size = headers.size();
	findNext();
    }

    private void findNext() {
	next=null;
	for( ; pos< size; pos++ ) {
	    MessageBytes n1=headers.getName( pos );
	    if( n1.equalsIgnoreCase( name )) {
		next=headers.getValue( pos );
		break;
	    }
	}
	pos++;
    }
    
    public boolean hasMoreElements() {
	return next!=null;
    }

    public Object nextElement() {
	MessageBytes current=next;
	findNext();
	return current.toString();
    }
}

class MimeHeaderField {
    // multiple headers with same name - a linked list will
    // speed up name enumerations and search ( both cpu and
    // GC)
    MimeHeaderField next;
    MimeHeaderField prev; 
    
    protected final MessageBytes nameB = MessageBytes.newInstance();
    protected final MessageBytes valueB = MessageBytes.newInstance();

    /**
     * Creates a new, uninitialized header field.
     */
    public MimeHeaderField() {
    }

    public void recycle() {
	nameB.recycle();
	valueB.recycle();
	next=null;
    }

    public MessageBytes getName() {
	return nameB;
    }

    public MessageBytes getValue() {
	return valueB;
    }
}
