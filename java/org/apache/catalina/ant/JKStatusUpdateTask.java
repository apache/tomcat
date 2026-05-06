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
package org.apache.catalina.ant;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.tools.ant.BuildException;

/**
 * Ant task that implements the <code>/status</code> command, supported by the mod_jk status (1.2.9) application.
 *
 * @since 5.5.9
 */
public class JKStatusUpdateTask extends AbstractCatalinaTask {

    private String worker = "lb";

    private String workerType = "lb";

    private int internalid = 0;

    private Integer lbRetries;

    private Integer lbRecovertime;

    private Boolean lbStickySession = Boolean.TRUE;

    private Boolean lbForceSession = Boolean.FALSE;

    private Integer workerLoadFactor;

    private String workerRedirect;

    private String workerClusterDomain;

    private Boolean workerDisabled = Boolean.FALSE;

    private Boolean workerStopped = Boolean.FALSE;

    private boolean isLBMode = true;

    private String workerLb;

    /**
     * Constructs a new JKStatusUpdateTask.
     */
    public JKStatusUpdateTask() {
        super();
        setUrl("http://localhost/status");
    }

    /**
     * Get the internal ID.
     *
     * @return the internal ID
     */
    public int getInternalid() {
        return internalid;
    }

    /**
     * Set the internal ID.
     *
     * @param internalid the internal ID to set
     */
    public void setInternalid(int internalid) {
        this.internalid = internalid;
    }

    /**
     * Get the lbForceSession flag.
     *
     * @return the lbForceSession flag
     */
    public Boolean getLbForceSession() {
        return lbForceSession;
    }

    /**
     * Set the lbForceSession flag.
     *
     * @param lbForceSession the value to set
     */
    public void setLbForceSession(Boolean lbForceSession) {
        this.lbForceSession = lbForceSession;
    }

    /**
     * Get the lbRecovertime value.
     *
     * @return the lbRecovertime value
     */
    public Integer getLbRecovertime() {
        return lbRecovertime;
    }

    /**
     * Set the lbRecovertime value.
     *
     * @param lbRecovertime the value to set
     */
    public void setLbRecovertime(Integer lbRecovertime) {
        this.lbRecovertime = lbRecovertime;
    }

    /**
     * Get the lbRetries value.
     *
     * @return the lbRetries value
     */
    public Integer getLbRetries() {
        return lbRetries;
    }

    /**
     * Set the lbRetries value.
     *
     * @param lbRetries the value to set
     */
    public void setLbRetries(Integer lbRetries) {
        this.lbRetries = lbRetries;
    }

    /**
     * Get the lbStickySession flag.
     *
     * @return the lbStickySession flag
     */
    public Boolean getLbStickySession() {
        return lbStickySession;
    }

    /**
     * Set the lbStickySession flag.
     *
     * @param lbStickySession the value to set
     */
    public void setLbStickySession(Boolean lbStickySession) {
        this.lbStickySession = lbStickySession;
    }

    /**
     * Get the worker name.
     *
     * @return the worker name
     */
    public String getWorker() {
        return worker;
    }

    /**
     * Set the worker name.
     *
     * @param worker the worker name to set
     */
    public void setWorker(String worker) {
        this.worker = worker;
    }

    /**
     * Get the worker type.
     *
     * @return the worker type
     */
    public String getWorkerType() {
        return workerType;
    }

    /**
     * Set the worker type.
     *
     * @param workerType the worker type to set
     */
    public void setWorkerType(String workerType) {
        this.workerType = workerType;
    }

    /**
     * Get the worker load balancing configuration.
     *
     * @return the worker load balancing configuration
     */
    public String getWorkerLb() {
        return workerLb;
    }

    /**
     * Set the worker load balancing configuration.
     *
     * @param workerLb the value to set
     */
    public void setWorkerLb(String workerLb) {
        this.workerLb = workerLb;
    }

    /**
     * Get the worker cluster domain.
     *
     * @return the worker cluster domain
     */
    public String getWorkerClusterDomain() {
        return workerClusterDomain;
    }

    /**
     * Set the worker cluster domain.
     *
     * @param workerClusterDomain the value to set
     */
    public void setWorkerClusterDomain(String workerClusterDomain) {
        this.workerClusterDomain = workerClusterDomain;
    }

    /**
     * Get the worker disabled flag.
     *
     * @return the worker disabled flag
     */
    public Boolean getWorkerDisabled() {
        return workerDisabled;
    }

    /**
     * Set the worker disabled flag.
     *
     * @param workerDisabled the value to set
     */
    public void setWorkerDisabled(Boolean workerDisabled) {
        this.workerDisabled = workerDisabled;
    }

    /**
     * Get the worker stopped flag.
     *
     * @return the worker stopped flag
     */
    public Boolean getWorkerStopped() {
        return workerStopped;
    }

    /**
     * Set the worker stopped flag.
     *
     * @param workerStopped the value to set
     */
    public void setWorkerStopped(Boolean workerStopped) {
        this.workerStopped = workerStopped;
    }

    /**
     * Get the worker load factor.
     *
     * @return the worker load factor
     */
    public Integer getWorkerLoadFactor() {
        return workerLoadFactor;
    }

    /**
     * Set the worker load factor.
     *
     * @param workerLoadFactor the value to set
     */
    public void setWorkerLoadFactor(Integer workerLoadFactor) {
        this.workerLoadFactor = workerLoadFactor;
    }

    /**
     * Get the worker redirect target.
     *
     * @return the worker redirect target
     */
    public String getWorkerRedirect() {
        return workerRedirect;
    }

    /**
     * Set the worker redirect target.
     *
     * @param workerRedirect the value to set
     */
    public void setWorkerRedirect(String workerRedirect) {
        this.workerRedirect = workerRedirect;
    }

    /**
     * Execute the requested operation.
     *
     * @exception BuildException if an error occurs
     */
    @Override
    public void execute() throws BuildException {

        super.execute();
        checkParameter();
        StringBuilder sb = createLink();
        execute(sb.toString(), null, null, -1);

    }

    /**
     * Create JkStatus link
     * <ul>
     * <li><b>load balance example: </b>http://localhost/status?cmd=update&mime=txt&w=lb&lf=false&ls=true</li>
     * <li><b>worker example: </b>http://localhost/status?cmd=update&mime=txt&w=node1&l=lb&wf=1&wd=false&ws=false</li>
     * </ul>
     *
     * @return create jkstatus link
     */
    private StringBuilder createLink() {
        // Building URL
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("?cmd=update&mime=txt");
            sb.append("&w=");
            sb.append(URLEncoder.encode(worker, getCharset()));

            if (isLBMode) {
                // http://localhost/status?cmd=update&mime=txt&w=lb&lf=false&ls=true
                if ((lbRetries != null)) { // > 0
                    sb.append("&lr=");
                    sb.append(lbRetries);
                }
                if ((lbRecovertime != null)) { // > 59
                    sb.append("&lt=");
                    sb.append(lbRecovertime);
                }
                if ((lbStickySession != null)) {
                    sb.append("&ls=");
                    sb.append(lbStickySession);
                }
                if ((lbForceSession != null)) {
                    sb.append("&lf=");
                    sb.append(lbForceSession);
                }
            } else {
                // http://localhost/status?cmd=update&mime=txt&w=node1&l=lb&wf=1&wd=false&ws=false
                if ((workerLb != null)) { // must be configured
                    sb.append("&l=");
                    sb.append(URLEncoder.encode(workerLb, getCharset()));
                }
                if ((workerLoadFactor != null)) { // >= 1
                    sb.append("&wf=");
                    sb.append(workerLoadFactor);
                }
                if ((workerDisabled != null)) {
                    sb.append("&wd=");
                    sb.append(workerDisabled);
                }
                if ((workerStopped != null)) {
                    sb.append("&ws=");
                    sb.append(workerStopped);
                }
                if ((workerRedirect != null)) { // other worker conrecte lb's
                    sb.append("&wr=");
                }
                if ((workerClusterDomain != null)) {
                    sb.append("&wc=");
                    sb.append(URLEncoder.encode(workerClusterDomain, getCharset()));
                }
            }

        } catch (UnsupportedEncodingException e) {
            throw new BuildException("Invalid 'charset' attribute: " + getCharset());
        }
        return sb;
    }

    /**
     * check correct lb and worker parameter
     */
    protected void checkParameter() {
        if (worker == null) {
            throw new BuildException("Must specify 'worker' attribute");
        }
        if (workerType == null) {
            throw new BuildException("Must specify 'workerType' attribute");
        }
        if ("lb".equals(workerType)) {
            if (lbRecovertime == null && lbRetries == null) {
                throw new BuildException(
                        "Must specify at a lb worker either 'lbRecovertime' or" + "'lbRetries' attribute");
            }
            if (lbStickySession == null || lbForceSession == null) {
                throw new BuildException(
                        "Must specify at a lb worker either" + "'lbStickySession' and 'lbForceSession' attribute");
            }
            if (null != lbRecovertime && 60 < lbRecovertime.intValue()) {
                throw new BuildException("The 'lbRecovertime' must be greater than 59");
            }
            if (null != lbRetries && 1 < lbRetries.intValue()) {
                throw new BuildException("The 'lbRetries' must be greater than 1");
            }
            isLBMode = true;
        } else if ("worker".equals(workerType)) {
            if (workerDisabled == null) {
                throw new BuildException("Must specify at a node worker 'workerDisabled' attribute");
            }
            if (workerStopped == null) {
                throw new BuildException("Must specify at a node worker 'workerStopped' attribute");
            }
            if (workerLoadFactor == null) {
                throw new BuildException("Must specify at a node worker 'workerLoadFactor' attribute");
            }
            if (workerClusterDomain == null) {
                throw new BuildException("Must specify at a node worker 'workerClusterDomain' attribute");
            }
            if (workerRedirect == null) {
                throw new BuildException("Must specify at a node worker 'workerRedirect' attribute");
            }
            if (workerLb == null) {
                throw new BuildException("Must specify 'workerLb' attribute");
            }
            if (workerLoadFactor.intValue() < 1) {
                throw new BuildException("The 'workerLoadFactor' must be greater or equal 1");
            }
            isLBMode = false;
        } else {
            throw new BuildException("Only 'lb' and 'worker' supported as workerType attribute");
        }
    }
}