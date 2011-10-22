/*
 */
package org.apache.tomcat.lite.io;

import java.nio.ByteBuffer;



/**
 * Holds raw data. Similar interface with a ByteBuffer in 'channel write'
 * or 'read mode'. Data is between position and limit - there is no
 * switching.
 *
 * TODO: FileBucket, DirectBufferBucket, CharBucket, ...
 *
 * @author Costin Manolache
 */
public interface BBucket {

    public void release();

    public byte[] array();
    public int position();
    public int remaining();
    public int limit();

    public boolean hasRemaining();

    public void position(int newStart);

    /**
     * Return a byte buffer, with data between position and limit.
     * Changes in the ByteBuffer position will not be reflected
     * in the IOBucket.
     *
     * @return
     */
    public ByteBuffer getByteBuffer();


}