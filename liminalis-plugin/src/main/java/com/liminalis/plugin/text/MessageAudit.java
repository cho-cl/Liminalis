package com.liminalis.plugin.text;

import com.liminalis.plugin.ability.Ability;
import com.liminalis.plugin.ability.Power;
import com.liminalis.plugin.modifier.Modifier;
import com.liminalis.plugin.modifier.ModifierRegistry;
import com.liminalis.plugin.modifier.capability.Restriction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Asks, at startup, for every message key the registry is going to need.
 *
 * <p>Missing keys are the most persistent bug on this project, and they are invisible by
 * construction: nothing fails, nothing logs, and the text simply renders as
 * "[missing message: ability.priest.smote]" on the screen of whoever triggered the one line
 * that needed it. A whole ability read as broken to its owner for that reason alone - every
 * power fired correctly and every word of feedback was a placeholder.
 *
 * <p>A script over the source catches keys that are written out in full. It cannot catch the
 * ones built at runtime out of a modifier id and a suffix, which are exactly the ones nobody
 * remembers to write, because a new ability adds a dozen of them at once. So this asks the
 * real registry instead: every modifier that exists, every key the framework will ever ask
 * for it, checked against the file actually loaded.
 *
 * <p>Logged rather than thrown. A missing line is embarrassing, not dangerous, and refusing
 * to start a live server over one would be a worse failure than the one being prevented.
 */
public final class MessageAudit {

    private MessageAudit() {
    }

    /**
     * Reports every key the registry needs and messages.yml does not have.
     *
     * @return the missing keys, in the order they would be asked for
     */
    public static List<String> missingKeys(ModifierRegistry registry, Messages messages) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(messages, "messages");

        List<String> missing = new ArrayList<>();
        for (Modifier modifier : registry.all()) {
            check(messages, missing, modifier.nameKey());
            check(messages, missing, modifier.descriptionKey());

            if (modifier instanceof Restriction restriction) {
                check(messages, missing, restriction.refusalKey());
            }
            if (modifier instanceof Ability ability) {
                for (int level = 1; level <= ability.maxLevel(); level++) {
                    check(messages, missing, ability.levelKey(level));
                }
                for (Power power : ability.powers()) {
                    String base = "ability." + ability.id() + "." + power.id();
                    check(messages, missing, base + ".name");
                    check(messages, missing, base + ".description");
                }
            }
        }
        return missing;
    }

    private static void check(Messages messages, List<String> missing, String key) {
        if (!messages.has(key)) {
            missing.add(key);
        }
    }
}
