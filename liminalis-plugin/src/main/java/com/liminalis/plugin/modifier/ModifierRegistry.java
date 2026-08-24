package com.liminalis.plugin.modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The catalogue of every modifier this build knows how to apply.
 *
 * <p>Also the source of truth for command tab-completion, so an admin never has to remember
 * an id and cannot typo one.
 */
public final class ModifierRegistry {

    private final Map<String, Modifier> byId = new LinkedHashMap<>();

    /**
     * @throws IllegalArgumentException if the id is already taken or is not a legal id -
     *         both are programming errors that must surface at startup, not mid-season
     */
    public void register(Modifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        String id = modifier.id();
        requireLegalId(id);
        Modifier existing = byId.putIfAbsent(id, modifier);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate modifier id '" + id + "': "
                    + existing.getClass().getName() + " and " + modifier.getClass().getName());
        }
    }

    public Optional<Modifier> find(String id) {
        return id == null ? Optional.empty()
                : Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<Modifier> ofType(ModifierType type) {
        List<Modifier> matches = new ArrayList<>();
        for (Modifier modifier : byId.values()) {
            if (modifier.type() == type) {
                matches.add(modifier);
            }
        }
        return List.copyOf(matches);
    }

    public Collection<Modifier> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    private static void requireLegalId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Modifier id must not be blank");
        }
        if (!id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Modifier id must be lowercase: " + id);
        }
        if (!id.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Modifier id may only contain a-z, 0-9 and underscores: " + id);
        }
    }
}
