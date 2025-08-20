package com.odaishi.asheskingdoms.noapi;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public final class NOLog {
    private static final Logger LOG = LogManager.getLogger("NO-Compat");
    public static void info(String s, Object... args) { LOG.info(String.format(s, args)); }
    public static void warn(String s, Object... args) { LOG.warn(String.format(s, args)); }
    public static void error(String s, Object... args) { LOG.error(String.format(s, args)); }
}