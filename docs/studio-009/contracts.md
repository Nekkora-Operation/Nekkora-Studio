# STUDIO-009 — Contratos

## 1. `nekkora-studio/guided-debug-plan/1.0.0` (G1)

O plano intermediário do debug guiado é uma projeção determinística do modelo (D-S009-01). Ele é produzido por
`nekkora-studio-guided` (`GuidedPlan.of(automação, relatório)`). Schema e fixture ficam em
`nekkora-studio-guided/src/test/resources/contracts/guided-debug-plan/1.0.0/`.

| Campo | Conteúdo |
|---|---|
| `automationId`, `modelVersion`, `automationRevision` | de onde o plano foi projetado |
| `planRevision` | `sha256:` do JSON canônico sem a própria revisão, sem `automationRevision` e sem `modelVersion` (D-S009-21: a versão do modelo vai a 1.2.0 só porque há decisões, e decisões são do registro). Muda quando o **conteúdo** do plano muda; não muda com o nome de uma etapa (que o plano não contém) nem com decisões. É a revisão que o caso Python congela |
| `staticValidation` | regras, estado e `"meaning": "STATIC_ONLY"`. `NO_STATIC_BLOCKERS` nunca é sucesso de execução |
| `variables[]` | como o contrato semântico 1.0.0 publica: nome, tipos, situação, sensibilidade, origem e produtora. Sem valor nem finalidade |
| `steps[].intent` | ação, requisito de alvo, **origem** do valor (`NONE`, `LITERAL`, `VARIABLE`, `CREDENTIAL`), variável declarada, tecla só do vocabulário fechado (`Enter`, `Tab`, `Escape`) e se há opção declarada. O literal e o apelido de credencial ficam retidos |
| `steps[].target` | a fonte da identidade é `TARGET_ID` (identidade lógica registrada pela extensão) ou `DESCRIPTOR` (só a referência compacta persistida). Inclui o estado do alvo efetivo, o tipo, a tag, o nome acessível apresentável, sensível, até 5 localizadores **como capturados** (validados ou não), o preferencial, os manuais (`NOT_VERIFIED`), a trajetória e as âncoras |
| `steps[].context` | aplicação, título e endereço **já sanitizados** pelo produtor: modelo de URL, nunca a URL completa |
| `steps[].applicability` | `INSPECT_TARGET`, `NO_TARGET_REQUIRED` ou `TARGET_MISSING`. No último caso, a etapa só admite Corrigir; localizador manual não promove identidade |
| `steps[].criteria` | o que o debug verifica no navegador: contexto, identidade, unicidade, visibilidade, habilitado, editável, tipo × ação, alcance de frame e de shadow |
| `steps[].uncertainties` | o que se sabe que **não** está comprovado antes do debug |
| `steps[].staticFindings`, `semanticUncertainties` | códigos da validação 1.1.0 e do contrato semântico para a etapa |
| `exclusions` | o que o plano nunca contém |

Garantias testadas por `GuidedPlanTest` e `GuidedPlanApiTest`:
- schema igual ao código;
- fixture congelada;
- mesmos bytes para o mesmo estado;
- ausência de literais, finalidades, nomes, apelidos e chaves de correlação, no JSON **e** em qualquer caminho da API Java;
- casos negativos: sem alvo, manual só ao lado de referência, descritor, frame/shadow, tipo incomum, sensível, tecla fora do vocabulário, relatório de outra automação.

## 2. `nekkora-bridge/1` + capacidade `guided-debug/1` (G2)

Acréscimo ao contrato da extensão (`contracts/studio-bridge.schema.json`). A cópia do Studio fica em
`nekkora-studio-bridge/src/test/resources/contracts`. A capacidade é negociada no `studio.debug.start` (`capabilities:
["guided-debug/1"]`). Studio novo com extensão antiga recebe `UNKNOWN_COMMAND`. Uma capacidade desconhecida recebe
`BAD_REQUEST`.

| Comando | Payload | Resposta |
|---|---|---|
| `studio.debug.start` | `debugId`, `operationId`, `tabRef`, `leaseMs`, `capabilities` | `DebugResult` (`debug`, `duplicate`): idempotente por `operationId` |
| `studio.debug.inspect` | `debugId`, `requestId`, `stepRef`, `actionType`, `targetId` ou `null`, `descriptor`, `highlight` | `DebugInspectResult`: idempotente por `requestId` |
| `studio.debug.highlight` | `debugId`, `requestId`, `candidateRef` (`null` limpa) | `DebugHighlightResult` |
| `studio.debug.pick` | `debugId`, `requestId`, `action` (`START`, `CANCEL` ou `ADOPT_CANDIDATE`), `candidateRef`, `actionType` | `DebugPickResult`; o elemento chega em `studio.debug.picked` |
| `studio.debug.finish` | `debugId`, `operationId` | `DebugResult` |
| `studio.debug.status`, `studio.debug.renew` | `debugId` | `DebugResult` |
| `studio.debug.probe` (G6) | `debugId`, `requestId`, `stepRef`, `operation` (`FILL_FIXTURE`, `CLICK`, `PRESS_ENTER`), `targetId`, `descriptor`, `valueKey` (só a CHAVE do catálogo sintético: `lab:registro-ativo`, `lab:registro-suspenso`, `lab:registro-encerrado`, `lab:registro-inexistente`), `authorization: "LAB_PROBE"` | `DebugProbeResult`: `status` (alvo reencontrado) ≠ `performed` (operação feita) ≠ `after.domChanged/navigated` (mudança observada); `reasons` de vocabulário fechado; idempotente por `requestId` (repetir um clique exige pedido novo). Fora de origem LAB: `PROBE_NOT_ALLOWED`. O valor do catálogo nunca atravessa o Bridge |

Erros novos: `DEBUG_NOT_FOUND`, `DEBUG_ENDED`, `CANDIDATE_EXPIRED` (candidato de uma época anterior da página), `PROBE_NOT_ALLOWED`.

| Evento | Payload |
|---|---|
| `studio.debug.picked` | `debugId`, `requestId`, `pageEpoch`, `observedAt`, `origin` (`BROWSER_SELECTION` ou `ADOPTED_CANDIDATE`), `element` (o `ElementRef` de etapa, com a nova `correlation.targetId`), `checks`, `box`, `notes` |
| `studio.debug.changed` | `debug` (estado, época, motivo em código: `NAVIGATED`, `DOCUMENT_REPLACED`, `TAB_CLOSED`, `PERMISSION_REVOKED`, `EXTENSION_RESTARTED`, `LEASE_EXPIRED`, …). Navegar na aba (`webNavigation.onCommitted`, quadro principal) avança a época mesmo sem captura (D-S009-20); `picking: false` sem `picked` = a seleção não pôde ser descrita |

O Studio entrega aos ouvintes só os eventos `studio.debug.*` da lista fechada do contrato (D-S009-19). Canal perdido = sessão encerrada do lado do Studio (motivo LOCAL `LINK_LOST`, que não trafega); a próxima abertura encerra a sessão órfã com `studio.debug.finish` (D-S009-17).

`DebugInspectResult`:

| Campo | Conteúdo |
|---|---|
| `status` | os do `TargetResolver` (`RESOLVED`, `NOT_FOUND`, `AMBIGUOUS`, `IDENTITY_MISMATCH`, `STALE_TARGET`, `SCOPE_UNAVAILABLE`, `INSUFFICIENT_EVIDENCE`) + `CONTEXT_UNAVAILABLE`. Só `RESOLVED` comprova a identidade |
| `identitySource` | `TARGET_ID` ou `DESCRIPTOR` |
| `reasons` | até 16 códigos de um vocabulário fechado (`DebugReasonCode`) |
| `checks` | `identity`, `unique`, `visible`, `enabled`, `editable`, `kindMatchesAction`, `frameReachable`, `shadowReachable` ∈ {`PASS`, `FAIL`, `UNKNOWN`, `NOT_APPLICABLE`} |
| `element` | só tipo, nome do vocabulário, tag e se é sensível |
| `candidates` | até 5, só os plausíveis, com `candidateRef` válido para a época atual |
| `highlighted`, `box` | caixa em px do viewport e posição aproximada na tela |
| `continuity` | D7: continuidade técnica, que nunca é identidade |

Não existe campo para frase livre, valor, PageMap, `nodeId` ou rótulo de confirmação humana. As fixtures inválidas `s009-*`
provam isso.

Barreiras:
- **Service worker:** filtra códigos e passa os nomes pelo vocabulário.
- **Host (`DebugPrivacy`):** a mesma barreira de etapa para o elemento escolhido; nomes pelo vocabulário no resumo e nos candidatos; schema estrito.
- **Studio (4ª barreira, no `GuidedDebugClient`):** reaplica a política.

Host (Native Messaging, `nekkora-nm/1`, acréscimo):
- `debug.resolve` usa o resolvedor **somente leitura** (D-S009-03).
- `debug.node.state` faz as verificações situadas de um nó.

## 3. `.nekkora` 1.2.0 (G4)

O 1.1.0 mais dois campos em cada etapa (`effective`):
- `targetReplacement`: o elemento que substitui o capturado depois de uma correção aprovada, com a mesma forma de `capturedElement`;
- `targetDecision`: `kind` (`CONFIRMED` ou `REJECTED`), `actor` (`DEVELOPER` ou `TEST_AGENT`), `decidedAt`, `basis` (resumo **em códigos** da verificação: fonte, status, motivos, instante, modelo de URL) e `expectation` (ação, origem do valor, variável, `targetId`, tipo, tag, expressão preferencial, trajetória).

`StepField` ganha `TARGET`, válido só no 1.2. Regras de versão:
- o escritor grava a **menor** versão capaz: 1.2.0 só com ao menos uma decisão ou substituto; senão 1.1.0 ou 1.0.0, byte a byte como antes;
- no 1.2.x o catálogo de variáveis é opcional;
- o leitor recusa um 1.2.0 sem decisões, um campo de decisão ou `TARGET` em 1.0/1.1, e tudo o que é mais novo que 1.2.

Gravar 1.2 sobre o próprio arquivo 1.0 ou 1.1 é migração: pede confirmação explícita e faz uma cópia `… (formato x.y.z).nekkora`, como na STUDIO-008.

Schema e fixtures ficam em `nekkora-studio-persistence/src/test/resources/contracts/nekkora-project/1.2.0/`: 2 válidas e 11 inválidas.
- Inválidas:
  - ator `HUMAN_CONFIRMED` ou `AI_VERIFIED`;
  - decisão sem ator;
  - frase livre na base;
  - motivo fora de código;
  - substituto com texto da página;
  - URL completa;
  - 1.2 sem decisões;
  - substituto incompatível com a ação;
  - decisão ou `TARGET` em arquivo 1.1.
- Ajuste em fixtures congeladas: as fixtures "formato mais novo" do 1.0.0 e do 1.1.0 passaram a usar 1.3.0 (antes 1.2.0, que agora é real). É o mesmo ajuste que a STUDIO-008 fez no 1.1.0; os schemas congelados não mudaram (D-S009-12).

## 4. `nekkora-studio/guided-debug-record/1.0.0` (G7)

Registro de uma ou mais passagens do debug guiado: o contexto SOMENTE LEITURA para quem interpreta (desenvolvedor,
agente de teste, geração assistida). Produzido por `GuidedDebugRecord.toJson` (módulo `nekkora-studio-guided`); schema e
fixture em `nekkora-studio-guided/src/test/resources/contracts/guided-debug-record/1.0.0/`.

| Campo | Conteúdo |
|---|---|
| `automationId`, `planSchema`, `planRevision` | o plano verificado (a mesma revisão do §1) |
| `operator` | `DEVELOPER` ou `TEST_AGENT` — nunca confirmação humana nem "verificado por IA" |
| `meaning` | `NO_EXECUTION_NO_BUSINESS_OUTCOME`: nada foi executado; resultado de negócio não é afirmado |
| `entries[]` | `stepId`, instante, `kind` (`VERIFICATION`, `DECISION`, `CORRECTION`, `PROBE`), `status`, `identitySource`, `reasons`, `outcome` (ex.: `REUSE`, `REVIEW`, `PERFORMED`), `outcomeReasons`, `actor` — **só códigos** |
| `finalDecisions[]` | por etapa que precisa de alvo: decisão final, ator, status da base, se o alvo foi substituído |
| `summary` | contagens (passagens, confirmadas, rejeitadas, sem decisão, reusos, sondas feitas) |
| `exclusions` | `VALUES`, `NAMES_AND_DESCRIPTIONS`, `PAGE_TEXT`, `RESOLVER_STATEMENTS`, `CORRELATION_KEYS`, `BUSINESS_OUTCOME` |

Plano + registro da mesma `planRevision` = **plano congelado** (D-S009-21): é a entrada do caso Python.

## 5. Script Python do caso de laboratório (G9)

`docs/studio-009/python/gerar.py --plano P --registro R --saida S [--diff D]` (D-S009-23):

| Saída do gerador | Significado |
|---|---|
| 0 `GERADO` | script escrito (com `# nk-conteudo-sha256` do próprio conteúdo) |
| 2 `BLOQUEADO` | `bloqueios[]`: contrato errado, registro de outra automação/revisão, etapa sem decisão `CONFIRMED`, ação fora de FILL/CLICK, localizador sem expressão, quadro/shadow, valor sem variável |
| 3 `EDICAO_MANUAL_PRESERVADA` | o script de saída foi editado à mão: nada sobrescrito; `<saida>.novo.py` + `<saida>.diff` |

Script gerado: `--cdp URL --url-aba URL [--debug PASTA] [--saida JSON] [--timeout-ms N]`; entradas só por
`NK_ENTRADA_<VARIAVEL>` (nunca registradas).

| Saída do script | Significado |
|---|---|
| 0 `ETAPAS_EXECUTADAS` | todas as etapas fizeram a ação. **Não é sucesso de negócio**: quem decide é o oráculo do laboratório |
| 1 `PARADO_NA_FALHA` | uma etapa falhou (`NOT_FOUND`, `AMBIGUOUS`, `IDENTITY_MISMATCH`, `TIMEOUT`, `PLAYWRIGHT_ERROR`); as seguintes ficam `NOT_RUN` |
| 3 `ORIGEM_NAO_AUTORIZADA` | a aba não é do laboratório local: nada conectado, nada feito |
| 4 `ENTRADA_AUSENTE`, 5 `ABA_NAO_ENCONTRADA`/`ABA_AMBIGUA` | nada feito |

`--debug`: por etapa, o mesmo destaque do Studio (caixa rosa sólida, sem etiqueta), captura com os campos editáveis
mascarados e `depuracao.jsonl` (`stepId`, ação, localizador, contagem, caixa, captura, status, ms, instante).
