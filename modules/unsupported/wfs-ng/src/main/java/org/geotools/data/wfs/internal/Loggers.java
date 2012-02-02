package org.geotools.data.wfs.internal;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.util.logging.Logging;

public final class Loggers {

    private Loggers() {
        //
    }

    private static final class Requests{}
    private static final class Responses{}
    private static final class Internal{}
    public static final Logger MODULE = Logger.getLogger("org.geotools.data.wfs");// Logging.getLogger(Internal.class);

    public static final Logger REQUESTS = Logger.getLogger("org.geotools.data.wfs.requests");//Logging.getLogger(Requests.class);

    public static final Logger RESPONSES = Logger.getLogger("org.geotools.data.wfs.responses");//Logging.getLogger(Responses.class);

    public static final Level MODULE_TRACE_LEVEL = Level.FINER;

    public static final Level MODULE_DEBUG_LEVEL = Level.FINE;

    public static final Level MODULE_INFO_LEVEL = Level.INFO;

    public static final Level REQUEST_TRACE_LEVEL = Level.INFO;// TODO: lower this

    public static final Level REQUEST_DEBUG_LEVEL = Level.INFO;// TODO: lower this

    public static final Level RESPONSES_TRACE_LEVEL = Level.INFO;// TODO: lower this

    public static final Level RESPONSES_DEBUG_LEVEL = Level.INFO;// TODO: lower this

    public static void trace(Object... message) {
        log(MODULE, MODULE_TRACE_LEVEL, message);
    }

    public static void debug(Object... message) {
        log(MODULE, MODULE_DEBUG_LEVEL, message);
    }

    public static void info(Object... message) {
        log(MODULE, MODULE_INFO_LEVEL, message);
    }

    public static void requestTrace(Object... message) {
        log(REQUESTS, REQUEST_TRACE_LEVEL, message);
    }

    public static void requestDebug(Object... message) {
        log(REQUESTS, REQUEST_DEBUG_LEVEL, message);
    }

    public static void responseTrace(Object... message) {
        log(RESPONSES, RESPONSES_TRACE_LEVEL, message);
    }

    public static void responseDebug(Object... message) {
        log(RESPONSES, RESPONSES_DEBUG_LEVEL, message);
    }

    private static void log(Logger logger, Level level, Object... message) {
        if (logger.isLoggable(level)) {
            // miss guava Joiner....
            StringBuilder sb = new StringBuilder();
            for (Object part : message) {
                sb.append(part);
            }
            logger.log(level, sb.toString());
        }
    }

}
