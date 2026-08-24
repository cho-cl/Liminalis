package com.liminalis.core.rescue;

/**
 * How a living player's trip into the grey ended.
 */
public enum CrossingOutcome {

    /** Back in time, unchanged. */
    RETURNED,

    /** Back, but the grey kept a life for the trouble. */
    RETURNED_DIMINISHED,

    /** Not back. They stayed too long with nothing left to give, and Limbo kept them. */
    STRANDED
}
