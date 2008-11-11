package org.apache.tomcat.bayeux;

public class HttpError {
    private int code;
    private String status;
    private Throwable cause;
    public HttpError(int code, String status, Throwable cause) {
        this.code = code;
        this.status = status;
        this.cause = cause;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCause(Throwable exception) {
        this.cause = exception;
    }

    public int getCode() {
        return code;
    }

    public String getStatus() {
        return status;
    }

    public Throwable getCause() {
        return cause;
    }

    public String toString() {
        if (cause != null)
            return code + ":" + status + " - [" + cause + "]";
        else
            return code + ":" + status;
    }
}
