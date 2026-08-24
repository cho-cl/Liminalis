package com.liminalis.core.config;

import com.liminalis.core.roll.TraitRollSettings;

import java.util.Map;

/**
 * Trait configuration: how the roll behaves, and the numbers the traits themselves use.
 *
 * <p>The tuning map is deliberately open rather than a fixed set of fields. It is what makes
 * the authoring model work: a trait is a small Java class, but every constant in it is looked
 * up by key, so rebalancing one never needs a rebuild. Keys nobody reads are simply ignored,
 * which means an old key left behind after a trait is removed is harmless.
 *
 * @param roll   how many traits are granted and how often the Singularity pool is reached for
 * @param tuning numbers beneath {@code traits.tuning} in config.yml, keyed without the prefix
 */
public record TraitSettings(TraitRollSettings roll, Map<String, Double> tuning) {

    public TraitSettings {
        tuning = Map.copyOf(tuning);
    }

    public static final TraitSettings DEFAULTS =
            new TraitSettings(TraitRollSettings.DEFAULTS, Map.of());
}
