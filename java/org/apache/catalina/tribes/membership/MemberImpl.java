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

package org.apache.catalina.tribes.membership;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.io.XByteBuffer;
import org.apache.catalina.tribes.transport.SenderState;
import org.apache.catalina.tribes.util.StringManager;

/**
 * A <b>membership</b> implementation using simple multicast.
 * This is the representation of a multicast member.
 * Carries the host, and port of the this or other cluster nodes.
 */
public class MemberImpl implements Member, java.io.Externalizable {

    /**
     * Should a call to getName or getHostName try to do a DNS lookup?
     * default is false
     */
    public static final boolean DO_DNS_LOOKUPS = Boolean.parseBoolean(System.getProperty("org.apache.catalina.tribes.dns_lookups","false"));

    public static final transient byte[] TRIBES_MBR_BEGIN = new byte[] {84, 82, 73, 66, 69, 83, 45, 66, 1, 0};
    public static final transient byte[] TRIBES_MBR_END   = new byte[] {84, 82, 73, 66, 69, 83, 45, 69, 1, 0};
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * The listen host for this member
     */
    protected volatile byte[] host;
    protected volatile transient String hostname;
    /**
     * The tcp listen port for this member
     */
    protected volatile int port;
    /**
     * The udp listen port for this member
     */
    protected volatile int udpPort = -1;

    /**
     * The tcp/SSL listen port for this member
     */
    protected volatile int securePort = -1;

    /**
     * Counter for how many broadcast messages have been sent from this member
     */
    protected int msgCount = 0;
    /**
     * The number of milliseconds since this member was
     * created, is kept track of using the start time
     */
    protected long memberAliveTime = 0;

    /**
     * For the local member only
     */
    protected transient long serviceStartTime;

    /**
     * To avoid serialization over and over again, once the local dataPkg
     * has been set, we use that to transmit data
     */
    protected transient byte[] dataPkg = null;

    /**
     * Unique session Id for this member
     */
    protected volatile byte[] uniqueId = new byte[16];

    /**
     * Custom payload that an app framework can broadcast
     * Also used to transport stop command.
     */
    protected volatile byte[] payload = new byte[0];

    /**
     * Command, so that the custom payload doesn't have to be used
     * This is for internal tribes use, such as SHUTDOWN_COMMAND
     */
    protected volatile byte[] command = new byte[0];

    /**
     * Domain if we want to filter based on domain.
     */
    protected volatile byte[] domain = new byte[0];

    /**
     * Empty constructor for serialization
     */
    public MemberImpl() {

    }

    /**
     * Construct a new member object.
     *
     * @param host - the tcp listen host
     * @param port - the tcp listen port
     * @param aliveTime - the number of milliseconds since this member was created
     *
     * @throws IOException If there is an error converting the host name to an
     *                     IP address
     */
    public MemberImpl(String host,
                      int port,
                      long aliveTime) throws IOException {
        setHostname(host);
        this.port = port;
        this.memberAliveTime=aliveTime;
    }

    public MemberImpl(String host,
                      int port,
                      long aliveTime,
                      byte[] payload) throws IOException {
        this(host,port,aliveTime);
        setPayload(payload);
    }

    @Override
    public boolean isReady() {
        return SenderState.getSenderState(this).isReady();
    }
    @Override
    public boolean isSuspect() {
        return SenderState.getSenderState(this).isSuspect();
    }
    @Override
    public boolean isFailing() {
        return SenderState.getSenderState(this).isFailing();
    }

    /**
     * Increment the message count.
     */
    protected void inc() {
        msgCount++;
    }

    /**
     * Create a data package to send over the wire representing this member.
     * This is faster than serialization.
     * @return - the bytes for this member deserialized
     */
    public byte[] getData()  {
        return getData(true);
    }


    @Override
    public byte[] getData(boolean getalive)  {
        return getData(getalive,false);
    }


    @Override
    public synchronized int getDataLength() {
        return TRIBES_MBR_BEGIN.length+ //start pkg
               4+ //data length
               8+ //alive time
               4+ //port
               4+ //secure port
               4+ //udp port
               1+ //host length
               host.length+ //host
               4+ //command length
               command.length+ //command
               4+ //domain length
               domain.length+ //domain
               16+ //unique id
               4+ //payload length
               payload.length+ //payload
               TRIBES_MBR_END.length; //end pkg
    }


    @Override
    public synchronized byte[] getData(boolean getalive, boolean reset)  {
        if ( reset ) dataPkg = null;
        //look in cache first
        if ( dataPkg!=null ) {
            if ( getalive ) {
                //you'd be surprised, but System.currentTimeMillis
                //shows up on the profiler
                long alive=System.currentTimeMillis()-getServiceStartTime();
                XByteBuffer.toBytes(alive, dataPkg, TRIBES_MBR_BEGIN.length+4);
            }
            return dataPkg;
        }

        //package looks like
        //start package TRIBES_MBR_BEGIN.length
        //package length - 4 bytes
        //alive - 8 bytes
        //port - 4 bytes
        //secure port - 4 bytes
        //udp port - 4 bytes
        //host length - 1 byte
        //host - hl bytes
        //clen - 4 bytes
        //command - clen bytes
        //dlen - 4 bytes
        //domain - dlen bytes
        //uniqueId - 16 bytes
        //payload length - 4 bytes
        //payload plen bytes
        //end package TRIBES_MBR_END.length
        long alive=System.currentTimeMillis()-getServiceStartTime();
        byte[] data = new byte[getDataLength()];

        int bodylength = (getDataLength() - TRIBES_MBR_BEGIN.length - TRIBES_MBR_END.length - 4);

        int pos = 0;

        //TRIBES_MBR_BEGIN
        System.arraycopy(TRIBES_MBR_BEGIN,0,data,pos,TRIBES_MBR_BEGIN.length);
        pos += TRIBES_MBR_BEGIN.length;

        //body length
        XByteBuffer.toBytes(bodylength,data,pos);
        pos += 4;

        //alive data
        XByteBuffer.toBytes(alive,data,pos);
        pos += 8;
        //port
        XByteBuffer.toBytes(port,data,pos);
        pos += 4;
        //secure port
        XByteBuffer.toBytes(securePort,data,pos);
        pos += 4;
        //udp port
        XByteBuffer.toBytes(udpPort,data,pos);
        pos += 4;
        //host length
        data[pos++] = (byte) host.length;
        //host
        System.arraycopy(host,0,data,pos,host.length);
        pos+=host.length;
        //clen - 4 bytes
        XByteBuffer.toBytes(command.length,data,pos);
        pos+=4;
        //command - clen bytes
        System.arraycopy(command,0,data,pos,command.length);
        pos+=command.length;
        //dlen - 4 bytes
        XByteBuffer.toBytes(domain.length,data,pos);
        pos+=4;
        //domain - dlen bytes
        System.arraycopy(domain,0,data,pos,domain.length);
        pos+=domain.length;
        //unique Id
        System.arraycopy(uniqueId,0,data,pos,uniqueId.length);
        pos+=uniqueId.length;
        //payload
        XByteBuffer.toBytes(payload.length,data,pos);
        pos+=4;
        System.arraycopy(payload,0,data,pos,payload.length);
        pos+=payload.length;

        //TRIBES_MBR_END
        System.arraycopy(TRIBES_MBR_END,0,data,pos,TRIBES_MBR_END.length);
        pos += TRIBES_MBR_END.length;

        //create local data
        dataPkg = data;
        return data;
    }
    /**
     * Deserializes a member from data sent over the wire.
     *
     * @param data   The bytes received
     * @param member The member object to populate
     *
     * @return The populated member object.
     */
    public static Member getMember(byte[] data, MemberImpl member) {
        return getMember(data,0,data.length,member);
    }

    public static Member getMember(byte[] data, int offset, int length, MemberImpl member) {
        //package looks like
        //start package TRIBES_MBR_BEGIN.length
        //package length - 4 bytes
        //alive - 8 bytes
        //port - 4 bytes
        //secure port - 4 bytes
        //udp port - 4 bytes
        //host length - 1 byte
        //host - hl bytes
        //clen - 4 bytes
        //command - clen bytes
        //dlen - 4 bytes
        //domain - dlen bytes
        //uniqueId - 16 bytes
        //payload length - 4 bytes
        //payload plen bytes
        //end package TRIBES_MBR_END.length

        int pos = offset;

        if (XByteBuffer.firstIndexOf(data,offset,TRIBES_MBR_BEGIN)!=pos) {
            throw new IllegalArgumentException(sm.getString("memberImpl.invalid.package.begin", org.apache.catalina.tribes.util.Arrays.toString(TRIBES_MBR_BEGIN)));
        }

        if ( length < (TRIBES_MBR_BEGIN.length+4) ) {
            throw new ArrayIndexOutOfBoundsException(sm.getString("memberImpl.package.small"));
        }

        pos += TRIBES_MBR_BEGIN.length;

        int bodylength = XByteBuffer.toInt(data,pos);
        pos += 4;

        if ( length < (bodylength+4+TRIBES_MBR_BEGIN.length+TRIBES_MBR_END.length) ) {
            throw new ArrayIndexOutOfBoundsException(sm.getString("memberImpl.notEnough.bytes"));
        }

        int endpos = pos+bodylength;
        if (XByteBuffer.firstIndexOf(data,endpos,TRIBES_MBR_END)!=endpos) {
            throw new IllegalArgumentException(sm.getString("memberImpl.invalid.package.end", org.apache.catalina.tribes.util.Arrays.toString(TRIBES_MBR_END)));
        }


        byte[] alived = new byte[8];
        System.arraycopy(data, pos, alived, 0, 8);
        pos += 8;
        byte[] portd = new byte[4];
        System.arraycopy(data, pos, portd, 0, 4);
        pos += 4;

        byte[] sportd = new byte[4];
        System.arraycopy(data, pos, sportd, 0, 4);
        pos += 4;

        byte[] uportd = new byte[4];
        System.arraycopy(data, pos, uportd, 0, 4);
        pos += 4;


        byte hl = data[pos++];
        byte[] addr = new byte[hl];
        System.arraycopy(data, pos, addr, 0, hl);
        pos += hl;

        int cl = XByteBuffer.toInt(data, pos);
        pos += 4;

        byte[] command = new byte[cl];
        System.arraycopy(data, pos, command, 0, command.length);
        pos += command.length;

        int dl = XByteBuffer.toInt(data, pos);
        pos += 4;

        byte[] domain = new byte[dl];
        System.arraycopy(data, pos, domain, 0, domain.length);
        pos += domain.length;

        byte[] uniqueId = new byte[16];
        System.arraycopy(data, pos, uniqueId, 0, 16);
        pos += 16;

        int pl = XByteBuffer.toInt(data, pos);
        pos += 4;

        byte[] payload = new byte[pl];
        System.arraycopy(data, pos, payload, 0, payload.length);
        pos += payload.length;

        member.setHost(addr);
        member.setPort(XByteBuffer.toInt(portd, 0));
        member.setSecurePort(XByteBuffer.toInt(sportd, 0));
        member.setUdpPort(XByteBuffer.toInt(uportd, 0));
        member.setMemberAliveTime(XByteBuffer.toLong(alived, 0));
        member.setUniqueId(uniqueId);
        member.payload = payload;
        member.domain = domain;
        member.command = command;

        member.dataPkg = new byte[length];
        System.arraycopy(data, offset, member.dataPkg, 0, length);

        return member;
    }

    public static Member getMember(byte[] data) {
       return getMember(data,new MemberImpl());
    }

    public static Member getMember(byte[] data, int offset, int length) {
       return getMember(data,offset,length,new MemberImpl());
    }

    /**
     * Return the name of this object
     * @return a unique name to the cluster
     */
    @Override
    public String getName() {
        return "tcp://"+getHostname()+":"+getPort();
    }

    /**
     * Return the listen port of this member
     * @return - tcp listen port
     */
    @Override
    public int getPort()  {
        return this.port;
    }

    /**
     * Return the TCP listen host for this member
     * @return IP address or host name
     */
    @Override
    public byte[] getHost()  {
        return host;
    }

    public String getHostname() {
        if ( this.hostname != null ) return hostname;
        else {
            try {
                byte[] host = this.host;
                if (DO_DNS_LOOKUPS)
                    this.hostname = java.net.InetAddress.getByAddress(host).getHostName();
                else
                    this.hostname = org.apache.catalina.tribes.util.Arrays.toString(host,0,host.length,true);
                return this.hostname;
            }catch ( IOException x ) {
                throw new RuntimeException(sm.getString("memberImpl.unableParse.hostname"),x);
            }
        }
    }

    public int getMsgCount() {
        return this.msgCount;
    }

    /**
     * Contains information on how long this member has been online.
     * The result is the number of milli seconds this member has been
     * broadcasting its membership to the cluster.
     * @return nr of milliseconds since this member started.
     */
    @Override
    public long getMemberAliveTime() {
       return memberAliveTime;
    }

    public long getServiceStartTime() {
        return serviceStartTime;
    }

    @Override
    public byte[] getUniqueId() {
        return uniqueId;
    }

    @Override
    public byte[] getPayload() {
        return payload;
    }

    @Override
    public byte[] getCommand() {
        return command;
    }

    @Override
    public byte[] getDomain() {
        return domain;
    }

    @Override
    public int getSecurePort() {
        return securePort;
    }

    @Override
    public int getUdpPort() {
        return udpPort;
    }

    @Override
    public void setMemberAliveTime(long time) {
       memberAliveTime=time;
    }



    /**
     * String representation of this object
     */
    @Override
    public String toString()  {
        StringBuilder buf = new StringBuilder(getClass().getName());
        buf.append("[");
        buf.append(getName()).append(",");
        buf.append(getHostname()).append(",");
        buf.append(port).append(", alive=");
        buf.append(memberAliveTime).append(", ");
        buf.append("securePort=").append(securePort).append(", ");
        buf.append("UDP Port=").append(udpPort).append(", ");
        buf.append("id=").append(bToS(this.uniqueId)).append(", ");
        buf.append("payload=").append(bToS(this.payload,8)).append(", ");
        buf.append("command=").append(bToS(this.command,8)).append(", ");
        buf.append("domain=").append(bToS(this.domain,8)).append(", ");
        buf.append("]");
        return buf.toString();
    }
    public static String bToS(byte[] data) {
        return bToS(data,data.length);
    }
    public static String bToS(byte[] data, int max) {
        StringBuilder buf = new StringBuilder(4*16);
        buf.append("{");
        for (int i=0; data!=null && i<data.length; i++ ) {
            buf.append(String.valueOf(data[i])).append(" ");
            if ( i==max ) {
                buf.append("...("+data.length+")");
                break;
            }
        }
        buf.append("}");
        return buf.toString();
    }

    /**
     * @see java.lang.Object#hashCode()
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return getHost()[0]+getHost()[1]+getHost()[2]+getHost()[3];
    }

    /**
     * Returns true if the param o is a McastMember with the same name
     *
     * @param o The object to test for equality
     */
    @Override
    public boolean equals(Object o) {
        if ( o instanceof MemberImpl )    {
            return Arrays.equals(this.getHost(),((MemberImpl)o).getHost()) &&
                   this.getPort() == ((MemberImpl)o).getPort() &&
                   Arrays.equals(this.getUniqueId(),((MemberImpl)o).getUniqueId());
        }
        else
            return false;
    }

    public synchronized void setHost(byte[] host) {
        this.host = host;
    }

    public void setHostname(String host) throws IOException {
        hostname = host;
        synchronized (this) {
            this.host = java.net.InetAddress.getByName(host).getAddress();
        }
    }

    public void setMsgCount(int msgCount) {
        this.msgCount = msgCount;
    }

    public synchronized void setPort(int port) {
        this.port = port;
        this.dataPkg = null;
    }

    public void setServiceStartTime(long serviceStartTime) {
        this.serviceStartTime = serviceStartTime;
    }

    public synchronized void setUniqueId(byte[] uniqueId) {
        this.uniqueId = uniqueId!=null?uniqueId:new byte[16];
        getData(true,true);
    }

    @Override
    public synchronized void setPayload(byte[] payload) {
        // longs to avoid any possibility of overflow
        long oldPayloadLength = 0;
        if (this.payload != null) {
            oldPayloadLength = this.payload.length;
        }
        long newPayloadLength = 0;
        if (payload != null) {
            newPayloadLength = payload.length;
        }
        if (newPayloadLength > oldPayloadLength) {
            // It is possible that the max packet size will be exceeded
            if ((newPayloadLength - oldPayloadLength + getData(false, false).length) >
                    McastServiceImpl.MAX_PACKET_SIZE) {
                throw new IllegalArgumentException(sm.getString("memberImpl.large.payload"));
            }
        }
        this.payload = payload != null ? payload : new byte[0];
        getData(true, true);
    }

    @Override
    public synchronized void setCommand(byte[] command) {
        this.command = command!=null?command:new byte[0];
        getData(true,true);
    }

    public synchronized void setDomain(byte[] domain) {
        this.domain = domain!=null?domain:new byte[0];
        getData(true,true);
    }

    public synchronized void setSecurePort(int securePort) {
        this.securePort = securePort;
        this.dataPkg = null;
    }

    public synchronized void setUdpPort(int port) {
        this.udpPort = port;
        this.dataPkg = null;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int length = in.readInt();
        byte[] message = new byte[length];
        in.readFully(message);
        getMember(message,this);

    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        byte[] data = this.getData();
        out.writeInt(data.length);
        out.write(data);
    }

}
