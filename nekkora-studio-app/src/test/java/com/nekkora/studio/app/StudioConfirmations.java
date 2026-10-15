package com.nekkora.studio.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nekkora.studio.app.ui.StudioPrompts;

/** Conferência do texto das confirmações pedidas ao usuário. */
final class StudioConfirmations {
    private StudioConfirmations() {}

    static void assertText(StudioPrompts.Confirmation c, String... fragments) {
        for (String f : fragments) assertTrue(c.message().contains(f), "confirmação sem \"" + f + "\": " + c.message());
    }
}
