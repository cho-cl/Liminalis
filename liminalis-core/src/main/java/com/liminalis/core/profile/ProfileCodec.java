package com.liminalis.core.profile;

import com.liminalis.core.injury.ActiveInjury;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public static final int CURRENT_SCHEMA_VERSION = 5;

    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final String ID = "id";
    private static final String LAST_KNOWN_NAME = "lastKnownName";
    private static final String LIVES_REMAINING = "livesRemaining";
    private static final String TOTAL_DEATHS = "totalDeaths";
    private static final String IN_LIMBO = "inLimbo";
    private static final String LIMBO_SINCE = "limboSince";
    private static final String GHOST_COOLDOWN_UNTIL = "ghostVisitCooldownUntil";
    private static final String CROSSING_COOLDOWN_UNTIL = "crossingCooldownUntil";
    private static final String STORED_INVENTORY = "storedInventory";
    private static final String TRAIT_IDS = "traitIds";
    private static final String BLESSING_ID = "blessingId";
    private static final String CURSE_ID = "curseId";
    private static final String MARK_IDS = "markIds";
    private static final String ABILITY_ID = "abilityId";
    private static final String ABILITY_TIER = "abilityTier";
    private static final String FIRST_JOIN_COMPLETE = "firstJoinComplete";
    private static final String FIRST_JOINED_AT = "firstJoinedAt";
    private static final String LAST_SEEN_AT = "lastSeenAt";
    private static final String INJURIES = "injuries";
    private static final String INJURY_ID = "id";
    private static final String INJURY_EXPIRES_AT = "expiresAt";
    private static final String ABILITY_PROGRESS = "abilityProgress";

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
        root.addProperty(CROSSING_COOLDOWN_UNTIL, profile.crossingCooldownUntil());
        root.addProperty(STORED_INVENTORY, profile.storedInventory());

        root.add(TRAIT_IDS, toArray(profile.traitIds()));
        root.addProperty(BLESSING_ID, profile.blessingId());
        root.addProperty(CURSE_ID, profile.curseId());
        root.add(MARK_IDS, toArray(profile.markIds()));

        root.addProperty(ABILITY_ID, profile.abilityId());
        root.addProperty(ABILITY_TIER, profile.abilityTier());

        root.addProperty(FIRST_JOIN_COMPLETE, profile.firstJoinComplete());
        root.addProperty(FIRST_JOINED_AT, profile.firstJoinedAt());
        root.addProperty(LAST_SEEN_AT, profile.lastSeenAt());

        JsonArray injuries = new JsonArray();
        for (ActiveInjury injury : profile.injuries()) {
            JsonObject entry = new JsonObject();
            entry.addProperty(INJURY_ID, injury.id());
            entry.addProperty(INJURY_EXPIRES_AT, injury.expiresAt());
            injuries.add(entry);
        }
        root.add(INJURIES, injuries);

        JsonObject progress = new JsonObject();
        profile.abilityProgress().forEach(progress::addProperty);
        root.add(ABILITY_PROGRESS, progress);

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
        profile.setCrossingCooldownUntil(optionalLong(root, CROSSING_COOLDOWN_UNTIL));
        profile.setStoredInventory(nullableString(root, STORED_INVENTORY));

        profile.replaceTraits(optionalStringList(root, TRAIT_IDS));
        profile.setBlessingId(nullableString(root, BLESSING_ID));
        profile.setCurseId(nullableString(root, CURSE_ID));
        profile.replaceMarks(optionalStringList(root, MARK_IDS));

        profile.setAbilityId(nullableString(root, ABILITY_ID));
        profile.setAbilityTier(optionalInt(root, ABILITY_TIER, 0));

        profile.setFirstJoinComplete(optionalBoolean(root, FIRST_JOIN_COMPLETE));
        profile.setFirstJoinedAt(optionalLong(root, FIRST_JOINED_AT));
        profile.setLastSeenAt(optionalLong(root, LAST_SEEN_AT));
        profile.replaceInjuries(readInjuries(root));
        profile.replaceAbilityProgress(readProgress(root));

        return profile;
    }

    /**
     * Upgrades an older document in place.
     *
     * <p>Cases fall through deliberately, so a version 1 profile is walked forward through
     * every step rather than needing a direct path to the present.
     */
    private void migrate(JsonObject root, int fromVersion) {
        if (fromVersion == CURRENT_SCHEMA_VERSION) {
            return;
        }
        switch (fromVersion) {
            case 1:
                // Version 2 added injuries. Nothing to convert - an absent array reads as
                // "no wounds", which is exactly right for a profile written before wounds
                // existed. The case is here so the walk-forward is explicit rather than
                // relying on the reader being lenient by accident.
                // fall through
            case 2:
                // Version 3 added ability progress counters. Like injuries before it, an
                // absent object reads as "no progress", which is right for a profile written
                // before the counters existed.
                // fall through
            case 3:
                // Version 4 retired the five stat-only blessings and four of the curses,
                // replacing them with ones that change what a player can DO rather than what
                // their numbers are. An id that no longer exists would sit in the profile
                // forever, resolving to nothing, applying nothing, and showing up on the
                // profile screen as a blessing the player does not actually have. Clearing
                // it is the honest outcome: they are unblessed, and an admin can grant them
                // one of the new ones.
                clearRetiredBoons(root);
                // fall through
            case 4:
                // Version 5 added the put-aside inventory. An absent field reads as "they
                // are carrying their own", which is right for every profile written before
                // anybody could fly a drone.
                // fall through
            case 5:
                break;
            default:
                throw new ProfileFormatException(
                        "no migration path from schema version " + fromVersion
                                + " to " + CURRENT_SCHEMA_VERSION);
        }
    }

    /**
     * Boon ids this build no longer has any code for.
     *
     * <p>Deliberately different from how unknown <em>trait</em> ids are treated, which are
     * left in place in case a build was rolled back. These are not missing by accident - they
     * were removed on purpose and are never coming back, so leaving them would be leaving a
     * player permanently holding nothing while being told they hold something.
     */
    private static final Set<String> RETIRED_BOONS = Set.of(
            "ironblood", "far_wanderer", "steady_hand", "thickskinned", "long_arms",
            "unshod", "brittle", "swiftbane", "shallow_lungs");

    private static void clearRetiredBoons(JsonObject root) {
        for (String field : List.of(BLESSING_ID, CURSE_ID)) {
            if (!present(root, field)) {
                continue;
            }
            JsonElement value = root.get(field);
            if (value.isJsonPrimitive() && RETIRED_BOONS.contains(value.getAsString())) {
                root.add(field, com.google.gson.JsonNull.INSTANCE);
            }
        }
    }

    private Map<String, Integer> readProgress(JsonObject root) {
        Map<String, Integer> progress = new LinkedHashMap<>();
        if (!present(root, ABILITY_PROGRESS)) {
            return progress;
        }
        JsonElement element = root.get(ABILITY_PROGRESS);
        if (!element.isJsonObject()) {
            throw new ProfileFormatException("field '" + ABILITY_PROGRESS + "' is not an object");
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                continue;
            }
            try {
                progress.put(entry.getKey(), value.getAsInt());
            } catch (ClassCastException | NumberFormatException | IllegalStateException e) {
                throw new ProfileFormatException("ability progress '" + entry.getKey()
                        + "' is not a number", e);
            }
        }
        return progress;
    }

    private List<ActiveInjury> readInjuries(JsonObject root) {
        List<ActiveInjury> injuries = new ArrayList<>();
        if (!present(root, INJURIES)) {
            return injuries;
        }
        JsonElement element = root.get(INJURIES);
        if (!element.isJsonArray()) {
            throw new ProfileFormatException("field '" + INJURIES + "' is not an array");
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry == null || !entry.isJsonObject()) {
                continue;
            }
            JsonObject injury = entry.getAsJsonObject();
            String id = nullableString(injury, INJURY_ID);
            if (id == null || id.isBlank()) {
                throw new ProfileFormatException("an injury entry is missing its id");
            }
            injuries.add(new ActiveInjury(id, optionalLong(injury, INJURY_EXPIRES_AT)));
        }
        return injuries;
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
