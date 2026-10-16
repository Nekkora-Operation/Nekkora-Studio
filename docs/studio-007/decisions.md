# STUDIO-007 — Decisões

| Id | Decisão | Motivo |
|---|---|---|
| D-S007-01 | Validação estrutural ≠ execução: nenhum estado "pronto para executar" | não há executor; regras estáticas não verificam a página real |
| D-S007-02 | Motor em módulo próprio `nekkora-studio-validation`, Java puro, só domínio | testável sem Swing/Chrome/Native Host; reutilizável por Agent e Platform |
| D-S007-03 | Regras sobre os metadados existentes (`ActionType`, `TargetRequirement`, `ActionParameters`, `ElementReference`, `ObservationSummary`) | nenhum catálogo paralelo, nenhuma classe duplicada |
| D-S007-04 | Três estados: `INCOMPLETE`, `NOT_FULLY_ASSESSABLE`, `NO_STATIC_BLOCKERS`, com precedência fixa | semântica da missão; "sem bloqueios" não é "pronta" |
| D-S007-05 | Severidade fixa por código; textos são modelos controlados | estabilidade e privacidade |
| D-S007-06 | Variável e credencial = aviso que limita a avaliação | a referência é a configuração correta; a resolução não é demonstrável |
| D-S007-07 | Relatório NÃO persistido no `.nekkora`; formato continua 1.0.0, fixtures intactas | é derivado e recalculável; evita resultado obsoleto e migração prematura |
| D-S007-08 | Validação na EDT, de uma vez, sobre o estado efetivo | consistência garantida (domínio confinado à EDT) e custo de milissegundos (5.000 etapas ≈ 6 ms) |
| D-S007-09 | Atualidade pelo objeto da automação + objeto e geração do estado de persistência (só leitura) | cobre evidências, que não mudam a revisão; na dúvida, desatualizado |
| D-S007-10 | Painel abaixo da área de trabalho, oculto até validar; editor de etapa existente para corrigir | não cria um segundo editor; mantém propriedades à direita |
| D-S007-11 | Observação (STUDIO-005) sempre informação; evidência não resolve bloqueio | configuração e comportamento separados; nunca resultado de negócio |
| D-S007-12 | Localizador validado na captura = informação histórica | disponibilidade atual não é verificada |
| D-S007-13 | Privacidade omitida não é defeito; nada sugere desativá-la | coerência com a CHROME-SECURITY-001 |
| D-S007-14 | Detalhe só de vocabulário fechado (tipos de obstáculo), senão `OTHER` | nenhum texto do produtor no relatório |
| D-S007-15 | Ordem automação → sessões → etapas → regras; id `CÓDIGO:identidade[:detalhe]` | determinismo e identificação estável |
| D-S007-16 | Contrato JSON `nekkora-studio/validation-report` 1.0.0 com schema e fixture congelada | contrato revisável para consumidores futuros |

## Decisões de implementação

| Id | Decisão | Motivo |
|---|---|---|
| I-S007-01 | `AutomationCatalog` intocado; nenhuma mudança no domínio | a validação só lê |
| I-S007-02 | `NekkoraList` expõe cada linha a tecnologias assistivas (renderizador `Accessible`) | as linhas eram só pintadas; melhora todas as listas sem mudar a pintura |
| I-S007-03 | Símbolo de bloqueio "×" (U+00D7) | "✕" (U+2715) não existe na fonte do design system |
| I-S007-04 | Painel compacto: linhas únicas com reticências, filtro em linha própria, altura mínima calculada, divisão 55/45 | coluna central estreita (900 px) e 1366×768 a 150% |
| I-S007-05 | `ELEMENT_MISSING` orienta gravar a etapa | o domínio só aceita localizadores manuais em etapas com elemento capturado |
| I-S007-06 | `PRESS_KEY`: lista fechada de teclas | o Recorder só produz Enter; o resto é conferência estática simples |
