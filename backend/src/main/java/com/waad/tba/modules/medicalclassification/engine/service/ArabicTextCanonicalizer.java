package com.waad.tba.modules.medicalclassification.engine.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conservative Arabic/Latin canonicalization for EXACT lookups
 * (category-name resolution, alias matching).
 *
 * Deliberately simpler than the Python engine's fuzzy normalization — this is
 * only used for equality matching of names WAAD itself stored, never for
 * fuzzy scoring (which stays exclusively in the Python engine, plan §13 R5).
 */
public final class ArabicTextCanonicalizer {

    private ArabicTextCanonicalizer() {
    }

    /** Canonical form: lower-cased, diacritics/tatweel stripped, unified alef/taa, per-token "ال" removed. */
    public static String canonicalize(String text) {
        if (text == null) {
            return "";
        }
        String s = text.toLowerCase(Locale.ROOT)
                // strip Arabic diacritics + tatweel
                .replaceAll("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]", "")
                // unify alef forms and taa marbuta
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ة', 'ه').replace('ى', 'ي')
                // keep only letters/digits/spaces (drops punctuation like "،,-()")
                .replaceAll("[^0-9a-z\\u0621-\\u064A\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // strip the definite article per token ("الاخصائيين" == "اخصائيين")
        List<String> tokens = new ArrayList<>();
        for (String tok : s.split(" ")) {
            if (tok.length() > 2 && tok.startsWith("ال")) {
                tok = tok.substring(2);
            }
            if (!tok.isEmpty()) {
                tokens.add(tok);
            }
        }
        return String.join(" ", tokens);
    }
}
