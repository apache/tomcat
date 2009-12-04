/*
 * Copyright  2000-2009 The Apache Software Foundation
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
 *
 */
package org.apache.tomcat.util.bcel.verifier;

/**
 * A VerificationResult is what a PassVerifier returns
 * after verifying.
 *
 * @version $Id$
 * @author Enver Haase
 *
 */
public class VerificationResult {

    /**
     * Constant to indicate verification has not been tried yet.
     * This happens if some earlier verification pass did not return VERIFIED_OK.
     */
    public static final int VERIFIED_NOTYET = 0;
    /** Constant to indicate verification was passed. */
    public static final int VERIFIED_OK = 1;
    /** Constant to indicate verfication failed. */
    public static final int VERIFIED_REJECTED = 2;
    /**
     * This string is the canonical message for verifications that have not been tried yet.
     * This happens if some earlier verification pass did not return VERIFIED_OK.
     */
    private static final String VERIFIED_NOTYET_MSG = "Not yet verified.";
    /** This string is the canonical message for passed verification passes. */
    private static final String VERIFIED_OK_MSG = "Passed verification.";
    /**
     * Canonical VerificationResult for not-yet-tried verifications.
     * This happens if some earlier verification pass did not return VERIFIED_OK.
     */
    public static final VerificationResult VR_NOTYET = new VerificationResult(VERIFIED_NOTYET,
            VERIFIED_NOTYET_MSG);
    /** Canonical VerificationResult for passed verifications. */
    public static final VerificationResult VR_OK = new VerificationResult(VERIFIED_OK,
            VERIFIED_OK_MSG);
    /** The numeric status. */
    private int numeric;
    /** The detailed message. */
    private String detailMessage;


    /** The usual constructor. */
    public VerificationResult(int status, String message) {
        numeric = status;
        detailMessage = message;
    }


    /** Returns one one the VERIFIED_OK, VERIFIED_NOTYET, VERIFIED_REJECTED constants. */
    public int getStatus() {
        return numeric;
    }


    /** Returns a detailed message. */
    public String getMessage() {
        return detailMessage;
    }


    /** @return a hash code value for the object.
     */
    public int hashCode() {
        return numeric ^ detailMessage.hashCode();
    }


    /**
     * Returns if two VerificationResult instances are equal.
     */
    public boolean equals( Object o ) {
        if (!(o instanceof VerificationResult)) {
            return false;
        }
        VerificationResult other = (VerificationResult) o;
        return ((other.numeric == this.numeric) && (other.detailMessage.equals(this.detailMessage)));
    }


    /**
     * Returns a String representation of the VerificationResult.
     */
    public String toString() {
        String ret = "";
        if (numeric == VERIFIED_NOTYET) {
            ret = "VERIFIED_NOTYET";
        }
        if (numeric == VERIFIED_OK) {
            ret = "VERIFIED_OK";
        }
        if (numeric == VERIFIED_REJECTED) {
            ret = "VERIFIED_REJECTED";
        }
        ret += "\n" + detailMessage + "\n";
        return ret;
    }
}
