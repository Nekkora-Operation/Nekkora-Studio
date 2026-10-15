package com.nekkora.studio.recorder;

/** Acesso da extensão à página de uma aba (mesma classificação da política central da extensão). */
public enum TabAccess {
    LAB("Laboratório local", true),
    AUTHORIZED("Autorizada", true),
    PERMISSION_REQUIRED("Não autorizada", false),
    BROWSER_INTERNAL("Página interna do navegador", false),
    BROWSER_RESTRICTED("Bloqueada pelo Chrome", false),
    UNSUPPORTED_SCHEME("Endereço não suportado", false),
    INVALID_URL("Endereço inválido", false),
    URL_UNAVAILABLE("Endereço indisponível", false);

    private final String label;
    private final boolean allowed;

    TabAccess(String label, boolean allowed) {
        this.label = label;
        this.allowed = allowed;
    }

    public String label() {
        return label;
    }

    /** A gravação pode ser iniciada nesta aba. */
    public boolean allowed() {
        return allowed;
    }

    static TabAccess of(String s) {
        for (TabAccess a : values()) {
            if (a.name().equals(s)) {
                return a;
            }
        }
        return URL_UNAVAILABLE;
    }
}
