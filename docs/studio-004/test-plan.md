# STUDIO-004 — Plano de testes e matriz S004

Legenda:

- **D**: domínio (`AutomationEditingTest`);
- **A**: aplicação pela interface (`EditorStudioTest`, salvo indicação);
- **R**: `RecorderStepsStudioTest` (Recorder simulado no Studio real);
- **P**: `EditorPerformanceTest`;
- **H**: homologação real no Windows (`Studio004Homologation`), com o cenário indicado.

| Caso | Cenário | Evidência | Situação |
|---|---|---|---|
| S004-001 | Editar nome da automação | D `automacao`; A `automacao` (F2, aba renomeada); H 1 | ✅ |
| S004-002 | Editar descrição da automação | D `automacao`; A `automacao`; H 1 | ✅ |
| S004-003 | Recusar nome duplicado | D `automacao`; A `automacao` (mensagem no campo, nada parcial) | ✅ |
| S004-004 | Editar sessão lógica | D `sessao`; A `sessao` (F2); | ✅ |
| S004-005 | Editar observações da sessão | D `sessao`; A `sessao` | ✅ |
| S004-006 | Reorganizar sessões | D `reorganizarSessoes`; A `reorganizarSessoes`, `subirEDescerSessao`; H 2 | ✅ |
| S004-007 | Reorganizar primeira sessão | D `reorganizarSessoes` (primeira → última); A `reorganizarSessoes` (limite) | ✅ |
| S004-008 | Reorganizar última sessão | D `reorganizarSessoes` (última → primeira); A `reorganizarSessoes` | ✅ |
| S004-009 | Excluir sessão vazia | D `excluirSessoes`; A `excluirSessao` | ✅ |
| S004-010 | Excluir sessão com etapas | D `excluirSessoes`; A `excluirSessao` (confirmação com a contagem) | ✅ |
| S004-011 | Cancelar exclusão | A `excluirSessao` | ✅ |
| S004-012 | Editar nome de etapa | D `editarEtapa`; A `editarEtapa`; H 9 | ✅ |
| S004-013 | Editar descrição | D `editarEtapa`; A `editarEtapa` | ✅ |
| S004-014 | Alterar tipo de ação | D `editarEtapa`; A `editarEtapa` | ✅ |
| S004-015 | Rejeitar parâmetros incompatíveis | D `editarEtapa`; A `editarEtapa` (decisão explícita; recusar mantém tudo) | ✅ |
| S004-016 | Reorganizar etapas na mesma sessão | D `reorganizarNaSessao`; A `reorganizarPeloTeclado` (Alt+↑/↓); H 6/7 | ✅ |
| S004-017 | Mover etapa entre sessões | D `moverEntreSessoes`; A `moverEntreSessoes` (diálogo); H 3 | ✅ |
| S004-018 | Preservar StepId após movimentação | D `reorganizarNaSessao`, `moverEntreSessoes`; A idem; H 3 | ✅ |
| S004-019 | Atualizar numeração global | D `reorganizarSessoes`, `moverEntreSessoes`; A `moverEntreSessoes`, `reorganizarSessoes`; H 2/3 | ✅ |
| S004-020 | Duplicar etapa | D `duplicar`; A `duplicar` (Ctrl+D) | ✅ |
| S004-021 | Duplicação não inventa gravação | D `duplicar` (MANUAL, sem chaves, sem observação, cancelamento não a leva); A `duplicar` | ✅ |
| S004-022 | Excluir etapa | D `excluirEtapa`; A `excluirEtapa` (Delete + confirmação); H 7 | ✅ |
| S004-023 | Preservar outras etapas | D `excluirEtapa`; A `excluirEtapa` | ✅ |
| S004-024 | Configurar valor literal permitido | D `parametros`; A `parametros` | ✅ |
| S004-025 | Configurar VariableReference | D `parametros`; A `parametros`; H 4 | ✅ |
| S004-026 | Configurar CredentialReference | D `parametros`; A `parametros`; H 4 | ✅ |
| S004-027 | Rejeitar literal sensível | D `parametros`; A `parametros` (opção indisponível e recusa do domínio); H 4 | ✅ |
| S004-028 | Editar OptionChoice | D `opcao`; A `opcao` | ✅ |
| S004-029 | Visualizar localizadores | A `localizadores`; H 5 | ✅ |
| S004-030 | Selecionar localizador validado | D `localizadores`; A `localizadores`; H 5 | ✅ |
| S004-031 | Localizador manual permanece não verificado | D `localizadorManual`; A `localizadores` | ✅ |
| S004-032 | Preservar ElementReference original | D `localizadores`; A `localizadores`; H 5 | ✅ |
| S004-033 | Preservar RecordingObservation | D `procedencia`; A `procedenciaERestauracao`; H 9 | ✅ |
| S004-034 | Identificar alteração manual | D `procedencia`; A `procedenciaERestauracao`; H 9 | ✅ |
| S004-035 | Restaurar valor original | D `restaurar`, `restaurarSenhaNaoTrazSegredo`; A `procedenciaERestauracao` | ✅ |
| S004-036 | Adicionar comentário | D `comentarios`; A `comentarios`; H 6 | ✅ |
| S004-037 | Editar comentário | D `comentarios`; A `comentarios` (data preservada); H 6 | ✅ |
| S004-038 | Excluir comentário | D `comentarios`; A `comentarios` | ✅ |
| S004-039 | Preservar comentários ao mover etapa | D `moverEntreSessoes`; A `comentarios`, `moverEntreSessoes`; H 6 | ✅ |
| S004-040 | Undo de edição | A `desfazerRefazer`; H 7 | ✅ |
| S004-041 | Undo de exclusão | A `desfazerRefazer` (sessão excluída volta, mesma instância); H 7 | ✅ |
| S004-042 | Undo de movimentação | A `desfazerRefazer`; H 7 | ✅ |
| S004-043 | Redo | A `refazer`; H 7 | ✅ |
| S004-044 | Invalidar Redo após nova edição | A `refazer` | ✅ |
| S004-045 | Restaurar identidade no Undo | D `fotografia`; A `desfazerRefazer`, `refazer` (sem identidades novas); H 7 | ✅ |
| S004-046 | Impedir alteração parcial | D `editarEtapa`, `sessao`, `moverEntreSessoes`; A `semAlteracaoParcial` | ✅ |
| S004-047 | Bloquear edição durante gravação | R `s004BloqueioDuranteGravacao` (caso de uso, tela, atalhos); H 8 | ✅ |
| S004-048 | Preservar Recorder e StepIntake | R `s004BloqueioDuranteGravacao` + testes S003 da classe; `RecorderStepsTest`; A `conflitoComORecorder`; H 8 | ✅ |
| S004-049 | Atualizar somente sessões afetadas | A `incremental` (origem e destino relidos; demais não); P `sessionViewsReread = 3` | ✅ |
| S004-050 | Manter seleção consistente | A `reorganizarPeloTeclado`, `moverEntreSessoes`, `excluirEtapa`, `excluirSessao`, `desfazerRefazer`, `refazer` | ✅ |
| S004-051 | Validar atalhos de teclado | A `atalhos` (Ctrl+Z no campo, Delete só na lista), `reorganizarPeloTeclado`, `duplicar`, `excluirEtapa`, `desfazerRefazer`; `StudioThemeAndKeyboardTest`; H 1/4/6/7 | ✅ |
| S004-052 | Validar os quatro temas | A `temas` (5 abas × 4 temas); `DesignLintTest`; H (4 capturas) | ✅ |
| S004-053 | Validar privacidade | A `privacidade`; `StudioPrivacyTest`; H (sem IDs, sem usuário/senha) | ✅ |
| S004-054 | Preservar cancelamento de gravações | R `s004CancelamentoPreservado`, `s003Cancelamento`; D `duplicar` | ✅ |
| S004-055 | Preservar contratos anteriores | `BridgeContractFixturesTest`, `RealNativeHostBridgeTest` (inalterados); `git diff` dos contratos vazio; extensão não alterada | ✅ |
| S004-056 | Nenhum recurso futuro simulado | A `nadaSimulado`; `StudioScopeTest` | ✅ |

## Defeitos reais encontrados (com regressão)

| Defeito | Encontrado por | Regressão |
|---|---|---|
| Desfazer em sequência tratava a próxima entrada como conflito (restaurar conta revisão) | A `desfazerRefazer` | A `desfazerRefazer` |
| Reencadear o histórico sem conferir vizinhança escondia uma alteração externa entre duas entradas | A `desfazerRefazer` (Recorder entre entradas) | A `desfazerRefazer` (conflito no ponto certo) |
| Histórico ocupava ~14,7 MB por 100 operações com 1.000 etapas | P | P (memória registrada) |
| Subir e descer a mesma sessão deixava os cartões trocados na tela | H cenário 2 | A `subirEDescerSessao` |
| Delete/Alt+↑/↓ ignorados em silêncio durante a gravação | H cenário 8 | R `s004BloqueioDuranteGravacao` |
| Textos longos cortados em "Origem e alterações"; campo de expressão estreito | revisão das capturas da H | capturas finais |

## Suítes

- Studio: `./mvnw clean verify` (Windows) e `tools/golden-linux.sh verify` (Linux, Temurin 21.0.12.1+1).
- Extensão: não alterada. Os testes cruzados com o host real (`RealNativeHostBridgeTest`) rodam no Studio.

## Revisão técnica do PR #5

| Achado | Testes (falharam antes da correção, exceto os de controle) | Situação |
|---|---|---|
| P1 — `OptionChoice` com dado sensível | D `opcaoSensivelRecusada` (e-mail, CPF, JWT, trecho "eyJ", Bearer, UUID, ID de contrato, hash, controle, tamanho); A `opcaoSensivelPeloEditor` (etapa e histórico intactos); Recorder `revOpcaoDoRecorderContinuaAceita` (controle) | ✅ |
| P1 — tipo × parâmetros anteriores | A `trocaDeTipoComParametroAnterior` (FILL + variável → SELECT_OPTION + São Paulo/SP); `trocaDeTipoSemParametroAntigo` (controle) | ✅ |
| P2 — comentário como intervenção | D `comentarioEhIntervencaoManual`; A `comentarioComoIntervencaoNoPainel` | ✅ |
| P2 — duplicação sem chaves do evento | D `duplicarSemChavesDoEventoNoElemento`; Recorder `revDuplicarEtapaProduzidaPeloRecorderNaoCarregaChavesDoEvento` | ✅ |
