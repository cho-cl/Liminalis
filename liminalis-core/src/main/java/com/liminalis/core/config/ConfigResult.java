package com.liminalis.core.config;

import java.util.List;

/**
 * The outcome of reading a config file: either a usable config, or the reasons it was refused.
 *
 * <p>{@link #config()} is deliberately null when the config is invalid. A partially-valid
 * config is the dangerous case - it starts the server with some of your numbers and some
 * defaults, and looks like it worked.
 *
 * @param config the parsed config, or null if any error was found
 * @param errors every problem found, in the order encountered; empty when valid
 */
public record ConfigResult(LiminalisConfig config, List<String> errors) {

    public ConfigResult {
        errors = List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    static ConfigResult ok(LiminalisConfig config) {
        return new ConfigResult(config, List.of());
    }

    static ConfigResult failed(List<String> errors) {
        return new ConfigResult(null, errors);
    }
}
