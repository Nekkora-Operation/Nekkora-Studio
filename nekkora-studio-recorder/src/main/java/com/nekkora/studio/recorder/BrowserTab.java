package com.nekkora.studio.recorder;

/**
 * Aba do Chrome oferecida para gravação. {@code tabRef} é uma referência OPACA da extensão (o id interno do Chrome
 * não sai do navegador) e nunca é exibida. Título já mascarado pela política de privacidade; host sem caminho.
 */
public record BrowserTab(String tabRef, String title, String host, TabAccess access, boolean active, boolean recording) {

    /** Texto para o usuário: título (ou host) — nunca a URL. */
    public String label() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return host != null ? host : access.label();
    }
}
