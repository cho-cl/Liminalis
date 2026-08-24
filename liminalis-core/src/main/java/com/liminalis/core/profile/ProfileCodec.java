package com.liminalis.core.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes {@link PlayerProfile} as JSON.
 *
 * <p>The mapping is written out by hand rather than reflected over the class. That is
 * deliberate: with reflection, renaming a Java field silently orphans everyone's data and
 * nothing fails until a player logs in and finds themselves a stranger. Here, the on-disk
 * names are literals that only change when someone changes them on purpose.
 *
 * <p>The reader is strict about identity and lenient about everything else. A missing id or
 * life count is fatal, because guessing either one rewrites who somebody is. Unknown keys are
 * ignored so a profile written by a newer build still loads on an older one.
 */
public final class ProfileCodec {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final String ID = "id";
    private static final String LAST_KNOWN_NAME = "lastKnownName";
    private static final String LIVES_REMAINING = "livesRemaining";
    private static final String TOTAL_DEATHS = "totalDeaths";
    private static final String IN_LIMBO = "inLimbo";
    private static final String LIMBO_SINCE = "limboSince";
    private static final String GHOST_COOLDOWN_UNTIL = "ghostVisitCooldownUntil";
    private static final String TRAIT_IDS = "traitIds";
    private static final String BLESSING_ID = "blessingId";
    private static final String CURSE_ID = "curseId";
    private static final String MARK_IDS = "markIds";
    private static final String ABILITY_ID = "abilityId";
    private static final String ABILITY_TIER = "abilityTier";
    private static final String FIRST_JOIN_COMPLETE = "firstJoinComplete";
    private static final String FIRST_JOINED_AT = "firstJoinedAt";
    private static final String LAST_SEEN_AT = "lastSeenAt";

    private static final String UNKNOWN_NAME = "unknown";

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    public String toJson(PlayerProfile profile) {
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        root.addProperty(ID, profile.id().toString());
        root.addProperty(LAST_KNOWN_NAME, profile.lastKnownName());
        root.addProperty(LIVES_REMAINING, profile.livesRemaining());
        root.addProperty(TOTAL_DEATHS, profile.totalDeaths());

        root.addProperty(IN_LIMBO, profile.inLimbo());
        root.addProperty(LIMBO_SINCE, profile.limboSince());
        root.addProperty(GHOST_COOLDOWN_UNTIL, profile.ghostVisitCooldownUntil());

        root.add(TRAIT_IDS, toArray(profile.traitIds()));
        root.addProperty(BLESSING_ID, profile.blessingId());
        root.addProperty(CURSE_ID, profile.curseId());
        root.add(MARK_IDS, toArray(profile.markIds()));

        root.addProperty(ABILITY_ID, profile.abilityId());
        root.addProperty(ABILITY_TIER, profile.abilityTier());

        root.addProperty(FIRST_JOIN_COMPLETE, profile.firstJoinComplete());
        root.addProperty(FIRST_JOINED_AT, profile.firstJoinedAt());
        root.addProperty(LAST_SEEN_AT, profile.lastSeenAt());

        return gson.toJson(root);
    }

    public PlayerProfile fromJson(String json) {
        JsonObject root = parseObject(json);
        int version = readVersion(root);
        migrate(root, version);

        PlayerProfile profile = new PlayerProfile(
                requiredUuid(root, ID),
                optionalString(root, LAST_KNOWN_NAME, UNKNOWN_NAME),
                requiredInt(root, LIVES_REMAINING));

        profile.setTotalDeaths(optionalInt(root, TOTAL_DEATHS, 0));

        profile.setInLimbo(optionalBoolean(root, IN_LIMBO));
        profile.setLimboSince(optionalLong(root, LIMBO_SINCE));
        profile.setGhostVisitCooldownUntil(optionalLong(root, GHOST_COOLDOWN_UNTIL));

        profile.replaceTraits(optionalStringList(root, TRAIT_IDS));
        profile.setBlessingId(nullableString(root, BLESSING_ID));
        profile.setCurseId(nullableString(root, CURSE_ID));
        profile.replaceMarks(optionalStringList(root, MARK_IDS));

        profile.setAbilityId(nullableString(root, ABILITY_ID));
        profile.setAbilityTier(optionalInt(root, ABILITY_TIER, 0));

        profile.setFirstJoinComplete(optionalBoolean(root, FIRST_JOIN_COMPLETE));
        profile.setFirstJoinedAt(optionalLong(root, FIRST_JOINED_AT));
        profile.setLastSeenAt(optionalLong(root, LAST_SEEN_AT));

        return profile;
    }

    /**
     * Upgrades an older document in place. Nothing to do yet - there is only one schema
     * version - but the call site exists so that adding version 2 is an edit here rather
     * than a redesign.
     */
    private void migrate(JsonObject root, int fromVersion) {
        if (fromVersion == CURRENT_SCHEMA_VERSION) {
            return;
        }
        // switch (fromVersion) { case 1: upgrade1to2(root); /* fall through */ }
        throw new ProfileFormatException(
                "no migration path from schema version " + fromVersion
                        + " to " + CURRENT_SCHEMA_VERSION);
    }

    private JsonObject parseObject(String json) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (JsonSyntaxException e) {
            throw new ProfileFormatException("profile is not valid JSON", e);
        }
        if (parsed == null || !parsed.isJsonObject()) {
            throw new ProfileFormatException("profile is not a JSON object");
        }
        return parsed.getAsJsonObject();
    }

    private int readVersion(JsonObject root) {
        int version = optionalInt(root, SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        if (version > CURRENT_SCHEMA_VERSION) {
            throw new ProfileFormatException(
                    "profile was written by a newer build (schema version " + version
                            + ", this build understands " + CURRENT_SCHEMA_VERSION + ")");
        }
        return version;
    }

    private static JsonArray toArray(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static boolean present(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && !element.isJsonNull();
    }

    private UUID requiredUuid(JsonObject root, String key) {
        if (!present(root, key)) {
            throw new ProfileFormatException("profile is missing required field '" + key + "'");
        }
        String raw = root.get(key).getAsString();
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ProfileFormatException("field '" + key + "' is not a UUID: " + raw, e);
        }
    }

    private int requiredInt(JsonObject root, String key) {
        if (!present(root, key)) {
            throw new ProfileFormatException("profile is missing required field '" + key + "'");
        }
        return readInt(root, key);
    }

    private int optionalInt(JsonObject root, String key, int fallback) {
        return present(root, key) ? readInt(root, key) : fallback;
    }

    private int readInt(JsonObject root, String key) {
        try {
            return root.get(key).getAsInt();
        } catch (ClassCastException | NumberFormatException | IllegalStateException e) {
            throw new ProfileFormatException("field '" + key + "' is not a number", e);
        }
    }

    private long optionalLong(JsonObject root, String key) {
        if (!present(root, key)) {
            return 0L;
        }
        try {
            return root.get(key).getAsLong();
        } catch (ClassCastException | NumberFormatException | IllegalStateException e) {
            throw new ProfileFormatException("field '" + key + "' is not a number", e);
        }
    }

    private boolean optionalBoolean(JsonObject root, String key) {
        if (!present(root, key)) {
            return false;
        }
        try {
            return root.get(key).getAsBoolean();
        } catch (ClassCastException | IllegalStateException e) {
            throw new ProfileFormatException("field '" + key + "' is not a boolean", e);
        }
    }

    private String optionalString(JsonObject root, String key, String fallback) {
        String value = nullableString(root, key);
        return value == null ? fallback : value;
    }

    private String nullableString(JsonObject root, String key) {
        if (!present(root, key)) {
            return null;
        }
        try {
            return root.get(key).getAsString();
        } catch (ClassCastException | IllegalStateException e) {
            throw new ProfileFormatException("field '" + key + "' is not a string", e);
        }
    }

    private List<String> optionalStringList(JsonObject root, String key) {
        List<String> values = new ArrayList<>();
        if (!present(root, key)) {
            return values;
        }
        JsonElement element = root.get(key);
        if (!element.isJsonArray()) {
            throw new ProfileFormatException("field '" + key + "' is not an array");
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry != null && !entry.isJsonNull()) {
                values.add(entry.getAsString());
            }
        }
        return values;
    }

    /**
     * Thrown when a profile cannot be read. Callers must treat this as a hard failure and
     * refuse the login rather than substituting a fresh profile, which would look to the
     * player exactly like having their identity deleted.
     */
    public static final class ProfileFormatException extends RuntimeException {

        public ProfileFormatException(String message) {
            super(message);
        }

        public ProfileFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
