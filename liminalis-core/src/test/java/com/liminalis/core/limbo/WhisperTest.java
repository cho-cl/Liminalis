package com.liminalis.core.limbo;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the living hear when the dead speak: most of it lost, some of it not.
 *
 * <p>Takes its {@link Random} as a parameter so the behaviour can be pinned down at all,
 * rather than being untestable by nature.
 */
class WhisperTest {

    private static final String MESSAGE = "does anyone remember how to bring me back";

    @Test
    void aWhisperIsTheSameLengthAsWhatWasSaid() {
        // The shape of the sentence survives even though the words do not. That is what
        // makes it read as something almost heard rather than as noise.
        String heard = Whisper.garble(MESSAGE, 0.4, new Random(1));

        assertThat(heard).hasSameSizeAs(MESSAGE);
    }

    @Test
    void mostOfItIsLost() {
        String heard = Whisper.garble(MESSAGE, 0.4, new Random(1));

        assertThat(heard).isNotEqualTo(MESSAGE);
        long kept = countMatching(MESSAGE, heard);
        assertThat(kept).isLessThan(MESSAGE.length());
    }

    @Test
    void keepingEverythingLeavesItUntouched() {
        assertThat(Whisper.garble(MESSAGE, 1.0, new Random(1))).isEqualTo(MESSAGE);
    }

    @Test
    void keepingNothingLosesEveryLetter() {
        String heard = Whisper.garble(MESSAGE, 0.0, new Random(1));

        assertThat(heard).hasSameSizeAs(MESSAGE);
        assertThat(countLetters(heard)).isZero();
    }

    @Test
    void spacesAreAlwaysKeptSoTheRhythmOfSpeechRemains() {
        String heard = Whisper.garble(MESSAGE, 0.0, new Random(1));

        for (int i = 0; i < MESSAGE.length(); i++) {
            if (MESSAGE.charAt(i) == ' ') {
                assertThat(heard.charAt(i)).isEqualTo(' ');
            }
        }
    }

    @Test
    void theSameSeedAlwaysProducesTheSameWhisper() {
        assertThat(Whisper.garble(MESSAGE, 0.4, new Random(7)))
                .isEqualTo(Whisper.garble(MESSAGE, 0.4, new Random(7)));
    }

    @Test
    void anEmptyMessageStaysEmpty() {
        assertThat(Whisper.garble("", 0.4, new Random(1))).isEmpty();
    }

    private static long countMatching(String original, String heard) {
        long matching = 0;
        for (int i = 0; i < original.length(); i++) {
            if (original.charAt(i) == heard.charAt(i)) {
                matching++;
            }
        }
        return matching;
    }

    private static long countLetters(String text) {
        return text.chars().filter(Character::isLetterOrDigit).count();
    }
}
