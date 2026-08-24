package com.liminalis.core.limbo;

import java.util.Random;

/**
 * Turns something said in Limbo into what the living faintly hear.
 *
 * <p>The message keeps its length and its spaces, so what arrives has the rhythm of a
 * sentence with the words worn out of it. That is deliberately different from sending noise:
 * the living should be able to tell that <em>someone said something</em>, and occasionally
 * catch a word, without being handed the meaning.
 */
public final class Whisper {

    /** Stands in for a lost character. Quiet on screen, and unmistakably a gap. */
    private static final char LOST = '.';

    private Whisper() {
    }

    /**
     * @param message      what was said in Limbo
     * @param keepFraction the chance each character survives; 0.0 loses everything, 1.0 loses
     *                     nothing
     * @param random       supplied rather than created, so this is testable
     */
    public static String garble(String message, double keepFraction, Random random) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        StringBuilder heard = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char character = message.charAt(i);
            // Whitespace always survives: it is what preserves the shape of the sentence.
            if (Character.isWhitespace(character) || random.nextDouble() < keepFraction) {
                heard.append(character);
            } else {
                heard.append(LOST);
            }
        }
        return heard.toString();
    }
}
