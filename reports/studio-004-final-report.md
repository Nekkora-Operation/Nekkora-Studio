# NEKKORA-STUDIO-004 — Relatório final

**Editor avançado, organização de processos e revisão de etapas gravadas.** Branch `feat/nekkora-studio-004`. O Pull
Request fica aberto para revisão; **nenhum merge foi feito**. A extensão não foi alterada.

## 1. Baseline inicial

| Verificação | Resultado |
|---|---|
| PRs da STUDIO-003 | Studio #4 (`c5660b3`) e extensão #5 (`498e4ab`) integrados por merge commit |
| `./mvnw clean verify` (Windows) | **251** testes, 0 falhas |
| `tools/golden-linux.sh verify` (Temurin 21.0.12.1+1, extensão `8b251c5` ao lado) | **251**, 0 falhas, 0 ignorados |
| Extensão (referência) | Java 232 · Node 96 · Chrome 211 (STUDIO-003) |

## 2. Commit de partida

`84c317f` (main do Nekkora-Studio, merge do PR #4). Na extensão, `8b251c5` (main, merge do PR #5).

## 3. Arquitetura implementada

`docs/studio-004/architecture.md`. O editor amplia a aplicação existente, sem aplicação paralela:

```
interface → StudioController → WorkspaceService (bloqueio por gravação · histórico) → Automation (regras)
          → StudioState → StudioEvent (sessões afetadas) → tela
```

Nenhum componente Swing altera as coleções. As decisões D-S004-01 a 12, e as complementares 13 a 19, estão em
`docs/studio-004/decisions.md`.

## 4. Alterações no domínio

- **`Automation`**: novas operações, todas validando antes de alterar, contando uma revisão por operação efetiva e
  nenhuma revisão quando não há efeito.
  - estrutura: `editSession`, `moveSession`, `removeSession`;
  - etapas: `moveStep` (na sessão e entre sessões), `duplicateStep`, `removeStep`;
  - edição: `editStep` (edição completa), `setStepParameters`, `choosePreferredLocator`, `addManualLocator`,
    `removeManualLocator`;
  - comentários: `addComment`, `editComment`, `removeComment`;
  - procedência: `restoreOriginal`;
  - histórico: `snapshot()` e `restore()`.
- **`AutomationStep`**: o estado efetivo fica separado da observação original. `CapturedBaseline` é imutável e só existe
  para `RECORDED`. `capturedElement()` também é imutável. `ManualRevision` e `editedAt` são calculados.
- **Tipos novos**: `StepEdit`, `StepField`, `CapturedBaseline`, `ManualRevision`, `element.ManualLocator` (sempre
  `NOT_VERIFIED`), `Automation.Snapshot`.
- **`StepComment`** ganhou `editedAt` e a validação `requireText`. **`ElementReference`** ganhou `withPreferredLocator`
  (a referência efetiva).
- **Sem efeito, sem revisão**: `updateDetails`, `renameSession` e `describeSession` também deixaram de contar revisão
  quando nada muda.
- **`ValidationProblem`**: problemas novos (posição, localizador, comentário, gravação em andamento, histórico).

## 5. Operações de edição

| Operação | Interface |
|---|---|
| Automação (nome, descrição) | F2 / Editar propriedades |
| Sessão (nome, descrição, observações) | F2 / lápis / Editar |
| Reorganizar sessões | menu Mais ações / Para cima, Para baixo |
| Excluir sessão | menu / Excluir (confirmação com a contagem de etapas) |
| Editar etapa (5 abas) | F2 / Enter / Editar etapa |
| Reorganizar etapa | Alt+↑/↓ / menu / painel |
| Mover para outra sessão | Mover para… (diálogo) |
| Duplicar | Ctrl+D / menu / painel |
| Excluir etapa | Delete / menu / painel (confirmação) |
| Desfazer / refazer | Ctrl+Z / Ctrl+Y / barra |

Detalhes em `automation-editor.md`, `session-editor.md`, `step-editor.md` e `step-ordering.md`.

## 6. Regras de movimentação

- `moveStep(stepId, destino, índice)`: o índice final (0..n) conta sem a própria etapa. Destino e posição são
  validados antes; só sessões da mesma automação.
- Identidade, procedência, referência, parâmetros e comentários não mudam. Há uma revisão por movimentação.
- A seleção acompanha a etapa no destino. A tela relê só as sessões de origem e destino.
- Mudar a sessão no editor leva a etapa para o final dela.
- `moveSession(sessionId, índice)`: renumeração imediata; as mesmas visões, reordenadas.

## 7. Regras de exclusão

- **Sessão**: confirmação explícita com o nome e a contagem de etapas. A remoção é atômica, sem etapas órfãs. A seleção
  vai para a sessão que ocupou o lugar, a anterior ou a automação. A automação pode ficar sem sessões.
- **Etapa**: confirmação. A seleção vai para a próxima, a anterior ou a própria sessão.
- **Desfazer** devolve as mesmas identidades.

## 8. Preservação de procedência

`docs/studio-004/step-provenance.md`.

- A etapa gravada e editada continua `RECORDED` e fica com "Alterada manualmente: Sim", com os campos listados.
- A duplicada é `MANUAL` "duplicada de outra etapa" (`derivedFrom` interno), sem chaves de gravação ou de evento e sem
  observação.
- `RecordingObservation` nunca muda, e o resultado de negócio continua **Não validado**.
- Original × atual aparecem no painel e na aba "Origem e alterações", com "Restaurar original" por campo; a restauração
  é validada quanto à compatibilidade.

## 9. Edição de parâmetros

`docs/studio-004/parameters.md`. Usa o mesmo modelo (`ValueSource`, `OptionChoice`).

- **Literal**: só texto não sensível digitado. É indisponível e recusado em elemento sensível.
- **Variável**: `REFERENCE_NAME`.
- **Credencial**: só o apelido; a interface explica que não há cofre.
- **Opção**: rótulo e valor técnico distintos, numa etapa só.
- Parâmetros incompatíveis com o tipo exigem uma decisão explícita.

## 10. Edição de localizadores

`docs/studio-004/element-reference-editor.md`.

- O preferencial só pode ser um candidato validado na captura (`LocatorCandidate.validated()`).
- O localizador manual é sempre "Não verificado": sem `matchCount`/`matchesTarget`, nunca preferencial e fora da lista
  capturada.
- O `ElementReference` capturado fica intacto (mesmo objeto). Nada é recalculado e nenhum ID técnico é alterado.

## 11. Comentários

`docs/studio-004/comments.md`.

- Adicionar, editar e excluir.
- A data de criação é preservada; `editedAt` registra a edição.
- Até 1000 caracteres, com quebras de linha; caracteres de controle e aparência de dado sensível são recusados.
- Sem autor (não há login).
- O comentário acompanha a etapa ao mover; duplicar não o copia.

## 12. Desfazer / refazer

`docs/studio-004/undo-redo.md`.

- `EditHistory`: 100 operações por automação (configurável pelo construtor), só em memória.
- Cada entrada tem as fotografias de antes e depois do agregado, que preservam as identidades, e a seleção de antes e
  de depois.
- Nova edição invalida o refazer. Operações recusadas ou sem efeito não entram.
- As etapas do Recorder ficam fora do histórico.
- Uma alteração externa entre duas entradas vira **conflito explícito**: nada é aplicado e o histórico é descartado.

## 13. Proteção contra concorrência

- O `RecordingGuard` fica no caso de uso e lê o estado vivo do Recorder (`RecorderPresenter.locks`).
- A automação fica bloqueada durante o início, a gravação, a pausa, a interrupção, o encerramento, a reconciliação e as
  entregas pendentes.
- Ficam recusados: editar, reorganizar, mover, duplicar, excluir, comentar, parâmetros, nova etapa manual e desfazer.
- "Nova sessão" continua permitida para o Floating Recorder (D-S004-17).
- Na tela, aparecem o banner "Gravação em andamento" e as ações do painel indisponíveis, com o motivo. Atalhos e menus
  chegam ao caso de uso, que recusa e explica.
- Navegar, consultar e editar **outras** automações continuam livres.
- Ao finalizar ou cancelar, a edição é liberada.

## 14. Testes de domínio

`nekkora-studio-domain`: **52 → 71**, com 19 testes novos em `AutomationEditingTest` (ver o plano para o mapeamento
S004).

## 15. Testes de aplicação

`nekkora-studio-app`: **44 → 71** (mais o `Scale150Test` em JVM própria).

- `EditorStudioTest` (24): cada caso passa pela interface (formulários reais, F2, Delete, Alt+↑/↓, Ctrl+D, Ctrl+Z/Y,
  menus e painel).
- `RecorderStepsStudioTest`: +2 (bloqueio durante a gravação e cancelamento preservado).
- `EditorPerformanceTest` (1).

## 16. Testes de interface

- `nekkora-swing-ui`: **45 → 46** (desfazer a digitação no próprio campo). Os 4 golden screenshots **não foram
  regenerados nem alterados**.
- `nekkora-studio-ui`: 14 (inalterado; o `StudioUiTest` desenha o editor novo).
- `DesignLintTest` e `StudioScopeTest` continuam aprovados.

## 17. Regressões

| Suíte | Antes | Depois |
|---|---|---|
| Windows `./mvnw clean verify` | 251 | **307**, 0 falhas (298 na entrega + 9 da revisão técnica) |
| Linux `tools/golden-linux.sh verify` (extensão `8b251c5` ao lado) | 251 | **307**, 0 falhas, **0 ignorados** |
| Testes cruzados com o Native Host real (`RealNativeHostBridgeTest`, no Studio) | 3 | 3 (Windows e Linux) |
| Extensão (main `8b251c5`, não alterada) | 232 / 96 / 211 | **Java 232/232 · Node 96/96 · navegador 211/211**, 0 falhas |

Navegador da extensão: a primeira execução completa (`-Suite all`) foi interrompida pelo sistema por falta de memória
depois de 199 testes sem falhas. Na revisão técnica, a suíte de navegador foi repetida com **os mesmos casos e
critérios** (todas as classes `*IT` da suíte padrão, sem `*LiveWebIT`/`*ManualIT`), em **6 grupos isolados**
executados um de cada vez (`run-tests.ps1 -Suite integration -Test …`). Resultado: **28 classes, 211 testes, 0 falhas**.
Java (232) e Node (96) vieram da execução completa.

Testes da STUDIO-001/002/003 com premissa ajustada: **nenhum foi alterado**. O `StepDetailsForm` manteve a API
(`stepId()`, `basics()`, `overviewRows()`), e F2 continua editando a sessão selecionada, agora com observações.

## 18. Evidências reais

Homologação real no Windows (`Studio004Homologation`):

- Studio de verdade, Chrome for Testing 155, Native Host instalado pelo `install-host.ps1`;
- **mouse e teclado do sistema operacional** (Robot);
- primeiro uma gravação real (STUDIO-003) produz a automação "Consulta de clientes"; depois os cenários 1 a 9 a editam.

**Resultado: 36 verificações, 0 falhas** (`reports/studio-004/evidence/S004-homologacao-log.txt`).

| Cenário | Verificado | Captura |
|---|---|---|
| 1 Editar automação | nome e descrição; mesmas sessões e etapas (identidades); aba renomeada | `S004-01-*` |
| 2 Reorganizar sessões | Consulta antes de Autenticação; numeração 01–07 recalculada; ordem original restaurada | `S004-02-*` |
| 3 Mover Clientes | para o início da Consulta; mesma entidade; RECORDED; numeração contínua | `S004-03-*` |
| 4 Parâmetros | Usuário → Variável `cliente.usuario`; Senha → Credencial `sistema.senha` (literal indisponível); nada secreto guardado | `S004-04-*` |
| 5 Localizadores | 5 candidatos; não validados indisponíveis; outro validado escolhido; capturado intacto | `S004-05-localizadores.png` |
| 6 Comentários | criado, editado (data preservada) e mantido ao mover com Alt+↑ | `S004-06-comentario.png` |
| 7 Desfazer/refazer | renomear, mover (Alt+↓), excluir (Delete + confirmação); Ctrl+Z ×3, Ctrl+Y ×3, Ctrl+Z; nada perdido ou duplicado | `S004-07-*` |
| 8 Proteção | gravação real em andamento: Delete e Alt+↓ recusados com explicação, banner visível, o Recorder continua gravando; ao finalizar, a edição volta | `S004-08-bloqueio-durante-gravacao.png` |
| 9 Procedência | "Confirmar autenticação" continua RECORDED, alterada manualmente, com original × atual; resultado não validado | `S004-09-*` |
| Privacidade e temas | sem identificadores internos; usuário e senha digitados ausentes; 4 temas | `S004-10-tema-*` |

As capturas foram recortadas às janelas do Studio e do Chrome. Os dados são fictícios (laboratório).

## 19. Medições de desempenho

`EditorPerformanceTest`, com volumes **sintéticos** de teste (`S004-performance.json`, Windows 11, Java 21):

| Volume | Abrir | Reorganizar etapa | Mover entre sessões | Mover sessão | Desfazer | Refazer | Abrir editor | Memória do histórico (100 operações) |
|---|---|---|---|---|---|---|---|---|
| 10 sessões × 10 (100) | 94 ms | 21 ms¹ | 7 ms | 11 ms | 8 ms | 7 ms | 32 ms¹ | ~0,4 MB |
| 50 sessões × 10 (500) | 46 ms | 4 ms | 3 ms | 7 ms | 7 ms | 12 ms | 11 ms | ~1,9 MB |
| 10 sessões × 100 (1.000) | 26 ms | 4 ms | 4 ms | 5 ms | 11 ms | 10 ms | 13 ms | ~1,7 MB |
| 50 sessões × 20 (1.000) | 35 ms | 3 ms | 2 ms | 6 ms | 6 ms | 4 ms | 7 ms | ~2,7 MB |

¹ A primeira medição de cada operação inclui o aquecimento da JVM.

- **Componentes atualizados**: em todas as escalas, reorganizar etapa + mover entre sessões releu **3** visões de
  sessão (1 + origem + destino). As demais só renumeraram. Reordenar sessões reaproveitou as mesmas visões.
- **Degradação investigada**: o histórico ocupava cerca de **14,7 MB** por 100 operações com 1.000 etapas, porque cada
  fotografia copiava o estado de todas as etapas. Agora o estado imutável de cada etapa é compartilhado enquanto ela não
  muda, e as fotografias usam vetores alinhados, o que dá **cerca de 2,7 MB** (medição aproximada; o coletor de lixo
  não é determinístico).

## 20. Problemas encontrados

1. Desfazer em sequência tratava a entrada seguinte como conflito, porque restaurar conta revisão (teste de aplicação).
2. Reencadear o histórico sem conferir a vizinhança esconderia uma alteração externa (etapas do Recorder) entre duas
   entradas (teste de aplicação).
3. Memória do histórico alta com automações grandes (medição de desempenho).
4. **Homologação real:** subir e descer a mesma sessão deixava os cartões trocados na tela, com numeração certa e ordem
   errada. O `AutomationOverview` reconciliava pela ordem de inserção do mapa interno, um defeito latente desde a
   STUDIO-001 que só aparece ao reordenar.
5. **Homologação real:** Delete e Alt+↑/↓ durante a gravação eram ignorados em silêncio pela própria lista.
6. **Revisão das capturas:** valores longos cortados em "Origem e alterações"; campo da expressão manual estreito;
   título do aviso de bloqueio truncado em janelas estreitas.
7. Suíte completa da extensão interrompida pelo sistema por falta de memória (não é falha de teste; ver §17).

## 21. Correções realizadas

| Problema | Correção | Regressão |
|---|---|---|
| 1 e 2 | `EditHistory` reencadeia só a entrada vizinha (`after.revision == before.revision`); uma alteração externa continua sendo conflito | `EditorStudioTest.desfazerRefazer` e `refazer` |
| 3 | `AutomationStep` compartilha o estado imutável; `Snapshot` usa vetores alinhados | `EditorPerformanceTest` |
| 4 | Reconciliação pela ordem exibida; o mapa segue a ordem do domínio | `EditorStudioTest.subirEDescerSessao` |
| 5 | Os atalhos chegam ao caso de uso, que recusa e explica | `RecorderStepsStudioTest.s004BloqueioDuranteGravacao` |
| 6 | Parágrafos com quebra de linha, campos em largura total, dica com o localizador completo, título curto | capturas finais |

## 22. Limitações

`docs/studio-004/known-limitations.md`:

- histórico só em memória (100 por automação), descartado em conflito;
- edição bloqueada durante a gravação ("Nova sessão" permitida);
- sem arrastar e soltar;
- mudar a sessão no editor leva a etapa para o final;
- localizador manual sem verificação ao vivo;
- sem cofre de credenciais;
- sem movimentação entre automações;
- comentários sem autor.

## 23. Pendências

Nenhuma. A regressão de navegador da extensão foi completada na revisão técnica (211/211, ver §17).

## 24. Preparação para a STUDIO-005

- Cada etapa distingue o observado (`CapturedBaseline`, `capturedElement`, `RecordingObservation`, evidências e
  limitações) do editado (`ManualRevision`, `editedAt`). A STUDIO-005 pode associar evidências e resultados observados
  sem confundir o que o Recorder viu com o que o usuário mudou.
- `BusinessOutcome` continua `NOT_VALIDATED`, pronto para receber resultados de verificação reais.
- As referências de variáveis e credenciais (`ValueSource`) já ficam nas etapas, prontas para um cofre e para dados de
  execução.
- O histórico e as fotografias do agregado mostram como restaurar estados com as mesmas identidades, uma base para a
  persistência da STUDIO-006.

## Anexo — revisão técnica do PR #5 (P1 × 2 e P2 × 2)

Primeiro vieram os testes que reproduzem cada apontamento, rodados no código anterior: **7 falharam** (domínio 3,
Recorder 1, aplicação 3). Os 2 de controle (opção sem parâmetro anterior; opção sanitizada do Recorder) passavam e
continuam passando. Commit `6b2ab6d`.

| Achado | Defeito comprovado (antes) | Correção | Regressão |
|---|---|---|---|
| 🔴 P1 — `OptionChoice` aceitava dado sensível | "usuario@empresa.com", CPF, JWT (inclusive o trecho "eyJ…" isolado), Bearer, UUID, ID de contrato e hash eram aceitos no rótulo e no valor técnico, e iriam para a etapa e para o histórico | validação na fronteira de domínio (construtor de `OptionChoice`): `DisplaySafety.isSafeToDisplay`, até 120 caracteres, sem controle. `DisplaySafety` passou a reconhecer o trecho de JWT isolado. As opções do Recorder já passavam pela mesma barreira e continuam aceitas | `AutomationEditingTest.opcaoSensivelRecusada`; `EditorStudioTest.opcaoSensivelPeloEditor` (etapa, revisão e histórico intactos); `RecordedStepsTest.revOpcaoDoRecorderContinuaAceita` |
| 🔴 P1 — trocar o tipo descartava a opção digitada | FILL + Variável `cliente.estado` → SELECT_OPTION + São Paulo/SP: o modo "Variável" escondido vencia, a confirmação removia "o parâmetro" e a etapa ficava sem opção | o parâmetro efetivo é determinado pelo tipo selecionado. A confirmação lista **só** o que o tipo não aceita (a variável antiga) e nunca descarta o parâmetro novo válido | `EditorStudioTest.trocaDeTipoComParametroAnterior` (formulário → controlador → domínio) e `trocaDeTipoSemParametroAntigo` |
| 🟡 P2 — comentário não era intervenção manual | etapa gravada com comentário continuava "Alterada manualmente: Não" | `ManualRevision.comments`; o painel mostra "Comentários acrescentados" e "Campos capturados alterados: Nenhum". Excluir o comentário, ou desfazer, volta ao estado capturado | `AutomationEditingTest.comentarioEhIntervencaoManual`; `EditorStudioTest.comentarioComoIntervencaoNoPainel` |
| 🟡 P2 — duplicação levava chaves do evento | a cópia carregava `RECORDING_ID`/`RECORDED_EVENT_ID` na referência do elemento (chaves que o Recorder real põe ali) | `duplicateStep` copia a referência **sem** essas chaves (`CorrelationKeys.without`, `ElementReference.withCorrelation`). `TARGET_ID` e os demais dados do elemento ficam; o vínculo é `derivedFrom`; a original fica intacta | `AutomationEditingTest.duplicarSemChavesDoEventoNoElemento`; `RecordedStepsTest.revDuplicarEtapaProduzidaPeloRecorderNaoCarregaChavesDoEvento` (rascunho produzido pelo Recorder) |

Depois da revisão:

- Studio: **307/307** no Windows e no Linux (0 ignorados). Golden screenshots intactos e nenhum teste anterior alterado.
- Homologação real repetida: **36 verificações, 0 falhas** (`reports/studio-004/evidence/S004-homologacao-revisao-log.txt`).
- Extensão: **211/211** no navegador, em grupos isolados, e sem modificações.
