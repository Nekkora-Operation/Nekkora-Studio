package com.nekkora.studio.recorder;

/**
 * Aba do Chrome oferecida para gravação. {@code tabRef} é uma referência OPACA da extensão (o id interno do Chrome
 * não sai do navegador) e nunca é exibida. Título já mascarado pela política de privacidade; host sem caminho.
 */
public record BrowserTab(String tabRef, String title, String host, TabAccess access, boolean active, boolean recording, String visualCapture,
                         String origin, String pddConsent) {

    /**
     * Perfis de captura visual do PDD informados pela extensão. {@code nekkora-visual-capture-profile/2} (STUDIO-013):
     * LAB e AUTHORIZED_SITE (acesso do Chrome + consentimento PDD da origem); BENCHMARK só vem de extensões anteriores à 0.6.0.
     */
    public static final java.util.Set<String> VISUAL_PROFILES = java.util.Set.of("LAB", "BENCHMARK", "AUTHORIZED_SITE");
    /** Consentimento PDD da origem (STUDIO-013); {@code null} = extensão anterior, sem consentimento. */
    public static final java.util.Set<String> PDD_CONSENTS = java.util.Set.of("NOT_REQUIRED", "NONE", "SESSION", "REMEMBERED");
    private static final java.util.regex.Pattern ORIGIN = java.util.regex.Pattern.compile("https?://[A-Za-z0-9.\\[\\]:-]{1,280}");

    public BrowserTab {
        // valor desconhecido da extensão = sem captura (fail-closed)
        visualCapture = visualCapture != null && VISUAL_PROFILES.contains(visualCapture) ? visualCapture : null;
        // origem só na forma esquema://host[:porta] (nunca caminho, consulta ou credencial); fora disso, descartada
        origin = origin != null && ORIGIN.matcher(origin).matches() ? origin : null;
        pddConsent = pddConsent != null && PDD_CONSENTS.contains(pddConsent) ? pddConsent : null;
    }

    public BrowserTab(String tabRef, String title, String host, TabAccess access, boolean active, boolean recording, String visualCapture) {
        this(tabRef, title, host, access, active, recording, visualCapture, null, null);
    }

    /** O Chrome permite a aba, mas falta o consentimento PDD da origem (STUDIO-013): dá para autorizar pelo Studio. */
    public boolean pddConsentMissing() {
        return access == TabAccess.AUTHORIZED && "NONE".equals(pddConsent) && origin != null;
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
