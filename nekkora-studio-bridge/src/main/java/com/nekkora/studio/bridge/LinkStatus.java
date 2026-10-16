package com.nekkora.studio.bridge;

/**
 * Estado da ligação do Studio com o navegador.
 *
 * @param state     estado
 * @param detail    detalhe técnico estável (ex.: OTHER_INSTANCE, HEARTBEAT_LOST, CHROME_DISCONNECTED), ou null
 * @param extension versão/modo da extensão conectada (somente em CONNECTED)
 */
public record LinkStatus(State state, String detail, ExtensionInfo extension) {

    public enum State {
        /** Bridge sendo publicado. */
        STARTING,
        /** Bridge publicado; nenhuma extensão conectada (Chrome fechado ou conexão desligada no painel). */
        WAITING,
        /** Extensão conectada e autenticada. */
        CONNECTED,
        /** O Bridge não pôde ser publicado (outra instância, diretório inseguro, E/S); o Studio segue sem gravação. */
        UNAVAILABLE
    }

    /**
     * Versão, modo de construção e política de privacidade declarada pela extensão conectada. CHROME-SECURITY-001:
     * {@code privacy} ausente = extensão anterior à {@code nekkora-privacy/2} — o Studio revalida tudo o que recebe.
     */
    public record ExtensionInfo(String version, String buildMode, String privacy) {

        public static final String CURRENT_POLICY = "nekkora-privacy/2";

        public ExtensionInfo(String version, String buildMode) {
            this(version, buildMode, null);
        }

        /** A extensão declarou a política vigente nesta conexão. */
        public boolean currentPolicy() {
            return CURRENT_POLICY.equals(privacy);
        }
    }

    public static LinkStatus of(State state, String detail) {
        return new LinkStatus(state, detail, null);
    }

    public boolean connected() {
        return state == State.CONNECTED;
    }
}
