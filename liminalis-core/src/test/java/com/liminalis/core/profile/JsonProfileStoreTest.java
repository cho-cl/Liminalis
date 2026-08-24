package com.liminalis.core.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonProfileStoreTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @TempDir
    Path dir;

    private JsonProfileStore store;

    @BeforeEach
    void setUp() {
        store = new JsonProfileStore(dir);
    }

    private PlayerProfile profile(UUID id, String name, int lives) {
        return PlayerProfile.createNew(id, name, lives);
    }

    @Test
    void savedProfileCanBeLoadedBack() {
        PlayerProfile saved = profile(ID, "Aero", 3);
        saved.setLivesRemaining(1);
        saved.addTrait("resilience");

        store.save(saved);

        PlayerProfile loaded = store.load(ID).orElseThrow();
        assertThat(loaded.id()).isEqualTo(ID);
        assertThat(loaded.lastKnownName()).isEqualTo("Aero");
        assertThat(loaded.livesRemaining()).isEqualTo(1);
        assertThat(loaded.traitIds()).containsExactly("resilience");
    }

    @Test
    void loadingSomeoneNeverSeenReturnsEmpty() {
        assertThat(store.load(ID)).isEmpty();
    }

    @Test
    void savingTwiceOverwritesRatherThanAccumulatingFiles() throws IOException {
        PlayerProfile p = profile(ID, "Aero", 3);
        store.save(p);
        p.setLivesRemaining(2);
        store.save(p);

        assertThat(jsonFilesIn(dir)).hasSize(1);
        assertThat(store.load(ID).orElseThrow().livesRemaining()).isEqualTo(2);
    }

    @Test
    void saveLeavesNoTemporaryFilesBehind() throws IOException {
        // A stray .tmp would be harmless, but it is also the tell-tale of a write that did
        // not complete its move. If one is ever left, the atomic swap is not happening.
        store.save(profile(ID, "Aero", 3));

        try (Stream<Path> entries = Files.list(dir)) {
            assertThat(entries.map(p -> p.getFileName().toString()))
                    .allMatch(name -> name.endsWith(".json"));
        }
    }

    @Test
    void aFailedWriteLeavesThePreviousProfileIntact() throws IOException {
        // This is the whole point of the temp-file-and-swap. If the store wrote straight to
        // the real file, a write that died partway would leave the player's identity
        // truncated and unreadable. Here the previous good profile must survive untouched.
        PlayerProfile p = profile(ID, "Aero", 3);
        store.save(p);

        // Occupy the temp path with a directory so the write cannot possibly succeed.
        Files.createDirectory(dir.resolve(ID + ".json.tmp"));

        p.setLivesRemaining(0);
        assertThatThrownBy(() -> store.save(p))
                .isInstanceOf(JsonProfileStore.ProfileStorageException.class);

        assertThat(store.load(ID).orElseThrow().livesRemaining()).isEqualTo(3);
    }

    @Test
    void corruptProfileFailsLoudlyRatherThanReturningAFreshOne() throws IOException {
        // Silently returning a new profile here would look, to the player, exactly like
        // having their three lives and their ability quietly deleted.
        Files.writeString(dir.resolve(ID + ".json"), "{ not json at all",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> store.load(ID))
                .isInstanceOf(ProfileCodec.ProfileFormatException.class);
    }

    @Test
    void knownIdsListsEverySavedProfile() {
        store.save(profile(ID, "Aero", 3));
        store.save(profile(OTHER_ID, "Someone", 3));

        assertThat(store.knownIds()).containsExactlyInAnyOrder(ID, OTHER_ID);
    }

    @Test
    void knownIdsIgnoresFilesThatAreNotProfiles() throws IOException {
        store.save(profile(ID, "Aero", 3));
        Files.writeString(dir.resolve("notes.txt"), "ignore me", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("not-a-uuid.json"), "{}", StandardCharsets.UTF_8);

        assertThat(store.knownIds()).containsExactly(ID);
    }

    @Test
    void createsItsDirectoryIfTheServerHasNeverRunBefore() {
        Path fresh = dir.resolve("nested/players");

        new JsonProfileStore(fresh).save(profile(ID, "Aero", 3));

        assertThat(Files.isDirectory(fresh)).isTrue();
    }

    private static List<Path> jsonFilesIn(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
        }
    }
}
