package org.bytechen.rottenruins.utils.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared logging infrastructure, independent of the Forge bootstrap. */
public final class InfectionLog {
    public static final Logger LOGGER = LoggerFactory.getLogger("RottenRuins.Infection");
    public static final boolean VERBOSE = Boolean.parseBoolean(System.getProperty("infcore.verbose", "false"));
    private InfectionLog() {}
}
