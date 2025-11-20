package org.apache.coyote.http11.filters;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.coyote.http11.OutputFilter;

/**
 * Factory interface for creating output filters.
 * Allows pluggable compression and transformation filters.
 */
public interface OutputFilterFactory {

    /**
     * Create a new output filter instance configured for the given protocol.
     *
     * @param protocol The HTTP protocol instance providing configuration
     * @return A configured output filter ready for use
     */
    OutputFilter createFilter(AbstractHttp11Protocol<?> protocol);

    /**
     * Get the encoding name for this filter.
     * Used for Content-Encoding or Transfer-Encoding headers and
     * for matching against client Accept-Encoding preferences.
     *
     * @return The encoding name (e.g., "gzip", "br", "deflate", "zstd")
     */
    String getEncodingName();
}
