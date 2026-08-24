package com.liminalis.plugin.boon;

/**
 * A boon that changes how many lives its owner has.
 *
 * <p>Kept separate from every other capability because it is the one effect that cannot be
 * applied while a player is online and undone when they leave. Lives are a saved counter that
 * only ever goes down; "you have one more" is therefore a single edit made at the moment the
 * boon is assigned, not a bonus recomputed on attach - which happens on every login, every
 * reload and every admin grant, and would hand out an unbounded supply of them.
 *
 * <p>{@code BoonAssignment} is the only thing that reads this, and it grants and revokes in
 * matched pairs. Nothing else should.
 */
public interface LifeGranting extends Boon {

    /** How many lives this boon is worth. Negative values take them away. */
    int extraLives();
}
