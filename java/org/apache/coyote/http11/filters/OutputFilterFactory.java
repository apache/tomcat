package org.apache.coyote.http11.filters;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.apache.coyote.http11.OutputFilter;

/**
 * Factory interface for creating output filters.
 * Allows pluggable compression and transformation filters.
 * <p>
 * Implementations hold their own configuration as JavaBean properties.
 * Which can be set via nested elements in server.xml.
 */
public interface OutputFilterFactory {

    /**
     * Create a new output filter instance.
     * <p>
     * The factory is expected to configure the filter using its own
     * JavaBean properties rather than relying on external configuration
     *
     * @return A configured output filter ready for use
     */
    OutputFilter createFilter();

    /**
     * Get the encoding name for this filter.
     * Used for Content-Encoding or Transfer-Encoding headers and
     * for matching against client Accept-Encoding preferences.
     *
     * @return The encoding name (e.g., "gzip", "br", "deflate", "zstd")
     */
    String getEncodingName();
}
