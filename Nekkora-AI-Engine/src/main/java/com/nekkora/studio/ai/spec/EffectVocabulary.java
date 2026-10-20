package com.nekkora.studio.ai.spec;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * STUDIO-014 — classificação DERIVADA do efeito de um clique pelo rótulo de interface do alvo (vocabulário FECHADO,
 * português e inglês). Só serve quando o rótulo é um verbo conhecido; fora da lista não há palpite: a etapa vira pedido
 * de ajuda. Na dúvida entre os dois, vale o efeito externo ("Salvar e sair" grava).
 *
 * <p>É derivação, não prova: o resultado vai para a especificação com a procedência {@code DERIVED_LABEL} e a pessoa
 * pode corrigir. Links (âncoras) navegam: efeito {@code NONE} com procedência {@code DERIVED_KIND}.
 */
public final class EffectVocabulary {
    private EffectVocabulary() {}

    /** Verbos que gravam, enviam ou apagam algo no sistema-alvo. */
    static final Set<String> EXTERNAL = Set.of("gravar", "salvar", "enviar", "submeter", "confirmar", "concluir", "finalizar", "excluir", "apagar",
            "remover", "deletar", "cadastrar", "incluir", "registrar", "aprovar", "reprovar", "recusar", "pagar", "transferir", "emitir", "assinar",
            "protocolar", "agendar", "solicitar", "atualizar", "alterar", "efetivar", "autorizar", "cancelar", "save", "send", "submit", "delete",
            "remove", "confirm", "approve", "reject", "pay", "create", "add", "register", "update", "cancel", "post", "publish");

    /** Verbos que só consultam, navegam ou entram/saem (sem efeito de negócio). */
    static final Set<String> NONE = Set.of("consultar", "pesquisar", "buscar", "procurar", "filtrar", "localizar", "entrar", "acessar", "logar",
            "login", "sair", "logout", "voltar", "fechar", "limpar", "abrir", "ver", "visualizar", "detalhar", "exibir", "mostrar", "listar",
            "search", "find", "filter", "signin", "logon", "back", "close", "clear", "open", "view", "show", "list", "menu", "inicio", "home");

    public enum Effect { NONE, EXTERNAL }

    /** Efeito derivado do rótulo, ou vazio quando o rótulo não está no vocabulário (pedir ajuda). */
    public static Optional<Effect> fromLabel(String label) {
        if (label == null || label.isBlank()) return Optional.empty();
        String norm = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        norm = norm.replace("sign in", "signin").replace("log in", "login").replace("log out", "logout").replace("sign out", "logout");
        boolean none = false;
        for (String w : norm.split("[^a-z]+")) {
            if (w.isEmpty()) continue;
            if (EXTERNAL.contains(w)) return Optional.of(Effect.EXTERNAL);
            if (NONE.contains(w)) none = true;
        }
        return none ? Optional.of(Effect.NONE) : Optional.empty();
    }
}
