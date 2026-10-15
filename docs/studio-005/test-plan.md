# STUDIO-005 — Plano de testes (matriz oficial S005-001..064)

Legenda dos locais:

| Sigla | Local |
|---|---|
| **N-R** | `chrome-extension/test/feedback-rules.test.js` |
| **N-E** | `chrome-extension/test/studio-evidence.test.js` (todo payload validado contra o schema) |
| **H** | `EvidencePrivacyTest` (Native Host) |
| **IT** | `Studio005EvidenceIT` (Chrome for Testing real, cadeia completa) |
| **D** | `EvidenceDomainTest` |
| **R** | `RecorderEvidenceTest` |
| **A** | `RecorderEvidenceStudioTest` |
| **HR** | `Studio005Homologation` (Studio real, Robot, Windows) |

Os nomes dos testes começam pelo número da matriz (ex.: `test('S005-021 — …')`, `s005021…`, `@DisplayName("S005-021 …")`).

| Id | Caso | Onde |
|---|---|---|
| S005-001 | Baseline Studio e extensão | relatórios §1 (Studio 307 Win/Linux; extensão 232 Java, 96 Node, 211 navegador) |
| S005-002 | Extensão funciona sem Studio | suíte do navegador existente (224/224) e Node |
| S005-003 | Negociação `evidence/1` | N-E, R `s005003…`, HR |
| S005-004 | Studio antigo não recebe eventos novos | N-E (dois testes), IT `s005Compat` |
| S005-005 | Extensão antiga mantém gravação funcional | R `s005005…` (STUDIO-003/004) e `s005005b…` (STUDIO-002) |
| S005-006 | Envio de formulário | N-E, IT cenário 1, HR |
| S005-007 | Navegação | N-E (`DOCUMENT_CHANGED`), IT cenário 6, HR cenário 6 |
| S005-008 | Navegação SPA | N-E, IT cenário 6, HR cenário 6 |
| S005-009 | Contexto anterior à ação | IT cenário 1 (linha de base), HR S005-020, N-R (estado "de") |
| S005-010 | Contexto posterior à ação | N-E, IT cenário 1, A |
| S005-011 | Aviso com `role=alert` | N-R, IT cenário 1 |
| S005-012 | Mensagem dinâmica em região existente | IT cenário 1 (mesma região, texto novo) |
| S005-013 | Região com `aria-live` | N-R, IT cenário 5 |
| S005-014 | Mensagem de status | N-R, IT cenário 2, HR cenário 2 |
| S005-015 | Indicador de validação | IT cenário 4, HR cenário 4 |
| S005-016 | Indicador de carregamento | IT cenário 5, HR cenário 5 |
| S005-017 | Fim de carregamento | IT cenário 5, HR cenário 5 |
| S005-018 | Mudança relevante de elemento | N-R, IT cenário 6, HR cenário 6 |
| S005-019 | Mensagem que não mudou não duplica | N-E, IT cenário 1 |
| S005-020 | Mensagem anterior à ação não é evidência | IT cenário 1, HR |
| S005-021 | Ambígua não é atribuída | N-R, N-E (aviso e navegação), D, R, IT cenário 3, HR cenário 3 |
| S005-022 | Evidência tardia atualiza a etapa correta | N-E (envio tardio), IT cenário 2, A (histórico) |
| S005-023 | Evidência chega antes da etapa | N-E, R `s005023…`, A (login) |
| S005-024 | Evidência duplicada | N-E, R `s005024E026…`, D, IT reconexão |
| S005-025 | Retransmissão após reconexão | N-E, R `s005025…`, IT reconexão, HR cenário 10 |
| S005-026 | Sequência de evidências | N-E, R, D `s005026…` |
| S005-027 | Identidade da etapa preservada | D `s005027E033…`, N-E, HR |
| S005-028 | Sessão lógica correta | D `s005028…`, HR cenários 4 e 7 (evidências nas etapas das sessões Consulta e Quadros) |
| S005-029 | Etapa movida mantém evidências | D, A, HR |
| S005-030 | Etapa duplicada não inventa evidências | D, A, HR |
| S005-031 | Undo não apaga evidências | D, A, HR (Ctrl+Z) |
| S005-032 | Redo não reaplica evidências | D, A, HR (Ctrl+Y) |
| S005-033 | Baseline capturado intacto | D, HR |
| S005-034 | Resultado de negócio não validado | D, IT (sem `businessOutcome`/`SUCCESS`/`FAILURE`), A, HR |
| S005-035 | CAPTURE_ONLY sem consequência | N-R, N-E, D, IT `s005CaptureOnly` |
| S005-036 | CAPTURE_AND_INTERACT observa efeitos | N-R, N-E, IT (todos os cenários) |
| S005-037 | Pausa encerra a observação | N-E, R, IT pausa, HR cenário 10 |
| S005-038 | Retomada sem duplicação | N-E, IT pausa, HR cenário 10 |
| S005-039 | Finalização com evidências pendentes | N-E, HR (resumo) |
| S005-040 | Cancelamento remove evidências da gravação | N-E, D, R, A, HR |
| S005-041 | Degradação suspende sensores | N-E |
| S005-042 | Iframe autorizado | N-R, N-E, IT cenário 7, HR cenário 7 |
| S005-043 | Iframe sem permissão | IT cenário 7, HR cenário 7 |
| S005-044 | Shadow DOM | N-E, IT cenário 7 (componente `caixa-confirmacao`) |
| S005-045 | Mudança de documento durante a observação | N-E, IT cenário 6, HR cenário 6 |
| S005-046 | Revogação de permissão | N-E (`PERMISSION_REVOKED`), além do IT de revogação da STUDIO-003 (sensores) |
| S005-047 | Dados pessoais em mensagem | N-R, N-E, H, D, R, IT cenário 8, HR cenário 8 |
| S005-048 | Senha em mensagem | N-R, H, IT cenário 8 (código digitado), HR cenário 8 |
| S005-049 | JWT e Bearer em mensagem | N-R, H, D, IT cenário 8 |
| S005-050 | IDs técnicos fora da UI | A, HR |
| S005-051 | HTML bruto não transmitido | fixture inválida, N-E, R, IT cenário 8 (marcação) |
| S005-052 | Nenhum PageMap por evento | fixture inválida, N-E, R |
| S005-053 | Limite de evidências | N-E, D |
| S005-054 | Backpressure e perda declarada | N-E |
| S005-055 | Native Host preserva prioridade | parcial: `HostConcurrencyTest` (existente), ack/sync fora da fila de comandos no service worker, IT reconexão. Não há teste novo específico de prioridade entre evidências e comandos |
| S005-056 | EDT não bloqueada | A (verificação de `RepaintManager`; concorrência) |
| S005-057 | Floating Recorder atualiza contadores | A, HR |
| S005-058 | Painel de evidências | A, HR |
| S005-059 | Acessibilidade | A `s005AcessibilidadeETemas` |
| S005-060 | Quatro temas | A `s005AcessibilidadeETemas` |
| S005-061 | Golden screenshots preservados | suíte do Studio (golden tests existentes, sem ajuste) |
| S005-062 | Regressão STUDIO-001 a 004 | Studio 337/337 Windows e Linux; HR (bloqueio S004) |
| S005-063 | Regressão da Chrome Extension | Java 235, Node 129, navegador 224 |
| S005-064 | Nenhuma execução ou validação inventada | D, N-E, IT |

## Números

| Suíte | Antes | Depois |
|---|---|---|
| Studio (Windows) | 307 | **337** (+12 domínio, +12 Recorder, +6 aplicação) |
| Studio (Linux, Docker Temurin 21) | 307 | **337** |
| Extensão Java | 232 | **235** |
| Extensão Node | 96 | **129** |
| Extensão navegador (6 grupos) | 211 | **224** |
| Homologação real Studio (Robot) | — | **50/0** |
