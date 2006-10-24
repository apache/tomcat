package org.apache.tomcat.util.net;

import java.net.Socket;
import java.net.SocketException;

public class SocketProperties {
    protected boolean directBuffer = true;
    protected int rxBufSize = 25188;
    protected int txBufSize = 43800;
    protected boolean tcpNoDelay = false;
    protected boolean soKeepAlive = false;
    protected boolean ooBInline = true;
    protected boolean soReuseAddress = true;
    protected boolean soLingerOn = true;
    protected int soLingerTime = 10;
    protected int soTimeout = 5000;
    protected int soTrafficClass = 0x04 | 0x08 | 0x010;
    protected int performanceConnectionTime = 1;
    protected int performanceLatency = 1;
    protected int performanceBandwidth = 1;
    private Socket properties;

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

    public Socket getProperties() {
        return properties;
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

}