package com.liminalis.core.profile;

import com.liminalis.core.injury.ActiveInjury;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything Liminalis remembers about a player.
 *
 * <p>Mutable by design: this is the live, cached representation of a player that gameplay
 * writes to many times a session. Immutability here would mean re-inserting into the cache
 * on every life spent and every injury taken, for no benefit.
 *
 * <p>Collections are exposed as unmodifiable views. Mutate them through the add/remove
 * methods so there is exactly one path by which a player's identity can change.
 */
public final class PlayerProfile {

    private final UUID id;

    private String lastKnownName;
    private int livesRemaining;
    private int totalDeaths;

    private boolean inLimbo;
    private long limboSince;
    private long ghostVisitCooldownUntil;

    /**
     * Epoch millis before which a living player may not open a crossing into Limbo.
     *
     * <p>The mirror of {@link #ghostVisitCooldownUntil}: that one paces the dead coming out,
     * this one paces the Untethered going in. Persisted rather than kept in memory so a
     * restart is not a way to skip it.
     */
    private long crossingCooldownUntil;

    /**
     * A player's own inventory, put aside while they are flying a drone.
     *
     * <p>On the profile rather than in memory, and that is the entire reason it is safe to
     * empty somebody's pack at all. Holding it in a map would mean a crash, a kill -9 or a
     * power cut between taking it and giving it back costs a player everything they own -
     * on a server where a life is one of three. Written to disk before the inventory is
     * cleared and read back on the next login if it is still here, so the worst a crash can
     * do is give it back a minute later than expected.
     *
     * <p>Opaque to core, which has no idea what an ItemStack is. The plugin encodes it.
     */
    private String storedInventory;

    private final Set<String> traitIds = new LinkedHashSet<>();
    private String blessingId;
    private String curseId;
    private final Set<String> markIds = new LinkedHashSet<>();

    private String abilityId;
    private int abilityTier;

    /**
     * Counters an ability increments as its owner does the things it cares about.
     *
     * <p>Open-ended and namespaced by ability, so a new ability defines its own counters
     * without anything here needing to know they exist.
     */
    private final Map<String, Integer> abilityProgress = new LinkedHashMap<>();

    /**
     * Wounds currently carried.
     *
     * <p>A list rather than a set of ids, because unlike every other modifier an injury has
     * state of its own - when it fades. Cleared wholesale on respawn.
     */
    private final List<ActiveInjury> injuries = new ArrayList<>();

    private boolean firstJoinComplete;
    private long firstJoinedAt;
    private long lastSeenAt;

    PlayerProfile(UUID id, String lastKnownName, int livesRemaining) {
        this.id = Objects.requireNonNull(id, "id");
        this.lastKnownName = Objects.requireNonNull(lastKnownName, "lastKnownName");
        this.livesRemaining = livesRemaining;
    }

    /** A profile for someone the server has never seen before. */
    public static PlayerProfile createNew(UUID id, String name, int startingLives) {
        return new PlayerProfile(id, name, startingLives);
    }

    public UUID id() {
        return id;
    }

    public String lastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = Objects.requireNonNull(lastKnownName, "lastKnownName");
    }

    public int livesRemaining() {
        return livesRemaining;
    }

    public void setLivesRemaining(int livesRemaining) {
        this.livesRemaining = livesRemaining;
    }

    public int totalDeaths() {
        return totalDeaths;
    }

    public void setTotalDeaths(int totalDeaths) {
        this.totalDeaths = totalDeaths;
    }

    public boolean inLimbo() {
        return inLimbo;
    }

    public void setInLimbo(boolean inLimbo) {
        this.inLimbo = inLimbo;
    }

    /** Epoch millis the player fell to Limbo, or 0 if they never have. */
    public long limboSince() {
        return limboSince;
    }

    public void setLimboSince(long limboSince) {
        this.limboSince = limboSince;
    }

    /** Epoch millis before which the next ghost visit is refused. */
    public long ghostVisitCooldownUntil() {
        return ghostVisitCooldownUntil;
    }

    public void setGhostVisitCooldownUntil(long ghostVisitCooldownUntil) {
        this.ghostVisitCooldownUntil = ghostVisitCooldownUntil;
    }

    /** Epoch millis before which the next crossing into Limbo is refused. */
    public long crossingCooldownUntil() {
        return crossingCooldownUntil;
    }

    public void setCrossingCooldownUntil(long crossingCooldownUntil) {
        this.crossingCooldownUntil = crossingCooldownUntil;
    }

    /** The put-aside inventory, or null when the player is carrying their own. */
    public String storedInventory() {
        return storedInventory;
    }

    public void setStoredInventory(String storedInventory) {
        this.storedInventory = storedInventory;
    }

    public Set<String> traitIds() {
        return Collections.unmodifiableSet(traitIds);
    }

    public boolean addTrait(String traitId) {
        return traitIds.add(Objects.requireNonNull(traitId, "traitId"));
    }

    public boolean removeTrait(String traitId) {
        return traitIds.remove(traitId);
    }

    public String blessingId() {
        return blessingId;
    }

    public void setBlessingId(String blessingId) {
        this.blessingId = blessingId;
    }

    public String curseId() {
        return curseId;
    }

    public void setCurseId(String curseId) {
        this.curseId = curseId;
    }

    public Set<String> markIds() {
        return Collections.unmodifiableSet(markIds);
    }

    public boolean addMark(String markId) {
        return markIds.add(Objects.requireNonNull(markId, "markId"));
    }

    public boolean removeMark(String markId) {
        return markIds.remove(markId);
    }

    public String abilityId() {
        return abilityId;
    }

    public void setAbilityId(String abilityId) {
        this.abilityId = abilityId;
    }

    public int abilityTier() {
        return abilityTier;
    }

    public void setAbilityTier(int abilityTier) {
        this.abilityTier = abilityTier;
    }

    /** Whether the first-join roll (traits, blessing/curse) has already happened. */
    public boolean firstJoinComplete() {
        return firstJoinComplete;
    }

    public void setFirstJoinComplete(boolean firstJoinComplete) {
        this.firstJoinComplete = firstJoinComplete;
    }

    public long firstJoinedAt() {
        return firstJoinedAt;
    }

    public void setFirstJoinedAt(long firstJoinedAt) {
        this.firstJoinedAt = firstJoinedAt;
    }

    public long lastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Map<String, Integer> abilityProgress() {
        return Collections.unmodifiableMap(abilityProgress);
    }

    /** Adds to a counter, creating it if this is the first time. */
    public void addAbilityProgress(String counterKey, int amount) {
        Objects.requireNonNull(counterKey, "counterKey");
        if (amount == 0) {
            return;
        }
        abilityProgress.merge(counterKey, amount, Integer::sum);
    }

    public void setAbilityProgress(String counterKey, int value) {
        Objects.requireNonNull(counterKey, "counterKey");
        abilityProgress.put(counterKey, value);
    }

    /** Wipes every counter. Used when an ability is reassigned. */
    public void clearAbilityProgress() {
        abilityProgress.clear();
    }

    /** Used by {@link ProfileCodec} when rehydrating from disk. */
    void replaceAbilityProgress(Map<String, Integer> restored) {
        abilityProgress.clear();
        abilityProgress.putAll(restored);
    }

    public List<ActiveInjury> injuries() {
        return Collections.unmodifiableList(injuries);
    }

    /** Adds a wound, replacing any existing one of the same kind rather than stacking it. */
    public void addInjury(ActiveInjury injury) {
        Objects.requireNonNull(injury, "injury");
        removeInjury(injury.id());
        injuries.add(injury);
    }

    public boolean removeInjury(String injuryId) {
        return injuries.removeIf(existing -> existing.id().equals(injuryId));
    }

    public boolean hasInjury(String injuryId) {
        return injuries.stream().anyMatch(existing -> existing.id().equals(injuryId));
    }

    /** Wipes every wound. This is what respawning does - a new body carries no old harm. */
    public void clearInjuries() {
        injuries.clear();
    }

    /** Used by {@link ProfileCodec} when rehydrating from disk. */
    void replaceInjuries(Collection<ActiveInjury> restored) {
        injuries.clear();
        injuries.addAll(restored);
    }

    /** Used by {@link ProfileCodec} when rehydrating from disk. */
    void replaceTraits(Collection<String> ids) {
        traitIds.clear();
        traitIds.addAll(ids);
    }

    /** Used by {@link ProfileCodec} when rehydrating from disk. */
    void replaceMarks(Collection<String> ids) {
        markIds.clear();
        markIds.addAll(ids);
    }

    @Override
    public String toString() {
        return "PlayerProfile[" + id + " " + lastKnownName
                + " lives=" + livesRemaining + " limbo=" + inLimbo + "]";
    }
}
