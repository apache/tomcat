package org.apache.catalina.ha;

import org.apache.catalina.tribes.Member;


/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2005</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class ClusterMessageBase implements ClusterMessage {
    
    protected transient Member address;
    private String uniqueId;
    private long timestamp;
    public ClusterMessageBase() {
    }

    /**
     * getAddress
     *
     * @return Member
     * @todo Implement this org.apache.catalina.ha.ClusterMessage method
     */
    public Member getAddress() {
        return address;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * setAddress
     *
     * @param member Member
     * @todo Implement this org.apache.catalina.ha.ClusterMessage method
     */
    public void setAddress(Member member) {
        this.address = member;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}