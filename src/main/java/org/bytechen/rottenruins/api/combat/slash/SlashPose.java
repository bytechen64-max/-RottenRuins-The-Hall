package org.bytechen.rottenruins.api.combat.slash;

/** Directions are relative to the attacker, with right-to-left as the first default cut. */
public enum SlashPose {
    RIGHT_TO_LEFT, LEFT_TO_RIGHT, UPPER_RIGHT_TO_LOWER_LEFT, UPPER_LEFT_TO_LOWER_RIGHT;

    public double right() { return switch (this) {
        case RIGHT_TO_LEFT -> -1;
        case LEFT_TO_RIGHT -> 1;
        case UPPER_RIGHT_TO_LOWER_LEFT -> -Math.sqrt(0.5);
        case UPPER_LEFT_TO_LOWER_RIGHT -> Math.sqrt(0.5);
    }; }
    public double up() { return ordinal() >= 2 ? -Math.sqrt(0.5) : 0; }
}
