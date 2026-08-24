package com.liminalis.plugin.trait;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The numbers behind the traits, overridable from config without a rebuild.
 *
 * <p>This is the practical half of how traits are authored: behaviour is a small Java class,
 * but every constant it uses is looked up here. Adding a trait needs a rebuild; rebalancing
 * one at eleven at night does not.
 *
 * <p>Values are read through a supplier rather than copied in, and traits must look them up
 * <em>at the point of use</em> rather than caching them in a constructor. Otherwise
 * {@code /liminalis reload} would report success and change nothing, which is worse than not
 * offering a reload at all.
 */
public final class TraitTuning {

    private final Supplier<Map<String, Double>> values;

    public TraitTuning(Supplier<Map<String, Double>> values) {
        this.values = Objects.requireNonNull(values, "values");
    }

    /**
     * @param key      dotted key beneath {@code traits.tuning} in config.yml
     * @param fallback the value used when config says nothing, which is also the documented
     *                 default for the trait
     */
    public double get(String key, double fallback) {
        Double configured = values.get().get(key);
        return configured == null ? fallback : configured;
    }
}
