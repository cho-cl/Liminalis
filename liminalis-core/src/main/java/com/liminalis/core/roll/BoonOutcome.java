package com.liminalis.core.roll;

/**
 * The result of a blessing/curse roll.
 *
 * @param kind what happened
 * @param id   the modifier id granted, or null when {@code kind} is {@link BoonKind#NONE}
 */
public record BoonOutcome(BoonKind kind, String id) {

    public static final BoonOutcome NOTHING = new BoonOutcome(BoonKind.NONE, null);
}
