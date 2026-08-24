package com.liminalis.core.injury;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checks that the wound roster can answer for every kind of harm a player can take.
 *
 * <p>This exists because of a specific and completely silent failure. The classifier decides
 * severity from the damage alone - it never asks whether a wound of that severity exists for
 * that kind of harm. So a category with no mortal wound behind it produced this: a huge arrow
 * through the chest rolls MORTAL_WOUND, the roster is asked for a mortal piercing wound, there
 * is none, and the code returns having done nothing. A lighter arrow would have made the
 * player bleed. The hardest hits in the game were the only ones that left no mark, and nothing
 * anywhere logged a word about it.
 *
 * <p>The rule is therefore an invariant rather than a preference, and it is checked at
 * startup: <strong>every {@link DamageCategory} has at least one wound at every
 * {@link InjurySeverity} that can actually be inflicted.</strong> Get it wrong and the server
 * refuses to arm the injury system rather than quietly dropping wounds for a month.
 */
public final class InjuryCoverage {

    private InjuryCoverage() {
    }

    /**
     * One entry in the roster, reduced to what coverage depends on.
     *
     * @param id       which wound, so a gap report can name what is already there
     * @param causes   the kinds of harm that can leave it
     * @param severity how bad it is
     */
    public record Entry(String id, Set<DamageCategory> causes, InjurySeverity severity) {

        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(severity, "severity");
            causes = Set.copyOf(causes);
        }
    }

    /** The severities a blow can actually leave. {@code NONE} is the absence of a wound. */
    public static final Set<InjurySeverity> WOUNDING =
            Set.of(InjurySeverity.INJURY, InjurySeverity.MORTAL_WOUND);

    /**
     * Every hole in the roster, described well enough to fix without opening the code.
     *
     * @return one line per missing (category, severity) pair; empty when the roster is whole
     */
    public static List<String> gaps(Collection<Entry> roster) {
        Objects.requireNonNull(roster, "roster");

        Map<DamageCategory, Set<InjurySeverity>> covered = new EnumMap<>(DamageCategory.class);
        for (DamageCategory category : DamageCategory.values()) {
            covered.put(category, EnumSet.noneOf(InjurySeverity.class));
        }
        for (Entry entry : roster) {
            for (DamageCategory category : entry.causes()) {
                covered.get(category).add(entry.severity());
            }
        }

        List<String> gaps = new java.util.ArrayList<>();
        for (DamageCategory category : DamageCategory.values()) {
            for (InjurySeverity severity : List.of(
                    InjurySeverity.INJURY, InjurySeverity.MORTAL_WOUND)) {
                if (!covered.get(category).contains(severity)) {
                    gaps.add(category + " has no wound of severity " + severity
                            + " - a blow of that kind and size would be classified and then"
                            + " silently dropped");
                }
            }
        }
        return List.copyOf(gaps);
    }

    /**
     * Throws unless the roster covers everything.
     *
     * @throws IllegalStateException listing every gap at once, so one restart fixes them all
     */
    public static void require(Collection<Entry> roster) {
        List<String> gaps = gaps(roster);
        if (!gaps.isEmpty()) {
            throw new IllegalStateException("The injury roster has "
                    + gaps.size() + " gap(s):" + System.lineSeparator()
                    + String.join(System.lineSeparator(), gaps));
        }
    }
}
