package com.nekkora.studio.bridge;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Ligação do Studio com o navegador, vista pelo Recorder. Toda chamada é assíncrona: nenhuma espera bloqueia quem
 * chama (em especial a thread de interface). Os ouvintes são chamados em threads do Bridge — quem atualiza Swing
 * precisa repassar para a EDT.
 */
public interface BrowserLink {

    /** Ouvinte de estado da ligação e de eventos do navegador. */
    interface Listener {
        void statusChanged(LinkStatus status);

        void event(BridgeEvent event);
    }

    LinkStatus status();

    void addListener(Listener listener);

    void removeListener(Listener listener);

    /**
     * Envia um comando da lista explícita do contrato. O futuro completa com o payload da resposta confirmada, ou
     * excepcionalmente com {@link BridgeCommandException} (recusa, tempo esgotado, canal perdido).
     */
    CompletableFuture<Map<String, Object>> send(String type, Map<String, Object> payload, Duration timeout);

    /** Ligação inexistente (Bridge desligado): estado UNAVAILABLE e todo comando recusado — nunca uma conexão fictícia. */
    static BrowserLink offline(String detail) {
        LinkStatus status = LinkStatus.of(LinkStatus.State.UNAVAILABLE, detail);
        return new BrowserLink() {
            @Override
            public LinkStatus status() {
                return status;
            }

            @Override
            public void addListener(Listener listener) {
            }

            @Override
            public void removeListener(Listener listener) {
            }

            @Override
            public CompletableFuture<Map<String, Object>> send(String type, Map<String, Object> payload, Duration timeout) {
                return CompletableFuture.failedFuture(new BridgeCommandException(BridgeError.EXTENSION_UNAVAILABLE,
                        "O navegador não está conectado ao Studio.", detail, false));
            }
        };
    }
}
