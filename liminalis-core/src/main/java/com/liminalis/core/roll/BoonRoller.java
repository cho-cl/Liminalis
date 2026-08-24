package com.liminalis.core.roll;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Decides whether a player is blessed, cursed, or neither.
 *
 * <p>One uniform draw is split between the two outcomes rather than two draws taken in
 * sequence. That is the whole reason this class exists: rolling for a blessing and then
 * rolling for a curse only if the first missed makes the curse rarer than its configured
 * number, because it is only ever offered to the players the blessing passed over. With
 * 15% and 15% that quietly turns into 15% and 12.75%, and nothing about the code would
 * look wrong.
 */
public final class BoonRoller {

    private final List<WeightedEntry> blessings;
    private final List<WeightedEntry> curses;

    public BoonRoller(List<WeightedEntry> blessings, List<WeightedEntry> curses) {
        this.blessings = List.copyOf(Objects.requireNonNull(blessings, "blessings"));
        this.curses = List.copyOf(Objects.requireNonNull(curses, "curses"));
    }

    public BoonOutcome roll(BoonRollSettings settings, Random random) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(random, "random");

        double draw = random.nextDouble();
        double blessingChance = settings.blessingChance();

        // [0, blessing) is blessed, [blessing, blessing + curse) is cursed, the rest is
        // untouched. Exclusivity falls out of the arithmetic rather than being enforced.
        if (draw < blessingChance) {
            return granted(BoonKind.BLESSING, blessings, random);
        }
        if (draw < blessingChance + settings.curseChance()) {
            return granted(BoonKind.CURSE, curses, random);
        }
        return BoonOutcome.NOTHING;
    }

    /**
     * Draws from a pool, falling back to nothing if it is empty.
     *
     * <p>A build that configures curses but registers none must not stop anyone logging in.
     */
    private BoonOutcome granted(BoonKind kind, List<WeightedEntry> pool, Random random) {
        return WeightedPool.pick(pool, Set.of(), random)
                .map(id -> new BoonOutcome(kind, id))
                .orElse(BoonOutcome.NOTHING);
    }
}
