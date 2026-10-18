package com.nekkora.studio.recorder;

/**
 * Aba do Chrome oferecida para gravação. {@code tabRef} é uma referência OPACA da extensão (o id interno do Chrome
 * não sai do navegador) e nunca é exibida. Título já mascarado pela política de privacidade; host sem caminho.
 */
public record BrowserTab(String tabRef, String title, String host, TabAccess access, boolean active, boolean recording, String visualCapture) {

    /** Perfis de captura visual do PDD informados pela extensão ({@code nekkora-visual-capture-profile/1}). */
    public static final java.util.Set<String> VISUAL_PROFILES = java.util.Set.of("LAB", "BENCHMARK");

    public BrowserTab {
        // valor desconhecido da extensão = sem captura (fail-closed)
        visualCapture = visualCapture != null && VISUAL_PROFILES.contains(visualCapture) ? visualCapture : null;
    }

    /** Extensão anterior à NEKKORA-E2E-001 (sem o campo): só LAB captura. */
    public BrowserTab(String tabRef, String title, String host, TabAccess access, boolean active, boolean recording) {
        this(tabRef, title, host, access, active, recording, access == TabAccess.LAB ? "LAB" : null);
    }

    /** Texto para o usuário: título (ou host) — nunca a URL. */
    public String label() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return host != null ? host : access.label();
    }
}
