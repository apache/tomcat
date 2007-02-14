package org.apache.tomcat.util.net;

import java.net.Socket;
import java.net.SocketException;

public class SocketProperties {
    /**
     * Enable/disable direct buffers for the network buffers
     * Default value is enabled
     */
    protected boolean directBuffer = true;
    /**
     * Socket receive buffer size in bytes (SO_RCVBUF)
     * Default value is 25188
     */
    protected int rxBufSize = 25188;
    /**
     * Socket send buffer size in bytes (SO_SNDBUF)
     * Default value is 43800
     */
    protected int txBufSize = 43800;
    
    /**
     * NioChannel pool size for the endpoint,
     * this value is how many channels
     * -1 means unlimited cached, 0 means no cache
     * Default value is 500
     */
    protected int bufferPool = 500;
    

    /**
     * Buffer pool size in bytes to be cached
     * -1 means unlimited, 0 means no cache
     * Default value is 100MB (1024*1024*100 bytes)
     */
    protected int bufferPoolSize = 1024*1024*100;
    
    /**
     * TCP_NO_DELAY option, default is true
     */
    protected boolean tcpNoDelay = true;
    /**
     * SO_KEEPALIVE option, default is false
     */
    protected boolean soKeepAlive = false;
    /**
     * OOBINLINE option, default is true
     */
    protected boolean ooBInline = true;
    /**
     * SO_REUSEADDR option, default is true
     */
    protected boolean soReuseAddress = true;
    /**
     * SO_LINGER option, default is true, paired with the <code>soLingerTime</code> value
     */
    protected boolean soLingerOn = true;
    /**
     * SO_LINGER option, default is 25 seconds.
     */
    protected int soLingerTime = 25;
    /**
     * SO_TIMEOUT option, default is 5000 milliseconds
     */
    protected int soTimeout = 5000;
    /**
     * Traffic class option, value between 0 and 255
     * IPTOS_LOWCOST (0x02)
     * IPTOS_RELIABILITY (0x04)
     * IPTOS_THROUGHPUT (0x08)
     * IPTOS_LOWDELAY (0x10)
     * Default value is 0x04 | 0x08 | 0x010
     */
    protected int soTrafficClass = 0x04 | 0x08 | 0x010;
    /**
     * Performance preferences according to
     * http://java.sun.com/j2se/1.5.0/docs/api/java/net/Socket.html#setPerformancePreferences(int,%20int,%20int)
     * Default value is 1
     */
    protected int performanceConnectionTime = 1;
    /**
     * Performance preferences according to
     * http://java.sun.com/j2se/1.5.0/docs/api/java/net/Socket.html#setPerformancePreferences(int,%20int,%20int)
     * Default value is 0
     */
    protected int performanceLatency = 0;
    /**
     * Performance preferences according to
     * http://java.sun.com/j2se/1.5.0/docs/api/java/net/Socket.html#setPerformancePreferences(int,%20int,%20int)
     * Default value is 1
     */
    protected int performanceBandwidth = 1;
    

    public void setProperties(Socket socket) throws SocketException{
        socket.setReceiveBufferSize(rxBufSize);
        socket.setSendBufferSize(txBufSize);
        socket.setOOBInline(ooBInline);
        socket.setKeepAlive(soKeepAlive);
        socket.setPerformancePreferences(performanceConnectionTime,performanceLatency,performanceBandwidth);
        socket.setReuseAddress(soReuseAddress);
        socket.setSoLinger(soLingerOn,soLingerTime);
        socket.setSoTimeout(soTimeout);
        socket.setTcpNoDelay(tcpNoDelay);
        socket.setTrafficClass(soTrafficClass);
    }

    public boolean getDirectBuffer() {
        return directBuffer;
    }

    public boolean getOoBInline() {
        return ooBInline;
    }

    public int getPerformanceBandwidth() {
        return performanceBandwidth;
    }

    public int getPerformanceConnectionTime() {
        return performanceConnectionTime;
    }

    public int getPerformanceLatency() {
        return performanceLatency;
    }

    public int getRxBufSize() {
        return rxBufSize;
    }

    public boolean getSoKeepAlive() {
        return soKeepAlive;
    }

    public boolean getSoLingerOn() {
        return soLingerOn;
    }

    public int getSoLingerTime() {
        return soLingerTime;
    }

    public boolean getSoReuseAddress() {
        return soReuseAddress;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public int getSoTrafficClass() {
        return soTrafficClass;
    }

    public boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    public int getTxBufSize() {
        return txBufSize;
    }

    public int getBufferPool() {
        return bufferPool;
    }

    public int getBufferPoolSize() {
        return bufferPoolSize;
    }

    public int getDirectBufferPool() {
        return bufferPool;
    }

    public void setPerformanceConnectionTime(int performanceConnectionTime) {
        this.performanceConnectionTime = performanceConnectionTime;
    }

    public void setTxBufSize(int txBufSize) {
        this.txBufSize = txBufSize;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public void setSoTrafficClass(int soTrafficClass) {
        this.soTrafficClass = soTrafficClass;
    }

    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public void setSoReuseAddress(boolean soReuseAddress) {
        this.soReuseAddress = soReuseAddress;
    }

    public void setSoLingerTime(int soLingerTime) {
        this.soLingerTime = soLingerTime;
    }

    public void setSoKeepAlive(boolean soKeepAlive) {
        this.soKeepAlive = soKeepAlive;
    }

    public void setRxBufSize(int rxBufSize) {
        this.rxBufSize = rxBufSize;
    }

    public void setPerformanceLatency(int performanceLatency) {
        this.performanceLatency = performanceLatency;
    }

    public void setPerformanceBandwidth(int performanceBandwidth) {
        this.performanceBandwidth = performanceBandwidth;
    }

    public void setOoBInline(boolean ooBInline) {
        this.ooBInline = ooBInline;
    }

    public void setDirectBuffer(boolean directBuffer) {
        this.directBuffer = directBuffer;
    }

    public void setSoLingerOn(boolean soLingerOn) {
        this.soLingerOn = soLingerOn;
    }

    public void setBufferPool(int bufferPool) {
        this.bufferPool = bufferPool;
    }

    public void setBufferPoolSize(int bufferPoolSize) {
        this.bufferPoolSize = bufferPoolSize;
    }

    public void setDirectBufferPool(int directBufferPool) {
        this.bufferPool = directBufferPool;
    }

}