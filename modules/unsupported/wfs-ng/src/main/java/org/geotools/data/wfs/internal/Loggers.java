package org.geotools.data.wfs.internal;

import java.util.logging.Logger;

import org.geotools.util.logging.Logging;

public final class Loggers {

    private Loggers() {
        //
    }

    public static final Logger MODULE = Logging.getLogger("org.geotools.data.wfs");

    public static final Logger REQUESTS = Logging.getLogger("org.geotools.wfs.requests");

    public static final Logger RESPONSES = Logging.getLogger("org.geotools.wfs.responses");
}
