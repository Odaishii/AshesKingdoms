/**
 * NUMISMATIC OVERHAUL COMPATIBILITY LOGGER
 *
 * Dedicated logging utility for Numismatic Overhaul integration components.
 * Provides structured logging with consistent formatting for debugging and
 * monitoring NO compatibility issues.
 *
 * FEATURES:
 * - Dedicated log channel for NO-related messages
 * - Formatted message support with varargs
 * - Three log levels: info, warn, error
 * - Consistent logging format across NO integration
 *
 * LOG CATEGORIES:
 * - Info: Successful integration, component resolution
 * - Warn: Non-critical issues, fallback scenarios
 * - Error: Critical failures, integration problems
 *
 * USAGE:
 * Centralized logging for all NO adapter components ensuring consistent
 * error reporting and debugging information across the integration layer.
 */

package com.odaishi.asheskingdoms.noapi;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public final class NOLog {
    private static final Logger LOG = LogManager.getLogger("NO-Compat");
    public static void info(String s, Object... args) { LOG.info(String.format(s, args)); }
    public static void warn(String s, Object... args) { LOG.warn(String.format(s, args)); }
    public static int error(String s, Object... args) { LOG.error(String.format(s, args));
        return 0;
    }
}