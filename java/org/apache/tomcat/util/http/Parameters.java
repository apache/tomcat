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

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.collections.MultiMap;

/**
 * 
 * @author Costin Manolache
 */
public final class Parameters extends MultiMap {

    
    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog(Parameters.class );
    
    // Transition: we'll use the same Hashtable( String->String[] )
    // for the beginning. When we are sure all accesses happen through
    // this class - we can switch to MultiMap
    private Hashtable paramHashStringArray=new Hashtable();
    private boolean didQueryParameters=false;
    private boolean didMerge=false;
    
    MessageBytes queryMB;
    MimeHeaders  headers;

    UDecoder urlDec;
    MessageBytes decodedQuery=MessageBytes.newInstance();
    
    public static final int INITIAL_SIZE=4;

    // Garbage-less parameter merging.
    // In a sub-request with parameters, the new parameters
    // will be stored in child. When a getParameter happens,
    // the 2 are merged togheter. The child will be altered
    // to contain the merged values - the parent is allways the
    // original request.
    private Parameters child=null;
    private Parameters parent=null;
    private Parameters currentChild=null;

    String encoding=null;
    String queryStringEncoding=null;
    
    /**
     * 
     */
    public Parameters() {
	super( INITIAL_SIZE );
    }

    public void setQuery( MessageBytes queryMB ) {
	this.queryMB=queryMB;
    }

    public void setHeaders( MimeHeaders headers ) {
	this.headers=headers;
    }

    public void setEncoding( String s ) {
	encoding=s;
	if(debug>0) log( "Set encoding to " + s );
    }

    public void setQueryStringEncoding( String s ) {
	queryStringEncoding=s;
	if(debug>0) log( "Set query string encoding to " + s );
    }

    public void recycle() {
	super.recycle();
	paramHashStringArray.clear();
	didQueryParameters=false;
	currentChild=null;
	didMerge=false;
	encoding=null;
	decodedQuery.recycle();
    }
    
    // -------------------- Sub-request support --------------------

    public Parameters getCurrentSet() {
	if( currentChild==null )
	    return this;
	return currentChild;
    }
    
    /** Create ( or reuse ) a child that will be used during a sub-request.
	All future changes ( setting query string, adding parameters )
	will affect the child ( the parent request is never changed ).
	Both setters and getters will return the data from the deepest
	child, merged with data from parents.
    */
    public void push() {
	// We maintain a linked list, that will grow to the size of the
	// longest include chain.
	// The list has 2 points of interest:
	// - request.parameters() is the original request and head,
	// - request.parameters().currentChild() is the current set.
	// The ->child and parent<- links are preserved ( currentChild is not
	// the last in the list )
	
	// create a new element in the linked list
	// note that we reuse the child, if any - pop will not
	// set child to null !
	if( currentChild==null ) {
	    currentChild=new Parameters();
	    currentChild.setURLDecoder( urlDec );
	    currentChild.parent=this;
	    return;
	}
	if( currentChild.child==null ) {
	    currentChild.child=new Parameters();
	    currentChild.setURLDecoder( urlDec );
	    currentChild.child.parent=currentChild;
	} // it is not null if this object already had a child
	// i.e. a deeper include() ( we keep it )

	// the head will be the new element.
	currentChild=currentChild.child;
	currentChild.setEncoding( encoding );
    }

    /** Discard the last child. This happens when we return from a
	sub-request and the parameters are locally modified.
     */
    public void pop() {
	if( currentChild==null ) {
	    throw new RuntimeException( "Attempt to pop without a push" );
	}
	currentChild.recycle();
	currentChild=currentChild.parent;
	// don't remove the top.
    }
    
    // -------------------- Data access --------------------
    // Access to the current name/values, no side effect ( processing ).
    // You must explicitely call handleQueryParameters and the post methods.
    
    // This is the original data representation ( hash of String->String[])

    public void addParameterValues( String key, String[] newValues) {
        if ( key==null ) return;
        String values[];
        if (paramHashStringArray.containsKey(key)) {
            String oldValues[] = (String[])paramHashStringArray.get(key);
            values = new String[oldValues.length + newValues.length];
            for (int i = 0; i < oldValues.length; i++) {
                values[i] = oldValues[i];
            }
            for (int i = 0; i < newValues.length; i++) {
                values[i+ oldValues.length] = newValues[i];
            }
        } else {
            values = newValues;
        }

        paramHashStringArray.put(key, values);
    }

    public String[] getParameterValues(String name) {
	handleQueryParameters();
	// sub-request
	if( currentChild!=null ) {
	    currentChild.merge();
	    return (String[])currentChild.paramHashStringArray.get(name);
	}

	// no "facade"
	String values[]=(String[])paramHashStringArray.get(name);
	return values;
    }
 
    public Enumeration getParameterNames() {
	handleQueryParameters();
	// Slow - the original code
	if( currentChild!=null ) {
	    currentChild.merge();
	    return currentChild.paramHashStringArray.keys();
	}

	// merge in child
        return paramHashStringArray.keys();
    }

    /** Combine the parameters from parent with our local ones
     */
    private void merge() {
	// recursive
	if( debug > 0 ) {
	    log("Before merging " + this + " " + parent + " " + didMerge );
	    log(  paramsAsString());
	}
	// Local parameters first - they take precedence as in spec.
	handleQueryParameters();

	// we already merged with the parent
	if( didMerge ) return;

	// we are the top level
	if( parent==null ) return;

	// Add the parent props to the child ( lower precedence )
	parent.merge();
	Hashtable parentProps=parent.paramHashStringArray;
	merge2( paramHashStringArray , parentProps);
	didMerge=true;
	if(debug > 0 )
	    log("After " + paramsAsString());
    }


    // Shortcut.
    public String getParameter(String name ) {
	String[] values = getParameterValues(name);
        if (values != null) {
	    if( values.length==0 ) return "";
            return values[0];
        } else {
	    return null;
        }
    }
    // -------------------- Processing --------------------
    /** Process the query string into parameters
     */
    public void handleQueryParameters() {
	if( didQueryParameters ) return;

	didQueryParameters=true;

	if( queryMB==null || queryMB.isNull() )
	    return;
	
	if( debug > 0  )
	    log( "Decoding query " + decodedQuery + " " + queryStringEncoding);

        try {
            decodedQuery.duplicate( queryMB );
        } catch (IOException e) {
            // Can't happen, as decodedQuery can't overflow
            e.printStackTrace();
        }
        processParameters( decodedQuery, queryStringEncoding );
    }

    // --------------------
    
    /** Combine 2 hashtables into a new one.
     *  ( two will be added to one ).
     *  Used to combine child parameters ( RequestDispatcher's query )
     *  with parent parameters ( original query or parent dispatcher )
     */
    private static void merge2(Hashtable one, Hashtable two ) {
        Enumeration e = two.keys();

	while (e.hasMoreElements()) {
	    String name = (String) e.nextElement();
	    String[] oneValue = (String[]) one.get(name);
	    String[] twoValue = (String[]) two.get(name);
	    String[] combinedValue;

	    if (twoValue == null) {
		continue;
	    } else {
		if( oneValue==null ) {
		    combinedValue = new String[twoValue.length];
		    System.arraycopy(twoValue, 0, combinedValue,
				     0, twoValue.length);
		} else {
		    combinedValue = new String[oneValue.length +
					       twoValue.length];
		    System.arraycopy(oneValue, 0, combinedValue, 0,
				     oneValue.length);
		    System.arraycopy(twoValue, 0, combinedValue,
				     oneValue.length, twoValue.length);
		}
		one.put(name, combinedValue);
	    }
	}
    }

    // incredibly inefficient data representation for parameters,
    // until we test the new one
    private void addParam( String key, String value ) {
	if( key==null ) return;
	String values[];
	if (paramHashStringArray.containsKey(key)) {
	    String oldValues[] = (String[])paramHashStringArray.
		get(key);
	    values = new String[oldValues.length + 1];
	    for (int i = 0; i < oldValues.length; i++) {
		values[i] = oldValues[i];
	    }
	    values[oldValues.length] = value;
	} else {
	    values = new String[1];
	    values[0] = value;
	}
	
	
	paramHashStringArray.put(key, values);
    }

    public void setURLDecoder( UDecoder u ) {
	urlDec=u;
    }

    // -------------------- Parameter parsing --------------------

    // This code is not used right now - it's the optimized version
    // of the above.

    // we are called from a single thread - we can do it the hard way
    // if needed
    ByteChunk tmpName=new ByteChunk();
    ByteChunk tmpValue=new ByteChunk();
    CharChunk tmpNameC=new CharChunk(1024);
    CharChunk tmpValueC=new CharChunk(1024);
    
    public void processParameters( byte bytes[], int start, int len ) {
        processParameters(bytes, start, len, encoding);
    }

    public void processParameters( byte bytes[], int start, int len, 
                                   String enc ) {
	int end=start+len;
	int pos=start;
	
	if( debug>0 ) 
	    log( "Bytes: " + new String( bytes, start, len ));

        do {
	    boolean noEq=false;
	    int valStart=-1;
	    int valEnd=-1;
	    
	    int nameStart=pos;
	    int nameEnd=ByteChunk.indexOf(bytes, nameStart, end, '=' );
	    // Workaround for a&b&c encoding
	    int nameEnd2=ByteChunk.indexOf(bytes, nameStart, end, '&' );
	    if( (nameEnd2!=-1 ) &&
		( nameEnd==-1 || nameEnd > nameEnd2) ) {
		nameEnd=nameEnd2;
		noEq=true;
		valStart=nameEnd;
		valEnd=nameEnd;
		if( debug>0) log("no equal " + nameStart + " " + nameEnd + " " + new String(bytes, nameStart, nameEnd-nameStart) );
	    }
	    if( nameEnd== -1 ) 
		nameEnd=end;

	    if( ! noEq ) {
		valStart= (nameEnd < end) ? nameEnd+1 : end;
		valEnd=ByteChunk.indexOf(bytes, valStart, end, '&');
		if( valEnd== -1 ) valEnd = (valStart < end) ? end : valStart;
	    }
	    
	    pos=valEnd+1;
	    
	    if( nameEnd<=nameStart ) {
		continue;
		// invalid chunk - it's better to ignore
		// XXX log it ?
	    }
	    tmpName.setBytes( bytes, nameStart, nameEnd-nameStart );
	    tmpValue.setBytes( bytes, valStart, valEnd-valStart );

            try {
                addParam( urlDecode(tmpName, enc), urlDecode(tmpValue, enc) );
            } catch (IOException e) {
                // Exception during character decoding: skip parameter
            }

	    tmpName.recycle();
	    tmpValue.recycle();

	} while( pos<end );
    }

    private String urlDecode(ByteChunk bc, String enc)
        throws IOException {
        if( urlDec==null ) {
            urlDec=new UDecoder();   
        }
        urlDec.convert(bc);
        String result = null;
        if (enc != null) {
            bc.setEncoding(enc);
            result = bc.toString();
        } else {
            CharChunk cc = tmpNameC;
            cc.allocate(bc.getLength(), -1);
            // Default encoding: fast conversion
            byte[] bbuf = bc.getBuffer();
            char[] cbuf = cc.getBuffer();
            int start = bc.getStart();
            for (int i = 0; i < bc.getLength(); i++) {
                cbuf[i] = (char) (bbuf[i + start] & 0xff);
            }
            cc.setChars(cbuf, 0, bc.getLength());
            result = cc.toString();
            cc.recycle();
        }
        return result;
    }

    public void processParameters( char chars[], int start, int len ) {
	int end=start+len;
	int pos=start;
	
	if( debug>0 ) 
	    log( "Chars: " + new String( chars, start, len ));
        do {
	    boolean noEq=false;
	    int nameStart=pos;
	    int valStart=-1;
	    int valEnd=-1;
	    
	    int nameEnd=CharChunk.indexOf(chars, nameStart, end, '=' );
	    int nameEnd2=CharChunk.indexOf(chars, nameStart, end, '&' );
	    if( (nameEnd2!=-1 ) &&
		( nameEnd==-1 || nameEnd > nameEnd2) ) {
		nameEnd=nameEnd2;
		noEq=true;
		valStart=nameEnd;
		valEnd=nameEnd;
		if( debug>0) log("no equal " + nameStart + " " + nameEnd + " " + new String(chars, nameStart, nameEnd-nameStart) );
	    }
	    if( nameEnd== -1 ) nameEnd=end;
	    
	    if( ! noEq ) {
		valStart= (nameEnd < end) ? nameEnd+1 : end;
		valEnd=CharChunk.indexOf(chars, valStart, end, '&');
		if( valEnd== -1 ) valEnd = (valStart < end) ? end : valStart;
	    }
	    
	    pos=valEnd+1;
	    
	    if( nameEnd<=nameStart ) {
		continue;
		// invalid chunk - no name, it's better to ignore
		// XXX log it ?
	    }
	    
	    try {
		tmpNameC.append( chars, nameStart, nameEnd-nameStart );
		tmpValueC.append( chars, valStart, valEnd-valStart );

		if( debug > 0 )
		    log( tmpNameC + "= " + tmpValueC);

		if( urlDec==null ) {
		    urlDec=new UDecoder();   
		}

		urlDec.convert( tmpNameC );
		urlDec.convert( tmpValueC );

		if( debug > 0 )
		    log( tmpNameC + "= " + tmpValueC);
		
		addParam( tmpNameC.toString(), tmpValueC.toString() );
	    } catch( IOException ex ) {
		ex.printStackTrace();
	    }

	    tmpNameC.recycle();
	    tmpValueC.recycle();

	} while( pos<end );
    }
    
    public void processParameters( MessageBytes data ) {
        processParameters(data, encoding);
    }

    public void processParameters( MessageBytes data, String encoding ) {
	if( data==null || data.isNull() || data.getLength() <= 0 ) return;

	if( data.getType() == MessageBytes.T_BYTES ) {
	    ByteChunk bc=data.getByteChunk();
	    processParameters( bc.getBytes(), bc.getOffset(),
			       bc.getLength(), encoding);
	} else {
	    if (data.getType()!= MessageBytes.T_CHARS ) 
		data.toChars();
	    CharChunk cc=data.getCharChunk();
	    processParameters( cc.getChars(), cc.getOffset(),
			       cc.getLength());
	}
    }

    /** Debug purpose
     */
    public String paramsAsString() {
	StringBuffer sb=new StringBuffer();
	Enumeration en= paramHashStringArray.keys();
	while( en.hasMoreElements() ) {
	    String k=(String)en.nextElement();
	    sb.append( k ).append("=");
	    String v[]=(String[])paramHashStringArray.get( k );
	    for( int i=0; i<v.length; i++ )
		sb.append( v[i] ).append(",");
	    sb.append("\n");
	}
	return sb.toString();
    }

    private static int debug=0;
    private void log(String s ) {
        if (log.isDebugEnabled())
            log.debug("Parameters: " + s );
    }
   
    // -------------------- Old code, needs rewrite --------------------
    
    /** Used by RequestDispatcher
     */
    public void processParameters( String str ) {
	int end=str.length();
	int pos=0;
	if( debug > 0)
	    log("String: " + str );
	
        do {
	    boolean noEq=false;
	    int valStart=-1;
	    int valEnd=-1;
	    
	    int nameStart=pos;
	    int nameEnd=str.indexOf('=', nameStart );
	    int nameEnd2=str.indexOf('&', nameStart );
	    if( nameEnd2== -1 ) nameEnd2=end;
	    if( (nameEnd2!=-1 ) &&
		( nameEnd==-1 || nameEnd > nameEnd2) ) {
		nameEnd=nameEnd2;
		noEq=true;
		valStart=nameEnd;
		valEnd=nameEnd;
		if( debug>0) log("no equal " + nameStart + " " + nameEnd + " " + str.substring(nameStart, nameEnd) );
	    }

	    if( nameEnd== -1 ) nameEnd=end;

	    if( ! noEq ) {
		valStart=nameEnd+1;
		valEnd=str.indexOf('&', valStart);
		if( valEnd== -1 ) valEnd = (valStart < end) ? end : valStart;
	    }
	    
	    pos=valEnd+1;
	    
	    if( nameEnd<=nameStart ) {
		continue;
	    }
	    if( debug>0)
		log( "XXX " + nameStart + " " + nameEnd + " "
		     + valStart + " " + valEnd );
	    
	    try {
		tmpNameC.append(str, nameStart, nameEnd-nameStart );
		tmpValueC.append(str, valStart, valEnd-valStart );
	    
		if( debug > 0 )
		    log( tmpNameC + "= " + tmpValueC);

		if( urlDec==null ) {
		    urlDec=new UDecoder();   
		}

		urlDec.convert( tmpNameC );
		urlDec.convert( tmpValueC );

		if( debug > 0 )
		    log( tmpNameC + "= " + tmpValueC);
		
		addParam( tmpNameC.toString(), tmpValueC.toString() );
	    } catch( IOException ex ) {
		ex.printStackTrace();
	    }

	    tmpNameC.recycle();
	    tmpValueC.recycle();

	} while( pos<end );
    }


}
