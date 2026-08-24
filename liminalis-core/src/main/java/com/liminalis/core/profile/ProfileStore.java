package com.liminalis.core.profile;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent storage for player profiles.
 *
 * <p>Everything above this interface works in terms of profiles, not files, so moving to a
 * database later is a matter of writing one more implementation.
 *
 * <p>Implementations must never invent a profile to paper over a read failure. An empty
 * {@link Optional} means "this player is genuinely new"; anything else must throw.
 */
public interface ProfileStore {

    /** The stored profile, or empty if this player has never been seen. */
    Optional<PlayerProfile> load(UUID id);

    /** Writes the profile durably, replacing any previous version. */
    void save(PlayerProfile profile);

    /** Every player id with a stored profile, including those who are offline. */
    Set<UUID> knownIds();
}
