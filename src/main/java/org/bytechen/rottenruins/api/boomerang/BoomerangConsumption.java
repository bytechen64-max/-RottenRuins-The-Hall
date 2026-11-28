package org.bytechen.rottenruins.api.boomerang;

/** Costs are charged once on a successful launch, never again on hit or return. */
public enum BoomerangConsumption {
    NONE,
    DURABILITY,
    /** Permanently consumes items; returning projectiles do not refund them. */
    ITEM_COUNT
}
