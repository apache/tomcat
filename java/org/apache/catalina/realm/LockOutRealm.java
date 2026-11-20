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
package org.apache.catalina.realm;

import java.io.Serial;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.catalina.LifecycleException;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.HexUtils;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;

/**
 * This class extends the CombinedRealm (hence it can wrap other Realms) to provide a user lock out mechanism if there
 * are too many failed authentication attempts in a given period of time. To ensure correct operation, there is a
 * reasonable degree of synchronisation in this Realm. This Realm does not require modification to the underlying Realms
 * or the associated user storage mechanisms. It achieves this by recording all failed logins, including those for users
 * that do not exist. To prevent a DOS by deliberating making requests with invalid users (and hence causing this cache
 * to grow) the size of the list of users that have failed authentication is limited.
 */
public class LockOutRealm extends CombinedRealm {

    /**
     * Default constructor.
     */
    public LockOutRealm() {
    }

    private static final Log log = LogFactory.getLog(LockOutRealm.class);

    private static final String DEFAULT_CHARACTERS_TO_IGNORE;

    private static final Set<Integer> DEFAULT_CODE_POINTS_TO_IGNORE;

    static {
        StringBuilder sb = new StringBuilder();
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (isIgnoredByDefault(cp)) {
                sb.appendCodePoint(cp);
            }
        }
        DEFAULT_CHARACTERS_TO_IGNORE = sb.toString();
        DEFAULT_CODE_POINTS_TO_IGNORE = toCodePointSet(DEFAULT_CHARACTERS_TO_IGNORE);
    }

    /**
     * The number of times in a row a user has to fail authentication to be locked out. Defaults to 5.
     */
    protected int failureCount = 5;

    /**
     * The time (in seconds) a user is locked out for after too many authentication failures. Defaults to 300 (5
     * minutes).
     */
    protected int lockOutTime = 300;

    /**
     * Number of users that have failed authentication to keep in cache. Over time the cache will grow to this size and
     * may not shrink. Defaults to 1000.
     */
    protected int cacheSize = 1000;

    /**
     * If a failed user is removed from the cache because the cache is too big before it has been in the cache for at
     * least this period of time (in seconds) a warning message will be logged. Defaults to 3600 (1 hour).
     */
    protected int cacheRemovalWarningTime = 3600;

    /**
     * Users whose last authentication attempt failed. Entries will be ordered in access order from least recent to most
     * recent.
     */
    protected Map<String,LockRecord> failedUsers = null;

    /*
     * Should user names (the keys) in the failedUsers Map be treated in a case sensitive manner. The default is false.
     */
    private boolean caseSensitive = false;

    /*
     * Should user names (the keys) in the failedUsers Map have leading space removed. The default is true.
     */
    private boolean stripLeadingSpace = true;

    /*
     * Should user names (the keys) in the failedUsers Map have trailing space removed. The default is true.
     */
    private boolean stripTrailingSpace = true;

    /*
     * Characters that, if present in a user name, will be treated as if they are not present in the user name for the
     * purposes of this realm. The default is the set of characters that LDAP servers and most databases ignore when
     * comparing user names (see isIgnoredByDefault()). This is the actual characters, not the escaped form.
     */
    private volatile String charactersToIgnore = DEFAULT_CHARACTERS_TO_IGNORE;

    /*
     * Further characters that are treated as if they are not present in the user name. These are in addition to
     * charactersToIgnore. The default is none. This is the actual characters, not the escaped form.
     */
    private volatile String additionalCharactersToIgnore = "";

    /*
     * The code points of the characters in charactersToIgnore and additionalCharactersToIgnore. Derived from them when
     * either is set so it does not need to be derived each time a user name is normalized.
     */
    private volatile Set<Integer> codePointsToIgnore = DEFAULT_CODE_POINTS_TO_IGNORE;

    /*
     * Should user names (the keys) in the failedUsers Map be converted to Unicode Normalization Form KC (NFKC). This
     * is the form used by LDAP servers (RFC 4518, RFC 4013) when comparing user names. The default is true.
     */
    private volatile boolean normalizeUnicode = true;

    /*
     * Should combining marks (Unicode category Mn) be removed from user names (the keys) in the failedUsers Map. This
     * matches the behaviour of accent insensitive database collations. The default is true.
     */
    private volatile boolean stripCombiningMarks = true;

    /*
     * Should runs of space characters within user names (the keys) in the failedUsers Map be replaced with a single
     * space. The default is true.
     */
    private volatile boolean collapseInternalSpace = true;


    @Override
    protected void startInternal() throws LifecycleException {
        /*
         * Configure the list of failed users to delete the oldest entry once it exceeds the specified size. This is an
         * LRU cache so if the cache size is exceeded the users who most recently failed authentication will be
         * retained.
         */
        failedUsers = new LinkedHashMap<>(cacheSize, 0.75f, true) {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String,LockRecord> eldest) {
                if (size() > cacheSize) {
                    // Check to see if this element has been removed too quickly
                    long timeInCache = (System.currentTimeMillis() - eldest.getValue().getLastFailureTime()) / 1000;

                    if (timeInCache < cacheRemovalWarningTime) {
                        log.warn(
                                sm.getString("lockOutRealm.removeWarning", eldest.getKey(), Long.valueOf(timeInCache)));
                    }
                    return true;
                }
                return false;
            }
        };

        super.startInternal();
    }


    @Override
    public Principal authenticate(String username, String clientDigest, String nonce, String nc, String cnonce,
            String qop, String realmName, String digestA2, String algorithm) {

        Principal authenticatedUser =
                super.authenticate(username, clientDigest, nonce, nc, cnonce, qop, realmName, digestA2, algorithm);
        return filterLockedAccounts(username, authenticatedUser);
    }


    @Override
    public Principal authenticate(String username, String credentials) {
        Principal authenticatedUser = super.authenticate(username, credentials);
        return filterLockedAccounts(username, authenticatedUser);
    }


    @Override
    public Principal authenticate(X509Certificate[] certs) {
        String username = null;
        if (certs != null && certs.length > 0) {
            username = certs[0].getSubjectX500Principal().toString();
        }

        Principal authenticatedUser = super.authenticate(certs);
        return filterLockedAccounts(username, authenticatedUser);
    }


    @Override
    public Principal authenticate(GSSContext gssContext, boolean storeCreds) {
        if (gssContext.isEstablished()) {
            String username;
            GSSName name;
            try {
                name = gssContext.getSrcName();
            } catch (GSSException e) {
                log.warn(sm.getString("realmBase.gssNameFail"), e);
                return null;
            }

            username = name.toString();

            Principal authenticatedUser = super.authenticate(gssContext, storeCreds);

            return filterLockedAccounts(username, authenticatedUser);
        }

        // Fail in all other cases
        return null;
    }


    @Override
    public Principal authenticate(GSSName gssName, GSSCredential gssCredential) {
        String username = gssName.toString();

        Principal authenticatedUser = super.authenticate(gssName, gssCredential);

        return filterLockedAccounts(username, authenticatedUser);
    }


    /*
     * Filters authenticated principals to ensure that <code>null</code> is returned for any user that is currently
     * locked out.
     */
    private Principal filterLockedAccounts(String username, Principal authenticatedUser) {
        // Register all failed authentications
        if (authenticatedUser == null && isAvailable()) {
            registerAuthFailure(username);
        }

        if (isLocked(username)) {
            // If the user is currently locked, authentication will always fail
            log.warn(sm.getString("lockOutRealm.authLockedUser", username));
            return null;
        }

        if (authenticatedUser != null) {
            registerAuthSuccess(username);
        }

        return authenticatedUser;
    }


    /**
     * Unlock the specified username. This will remove all records of authentication failures for this user.
     *
     * @param username The user to unlock
     */
    public void unlock(String username) {
        // Auth success clears the lock record so...
        registerAuthSuccess(username);
    }


    /**
     * Check if the specified user is locked, that is, the user has at least the configured number of recent
     * authentication failures and the lock out period has not yet expired.
     *
     * @param username The username to check
     * @return true if the user is locked, false otherwise
     */
    public boolean isLocked(String username) {
        username = normalizeUsername(username);
        LockRecord lockRecord;
        synchronized (this) {
            lockRecord = failedUsers.get(username);
        }

        // No lock record means user can't be locked
        if (lockRecord == null) {
            return false;
        }

        // Check to see if user is locked
        // Otherwise, user has not, yet, exceeded lock thresholds
        return lockRecord.getFailures() >= failureCount &&
                (System.currentTimeMillis() - lockRecord.getLastFailureTime()) / 1000 < lockOutTime;

    }


    /*
     * After successful authentication, any record of previous authentication failure is removed.
     */
    private synchronized void registerAuthSuccess(String username) {
        // Successful authentication means removal from the list of failed users
        failedUsers.remove(normalizeUsername(username));
    }


    /*
     * After a failed authentication, add the record of the failed authentication.
     */
    private void registerAuthFailure(String username) {
        username = normalizeUsername(username);
        LockRecord lockRecord;
        synchronized (this) {
            if (!failedUsers.containsKey(username)) {
                lockRecord = new LockRecord();
                lockRecord.registerFailure();
                failedUsers.put(username, lockRecord);
            } else {
                lockRecord = failedUsers.get(username);
                if (lockRecord.getFailures() >= failureCount &&
                        ((System.currentTimeMillis() - lockRecord.getLastFailureTime()) / 1000) > lockOutTime) {
                    // User was previously locked out but lockout has now
                    // expired so reset failure count
                    lockRecord.setFailures(0);
                }
                lockRecord.registerFailure();
            }
        }
    }


    /**
     * Get the number of failed authentication attempts required to lock the user account.
     *
     * @return the failureCount
     */
    public int getFailureCount() {
        return failureCount;
    }


    /**
     * Set the number of failed authentication attempts required to lock the user account.
     *
     * @param failureCount the failureCount to set
     */
    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }


    /**
     * Get the period for which an account will be locked.
     *
     * @return the lockOutTime
     */
    public int getLockOutTime() {
        return lockOutTime;
    }


    /**
     * Set the period for which an account will be locked.
     *
     * @param lockOutTime the lockOutTime to set
     */
    public void setLockOutTime(int lockOutTime) {
        this.lockOutTime = lockOutTime;
    }


    /**
     * Get the maximum number of users for which authentication failure will be kept in the cache.
     *
     * @return the cacheSize
     */
    public int getCacheSize() {
        return cacheSize;
    }


    /**
     * Set the maximum number of users for which authentication failure will be kept in the cache.
     *
     * @param cacheSize the cacheSize to set
     */
    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }


    /**
     * Get the minimum period a failed authentication must remain in the cache to avoid generating a warning if it is
     * removed from the cache to make space for a new entry.
     *
     * @return the cacheRemovalWarningTime
     */
    public int getCacheRemovalWarningTime() {
        return cacheRemovalWarningTime;
    }


    /**
     * Set the minimum period a failed authentication must remain in the cache to avoid generating a warning if it is
     * removed from the cache to make space for a new entry.
     *
     * @param cacheRemovalWarningTime the cacheRemovalWarningTime to set
     */
    public void setCacheRemovalWarningTime(int cacheRemovalWarningTime) {
        this.cacheRemovalWarningTime = cacheRemovalWarningTime;
    }


    /**
     * Get the case sensitivity flag for user name matching.
     *
     * @return the current caseSensitive value
     */
    public boolean getCaseSensitive() {
        return caseSensitive;
    }


    /**
     * Set the case sensitivity flag for user name matching.
     *
     * @param caseSensitive the caseSensitive to set
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }


    /**
     * Get the case strip leading space flag for user name matching.
     *
     * @return the current stripLeadingSpace value
     */
    public boolean getStripLeadingSpace() {
        return stripLeadingSpace;
    }


    /**
     * Set the strip leading space flag for user name matching.
     *
     * @param stripLeadingSpace the stripLeadingSpace to set
     */
    public void setStripLeadingSpace(boolean stripLeadingSpace) {
        this.stripLeadingSpace = stripLeadingSpace;
    }


    /**
     * Get the case strip trailing space flag for user name matching.
     *
     * @return the current stripTrailingSpace value
     */
    public boolean getStripTrailingSpace() {
        return stripTrailingSpace;
    }


    /**
     * Set the strip trailing space flag for user name matching.
     *
     * @param stripTrailingSpace the stripTrailingSpace to set
     */
    public void setStripTrailingSpace(boolean stripTrailingSpace) {
        this.stripTrailingSpace = stripTrailingSpace;
    }


    /**
     * Get the characters that are ignored in user names when making locking decisions. This does not include the
     * additional characters to ignore.
     * <p>
     * The value is returned in escaped form so it can be passed to {@link #setCharactersToIgnore(String)}. Characters
     * other than the printable ASCII characters (U+0021 to U+007E) are returned as {@code \}{@code uXXXX} (four hex
     * digits) or, for characters outside the Basic Multilingual Plane, {@code \}{@code u{X...X}}. A backslash is
     * returned as two backslashes.
     *
     * @return the characters that are ignored in escaped form
     */
    public String getCharactersToIgnore() {
        return escape(charactersToIgnore);
    }


    /**
     * Set the characters that are ignored in user names when making locking decisions. Each Unicode character
     * (code point) in the provided value is removed from user names. The provided value replaces the default value in
     * its entirety. A {@code null} value is treated as an empty String, meaning that no characters are ignored. The
     * characters set with {@link #setAdditionalCharactersToIgnore(String)} are ignored as well.
     * <p>
     * Characters that are difficult to specify directly may be escaped. The supported escape sequences are:
     * <ul>
     * <li>{@code \}{@code \} - a backslash</li>
     * <li>{@code \}{@code uXXXX} - the UTF-16 code unit with the four digit hex value XXXX (a pair of these escape
     * sequences may be used to specify a character outside the Basic Multilingual Plane)</li>
     * <li>{@code \}{@code u{X...X}} - the character with the one to six digit hex value X...X</li>
     * </ul>
     * Any other use of a backslash is an error as is an unpaired surrogate.
     * <p>
     * The default value is every Unicode format character (category Cf, includes soft hyphen and the zero width
     * characters), every control character (category Cc) that is not a space character, and the following characters
     * that LDAP servers and many databases also ignore when comparing values: U+034F, U+1806, U+180B to U+180D,
     * U+FE00 to U+FE0F and U+FFFC.
     *
     * @param charactersToIgnore the characters to ignore, optionally containing escape sequences
     *
     * @throws IllegalArgumentException if the provided value contains an invalid escape sequence or an unpaired
     *                                      surrogate
     */
    public void setCharactersToIgnore(String charactersToIgnore) {
        String characters = unescape(charactersToIgnore);
        synchronized (this) {
            this.charactersToIgnore = characters;
            updateCodePointsToIgnore();
        }
    }


    /**
     * Get the additional characters that are ignored in user names when making locking decisions.
     * <p>
     * The value is returned in escaped form so it can be passed to {@link #setAdditionalCharactersToIgnore(String)}.
     * See {@link #getCharactersToIgnore()} for the form used.
     *
     * @return the additional characters that are ignored in escaped form
     */
    public String getAdditionalCharactersToIgnore() {
        return escape(additionalCharactersToIgnore);
    }


    /**
     * Set the additional characters that are ignored in user names when making locking decisions. These characters are
     * ignored in addition to those set with {@link #setCharactersToIgnore(String)} (or the default value of that
     * attribute if it has not been set). A {@code null} value is treated as an empty String, meaning that there are no
     * additional characters. The same escape sequences are supported as for {@link #setCharactersToIgnore(String)}.
     *
     * @param additionalCharactersToIgnore the additional characters to ignore, optionally containing escape sequences
     *
     * @throws IllegalArgumentException if the provided value contains an invalid escape sequence or an unpaired
     *                                      surrogate
     */
    public void setAdditionalCharactersToIgnore(String additionalCharactersToIgnore) {
        String characters = unescape(additionalCharactersToIgnore);
        synchronized (this) {
            this.additionalCharactersToIgnore = characters;
            updateCodePointsToIgnore();
        }
    }


    private void updateCodePointsToIgnore() {
        codePointsToIgnore = toCodePointSet(charactersToIgnore.concat(additionalCharactersToIgnore));
    }


    /**
     * Get the Unicode normalization flag for user name matching.
     *
     * @return {@code true} if user names are converted to Unicode Normalization Form KC (NFKC)
     */
    public boolean getNormalizeUnicode() {
        return normalizeUnicode;
    }


    /**
     * Set the Unicode normalization flag for user name matching. If {@code true}, user names are converted to Unicode
     * Normalization Form KC (NFKC) when making locking decisions. This is the form used by LDAP servers.
     *
     * @param normalizeUnicode the normalizeUnicode to set
     */
    public void setNormalizeUnicode(boolean normalizeUnicode) {
        this.normalizeUnicode = normalizeUnicode;
    }


    /**
     * Get the strip combining marks flag for user name matching.
     *
     * @return {@code true} if combining marks are removed from user names
     */
    public boolean getStripCombiningMarks() {
        return stripCombiningMarks;
    }


    /**
     * Set the strip combining marks flag for user name matching. If {@code true}, nonspacing combining marks (Unicode
     * category Mn, such as accents) are removed from user names when making locking decisions. Accent insensitive
     * database collations treat user names that differ only by such marks as the same user name.
     *
     * @param stripCombiningMarks the stripCombiningMarks to set
     */
    public void setStripCombiningMarks(boolean stripCombiningMarks) {
        this.stripCombiningMarks = stripCombiningMarks;
    }


    /**
     * Get the collapse internal space flag for user name matching.
     *
     * @return {@code true} if runs of space within user names are replaced with a single space
     */
    public boolean getCollapseInternalSpace() {
        return collapseInternalSpace;
    }


    /**
     * Set the collapse internal space flag for user name matching. If {@code true}, each run of one or more space
     * characters (see {@link #setStripLeadingSpace(boolean)}) between other characters in a user name is replaced by a
     * single space when making locking decisions. Leading and trailing space is controlled by the strip leading space
     * and strip trailing space flags.
     *
     * @param collapseInternalSpace the collapseInternalSpace to set
     */
    public void setCollapseInternalSpace(boolean collapseInternalSpace) {
        this.collapseInternalSpace = collapseInternalSpace;
    }


    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }

        // Remove ignored characters first so they can't prevent characters being combined or collapsed below
        Set<Integer> remove = codePointsToIgnore;
        if (!remove.isEmpty()) {
            username = username.codePoints().filter(cp -> !remove.contains(Integer.valueOf(cp)))
                    .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        }

        // Normalize before changing case since normalization can change the case of a character
        if (getNormalizeUnicode()) {
            username = Normalizer.normalize(username, Normalizer.Form.NFKC);
        }

        if (!getCaseSensitive()) {
            username = username.toLowerCase(Locale.ROOT);
        }

        if (getStripCombiningMarks()) {
            username = removeCombiningMarks(username);
        }

        if (getCollapseInternalSpace()) {
            username = collapseInternalSpace(username);
        }

        int start = 0;
        int end = username.length();
        if (getStripLeadingSpace()) {
            while (start < end) {
                int cp = username.codePointAt(start);
                if (!isSpace(cp)) {
                    break;
                }
                start += Character.charCount(cp);
            }
        }
        if (getStripTrailingSpace()) {
            while (end > start) {
                int cp = username.codePointBefore(end);
                if (!isSpace(cp)) {
                    break;
                }
                end -= Character.charCount(cp);
            }
        }
        return username.substring(start, end);
    }


    private String removeCombiningMarks(String username) {
        String decomposed = Normalizer.normalize(username, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        decomposed.codePoints().filter(cp -> Character.getType(cp) != Character.NON_SPACING_MARK)
                .forEach(sb::appendCodePoint);
        // Recompose what is left
        return Normalizer.normalize(sb, getNormalizeUnicode() ? Normalizer.Form.NFKC : Normalizer.Form.NFC);
    }


    private String collapseInternalSpace(String username) {
        int length = username.length();

        // Leading and trailing space is not internal space. It is left for the strip options to deal with.
        int start = 0;
        while (start < length) {
            int cp = username.codePointAt(start);
            if (!isSpace(cp)) {
                break;
            }
            start += Character.charCount(cp);
        }
        if (start == length) {
            return username;
        }
        int end = length;
        while (end > start) {
            int cp = username.codePointBefore(end);
            if (!isSpace(cp)) {
                break;
            }
            end -= Character.charCount(cp);
        }

        StringBuilder sb = new StringBuilder(length);
        sb.append(username, 0, start);
        boolean inSpace = false;
        int i = start;
        while (i < end) {
            int cp = username.codePointAt(i);
            if (isSpace(cp)) {
                if (!inSpace) {
                    sb.append(' ');
                    inSpace = true;
                }
            } else {
                sb.appendCodePoint(cp);
                inSpace = false;
            }
            i += Character.charCount(cp);
        }
        sb.append(username, end, length);
        return sb.toString();
    }


    /*
     * Character.isWhitespace() only covers some space characters. Character.isSpaceChar() is also needed. U+0085 (NEL)
     * is neither but LDAP servers treat it as a space.
     */
    private static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x0085;
    }


    /*
     * The characters that are ignored by default. These are characters that LDAP servers ignore when comparing values
     * (see the "map to nothing" characters in RFC 3454, table B.1, and the control and format characters in RFC 4518)
     * and that many databases ignore. They are all effectively invisible.
     */
    private static boolean isIgnoredByDefault(int cp) {
        switch (Character.getType(cp)) {
            case Character.FORMAT:
                return true;
            case Character.CONTROL:
                // Control characters that are space (tab, new line etc.) are dealt with as space
                return !isSpace(cp);
            default:
                break;
        }
        return cp == 0x034F || cp == 0x1806 || cp >= 0x180B && cp <= 0x180D || cp >= 0xFE00 && cp <= 0xFE0F ||
                cp == 0xFFFC;
    }


    /*
     * Convert the escaped form of a set of characters to the characters.
     */
    private String unescape(String escaped) {
        if (escaped == null) {
            return "";
        }

        int length = escaped.length();
        StringBuilder sb = new StringBuilder(length);
        int i = 0;
        while (i < length) {
            char c = escaped.charAt(i++);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i == length) {
                throw invalidEscape(escaped);
            }
            char type = escaped.charAt(i++);
            if (type == '\\') {
                sb.append('\\');
            } else if (type == 'u') {
                if (i < length && escaped.charAt(i) == '{') {
                    int close = escaped.indexOf('}', i);
                    if (close < 0) {
                        throw invalidEscape(escaped);
                    }
                    int cp = parseHex(escaped, i + 1, close, 6);
                    if (cp > Character.MAX_CODE_POINT) {
                        throw invalidEscape(escaped);
                    }
                    sb.appendCodePoint(cp);
                    i = close + 1;
                } else {
                    if (i + 4 > length) {
                        throw invalidEscape(escaped);
                    }
                    sb.append((char) parseHex(escaped, i, i + 4, 4));
                    i += 4;
                }
            } else {
                throw invalidEscape(escaped);
            }
        }

        // Surrogates are only valid if paired. Pairs are always read as a single code point.
        String result = sb.toString();
        int j = 0;
        while (j < result.length()) {
            int cp = result.codePointAt(j);
            if (Character.isSurrogate((char) cp) && cp <= 0xFFFF) {
                throw invalidEscape(escaped);
            }
            j += Character.charCount(cp);
        }
        return result;
    }


    /*
     * Parse the hex digits in value from start (inclusive) to end (exclusive). At least one digit is required and no
     * more than maxDigits are permitted. Only the ASCII hex digits are permitted.
     */
    private int parseHex(String value, int start, int end, int maxDigits) {
        if (end <= start || end - start > maxDigits) {
            throw invalidEscape(value);
        }
        int result = 0;
        for (int i = start; i < end; i++) {
            int digit = HexUtils.getDec(value.charAt(i));
            if (digit < 0) {
                throw invalidEscape(value);
            }
            result = result * 16 + digit;
        }
        return result;
    }


    private IllegalArgumentException invalidEscape(String value) {
        return new IllegalArgumentException(sm.getString("lockOutRealm.invalidEscape", value));
    }


    /*
     * Convert a set of characters to the escaped form. Only the printable ASCII characters other than the backslash
     * are not escaped. This avoids any ambiguity when displaying or editing the value.
     */
    private static String escape(String characters) {
        StringBuilder sb = new StringBuilder(characters.length());
        characters.codePoints().forEach(cp -> {
            if (cp == '\\') {
                sb.append("\\\\");
            } else if (cp >= 0x21 && cp <= 0x7E) {
                sb.appendCodePoint(cp);
            } else if (cp <= 0xFFFF) {
                String hex = Integer.toHexString(cp).toUpperCase(Locale.ROOT);
                sb.append("\\u").append("0000", hex.length(), 4).append(hex);
            } else {
                sb.append("\\u{").append(Integer.toHexString(cp).toUpperCase(Locale.ROOT)).append('}');
            }
        });
        return sb.toString();
    }


    private static Set<Integer> toCodePointSet(String characters) {
        return characters.codePoints().boxed().collect(Collectors.toUnmodifiableSet());
    }


    /**
     * Internal record to track lock state for a user.
     */
    protected static class LockRecord {
        /**
         * The number of authentication failures.
         */
        private final AtomicInteger failures = new AtomicInteger(0);
        /**
         * The time of the last failure.
         */
        private volatile long lastFailureTime = 0;

        /**
         * Default constructor.
         */
        public LockRecord() {
        }

        /**
         * Get the number of authentication failures.
         *
         * @return the failures
         */
        public int getFailures() {
            return failures.get();
        }

        /**
         * Set the number of authentication failures.
         *
         * @param theFailures the failures to set
         */
        public void setFailures(int theFailures) {
            failures.set(theFailures);
        }

        /**
         * Get the time of the last failure.
         *
         * @return the lastFailureTime
         */
        public long getLastFailureTime() {
            return lastFailureTime;
        }

        /**
         * Register a new authentication failure.
         */
        public void registerFailure() {
            failures.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
        }
    }
}
