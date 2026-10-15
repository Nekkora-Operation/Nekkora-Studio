# NEKKORA-STUDIO-005 — Relatório final (Studio)

Evidências, mensagens de erro, contexto e resultados **observados**. Branch `feat/nekkora-studio-005` (base `8d2a887`).
O PR é separado do PR da extensão e fica **sem merge**. Dados fictícios. Sem IA, sem execução de automações, sem
validação de negócio, sem persistência definitiva e sem capturas de tela pelo produto.

## 1. Baseline

| Item | Valor |
|---|---|
| Studio `main` | `8d2a887` (merge do PR #5, STUDIO-004) |
| Extensão de referência | `8b251c5` (antes) → `feat/nekkora-studio-005-evidence` (depois) |
| Testes do Studio antes | 307/307 no Windows e 307/307 no Linux (Docker, Temurin 21), 0 ignorados |
| Testes do Studio depois | **337/337** no Windows e **337/337** no Linux, 0 ignorados (+30) |

## 2. Commits utilizados

| Commit | Conteúdo |
|---|---|
| `5d0aee8` | domínio (`evidence`), Bridge, Recorder, aplicação e UI |
| `c76cbcd` | homologação real `Studio005Homologation` e correção das abas dos detalhes (sem rolagem) |
| `a76eaec` | rótulos na matriz oficial S005-001..064; acessibilidade e quatro temas |
| `b1936c1` | documentação `docs/studio-005`, este relatório, evidências da homologação e README do contrato |
| `d386d2f` | revisão do PR #6 da extensão: 4ª barreira com a regra fail-closed pela forma |
| último | documentação da revisão e evidências da homologação depois das correções |

Extensão (PR próprio): `1d6eab6`, `b5e81d3`, `d2e11a2`, `793fad3`, `4632c4b`, `b9d4e06` (correções da revisão) e `17e62fe`.

## 3. Arquitetura

Ver `docs/studio-005/architecture.md`. Em resumo, o evento passa por leitura estrita, depois pela caixa ordenada (com
espera da etapa) e pela fila única do domínio (sempre depois da etapa). Em seguida vem `EvidenceIntake` na EDT, fora do
histórico, o evento `EVIDENCE` e a UI.

### Auditoria: necessidade × recurso existente × evolução

| Necessidade | Recurso existente | Evolução |
|---|---|---|
| receber eventos ordenados e confirmar depois do domínio | `StepInbox`, ACK cumulativo e sync (STUDIO-003) | `EvidenceInbox` com sequência própria, ACK **contíguo** (as evidências podem esperar a etapa) |
| entrada no domínio sem simular cliques | `StepIntake` | `EvidenceIntake` separada (enriquecer ≠ criar etapa) |
| achar a etapa de um evento | `CorrelationKeys.RECORDING_ID` + `RECORDED_EVENT_ID` (STUDIO-003/004) | `Automation.recordedStep(recordingId, eventId)` |
| resultado de negócio | `RecordingObservation.BusinessOutcome.NOT_VALIDATED` | reaproveitado e forçado no `ObservationSummary` |
| resultado observado | `observedOutcome` (texto livre, STUDIO-001) | mantido; acrescentado `ObservationSummary` estruturado e derivado |
| histórico de edição | fotografias de `AutomationStep.State` + revisão (STUDIO-004) | evidências **fora** do `State` e da revisão |
| bloqueio na gravação | `RecordingGuard` (STUDIO-004) | mantido para edição; a recepção de evidências é permitida |
| apresentação segura | `DisplaySafety`, `StudioText` | `EvidencePresentation` (4ª barreira na apresentação) |

## 4. Contratos

O `nekkora-bridge/1` recebeu só acréscimos: capacidade `evidence/1`, evento `studio.recording.evidence`, comandos
`studio.recording.evidence.ack/.sync`, `EvidenceState` e `EvidenceSummary`. `BridgeProtocol` lista os itens novos. O
schema e as fixtures foram copiados da extensão: `BridgeContractFixturesTest` exige que sejam **idênticos** (56 válidas,
35 inválidas) e confere o vocabulário com o schema. Recuo: `evidence/1` recusada leva a nova tentativa só com `steps/1`
e, depois, sem capacidades. O PageMap 0.2.0 não mudou.

## 5. Modelo de evidência

Ver `docs/studio-005/evidence-model.md`. `ObservedEvidence` é imutável e validado no construtor; `EvidenceType` tem 14
tipos; os vocabulários fechados estão em `EvidenceVocabulary`. `ObservationSummary` é estruturado (situação, contagem
por categoria, indicador de erro observado, textos omitidos, corte, limitações e última observação) e o resultado de
negócio é sempre `NOT_VALIDATED`.

## 6. Regras de observação

Elas estão na extensão (`Nekkora-Extension-Chrome/docs/studio-005/evidence-observer.md`). O Studio não observa a página
nem executa JavaScript (D-S005-03).

## 7. Regras de correlação

A correlação acontece na extensão. No Studio, a evidência só entra na etapa GRAVADA **desta** gravação com o mesmo
evento, e só se a ação foi executada. Evidências ambíguas ficam na automação, sem etapa. Nenhuma etapa é inventada:
a evidência que chega antes da etapa espera (fila limitada) e entra depois dela.

## 8. Políticas de privacidade

Ver `docs/studio-005/privacy.md`. O Studio é a 4ª barreira: leitura estrita (campos extras descartam o evento),
`Message.of` no **modo estrito** (nenhum texto livre de mensagem entra), rótulos inseguros viram `null`, e
nenhum identificador interno aparece na tela. A homologação comprovou que nenhum valor digitado ou dado sensível de
mensagem chega ao domínio, à tela, ao armazenamento da extensão ou aos logs do host.

## 9. Integração com o domínio

`Automation.attachEvidence` valida etapa, gravação, evento, execução, duplicata e limite, e **não** conta revisão.
`WorkspaceService` implementa `EvidenceIntake` na EDT, permitida durante a gravação e fora do histórico, e dispara
`StudioChange.EVIDENCE` só para a sessão afetada. `WorkspaceEvidenceSink` completa o futuro depois do domínio, e só
então vem o ACK. Ver `docs/studio-005/evidence-intake.md`.

## 10. Compatibilidade com o editor

Ver `docs/studio-005/editor-integration.md`. A aba **Evidências** é a última; as abas existentes ficam nas mesmas
posições, e o espaçamento das abas foi ajustado para as seis caberem. Há o grupo **Evidências** no painel, o selo na
lista (só com evidência) e o Floating Recorder com **Evidências** e **Última observação**. Mover preserva as evidências,
duplicar não as copia, e nenhuma tela as edita.

## 11. Undo/Redo

Ver `docs/studio-005/undo-redo-compatibility.md`. A evidência fica fora do `State` e da revisão: desfazer não apaga,
refazer não reaplica nem duplica e o histórico não é invalidado. Há teste de concorrência (60 evidências da thread do
Bridge × 20 ciclos de desfazer/refazer em outra automação) e Ctrl+Z/Ctrl+Y reais na homologação.

## 12. Máquina de estados

A máquina do Recorder (STUDIO-002/003) não mudou de estados. Mudou o que acontece em cada um:

| Estado/transição | Evidências |
|---|---|
| STARTING | pede `steps/1` + `evidence/1`; recuo em `BAD_REQUEST` |
| RECORDING | recebe, ordena, espera a etapa, registra e confirma |
| PAUSED | a extensão corta as janelas (`WINDOW_CUT_BY_PAUSE`), registradas como observação incompleta |
| DEGRADED/RECOVERING | lacunas pedem `evidence.sync` ao voltar; a reconciliação não duplica |
| COMPLETED | as evidências que ainda chegarem são aceitas (como as etapas); o resumo traz `EvidenceProgress` |
| CANCELLED | `halted`: nada mais entra; o descarte remove as etapas, as evidências delas e as não atribuídas da gravação |

## 13. Testes

| Classe | Testes | Foco |
|---|---|---|
| `EvidenceDomainTest` | 12 | identidade, ordem, recusas, `CAPTURE_ONLY`, ambígua, privacidade, resumo, duplicar/mover, desfazer/refazer, cancelamento, limite, tipos fechados |
| `RecorderEvidenceTest` | 12 | negociação e recuo, espera da etapa, ordem e duplicata, lacuna, ambígua, leitura estrita, 4ª barreira, recusa no ACK, cancelamento, incompletas |
| `RecorderEvidenceStudioTest` | 6 | aplicação real (EDT, painel, aba, selo, Floating Recorder, IDs), bloqueio com recepção, histórico, concorrência, cancelamento, acessibilidade e quatro temas |
| `BridgeContractFixturesTest` | (existente) | contrato e fixtures idênticos aos da extensão |

O mapeamento completo da matriz S005-001..064 está em `docs/studio-005/test-plan.md`. Depois da revisão do PR #6, o
S005-055 passou a estar coberto (teste novo no host).

## 14. Homologação real

`Studio005Homologation` (Windows, Studio real, Chrome for Testing com a extensão, Native Host instalado pelo script
oficial, laboratório fictício, mouse e teclado reais pelo Robot): **50 verificações, 0 falhas**
(`reports/studio-005/evidence/S005-homologacao-log.txt`).

| Cenário | Resultado |
|---|---|
| 1 Erro de autenticação | alerta associado ao "Entrar" (indicador de erro; texto omitido pelo modo estrito), envio observado, resumo "mudanças observadas", negócio não validado, etapa não editada, mensagem pré-existente ignorada, automação bloqueada para edição enquanto recebe evidências |
| 2 Mensagem tardia | a confirmação ("Pedido registrado com sucesso." na página) associada à etapa como status, dentro da janela; texto omitido; nada vira resultado de negócio |
| 3 Ambígua | um aviso com duas ações candidatas fica sem etapa; nenhuma das duas recebe o aviso; painel da automação lista "Evidências sem etapa definida" |
| 4 Validação | CEP inválido (aria-invalid, observado diretamente) e e-mail obrigatório (validação nativa, sem `reportValidity`); formulário não enviado |
| 5 Pesquisa | carregamento → fim → conteúdo → "3 resultados encontrados."; conteúdo da lista não sai da página |
| 6 SPA | rota (History API), `aria-expanded` false → true no próprio botão, novo documento com a janela cortada pela navegação |
| 7 Iframe | mensagem do iframe autorizado associada à ação no iframe (FRAME); clique no iframe sem permissão sem etapa nem evidência; aviso escrito pelo principal no iframe **não** atribuído; cobertura parcial |
| 8 Dados sensíveis | 7 mensagens; só "Senha incorreta." liberada (sem HTML); CPF, e-mail, JWT, nome, código e senha ausentes do domínio, da tela, do armazenamento e dos logs |
| 9 Editor | aba Evidências é a última; linha do tempo com horário real; Ctrl+Z/Ctrl+Y preservam as evidências; duplicada sem evidências; mover preserva |
| 10 Falhas | pausa corta a janela; mensagem durante a pausa não vira evidência; desconexão e reconexão sem duplicar; cancelar descarta só esta gravação |

O Chrome real também passou pelo `Studio005EvidenceIT` da extensão (13/13).

## 15. Evidências

`reports/studio-005/evidence/`: 12 capturas (`S005-01` a `S005-12`) e o log da homologação. As capturas inteiras mostram
a página de teste com os dados fictícios digitados nos campos **da própria página**; o Studio não mostra nenhum deles.
Na extensão: `reports/evidence/S005-*.json` (payloads reais da cadeia completa e medições).

## 16. Desempenho

Ver `docs/studio-005/performance.md`. Clique → etapa: p50 de 811 ms sem evidências e 810 ms com evidências; a evidência
não atrasa a etapa. A emissão leva 1–2 ms. A EDT só faz a operação de domínio e a notificação, com atualização
localizada na sessão. A rajada de 60 evidências com desfazer/refazer simultâneos não causou erro. `EditorPerformanceTest`
e os golden tests passaram sem ajuste.

## 17. Falhas encontradas

| Falha | Diagnóstico |
|---|---|
| com 6 abas, a faixa de abas dos detalhes rolava e "Geral" ficava atrás da seta; o Robot errava a aba | largura do diálogo grande (760) × títulos com ícone no `SCROLL_TAB_LAYOUT` |
| homologação: a sessão da gravação não trocava pelo seletor do Studio | a sessão da gravação muda pelo Floating Recorder (como na STUDIO-003) |
| homologação: cobertura parcial não aparecia depois de navegar no meio da gravação | a cobertura chega nas fotografias da gravação (comportamento da STUDIO-002/003) |
| homologação: espera da página com `?externo=` | a comparação usava a consulta junto com o caminho |
| homologação: "atraso 0 ms" | a referência era a própria evidência; passou a ser o início do gesto do Robot (documentado) |
| números de testes fora da matriz oficial | rótulos iniciais usavam outra numeração |
| na extensão: texto visível que repete um valor digitado aparece na captura técnica existente | achado fora do canal de evidências (relatório da extensão) |

## 18. Correções

- `StepDetailsForm`: espaçamento horizontal menor nas abas deste formulário. Títulos, ícones e ordem são os mesmos, e um
  teste na escala da tela prova que as 6 cabem (ele falha sem a correção, conferido).
- Homologação: troca de sessão pelo Floating Recorder, cobertura lida depois de uma troca de modo (ida e volta), espera
  de página sem a consulta, atraso medido do gesto e verificação de painel pelo conteúdo.
- Testes renumerados para a matriz oficial e novos testes de acessibilidade e quatro temas.

## 19. Limitações

Ver `docs/studio-005/known-limitations.md` (Studio) e `Nekkora-Extension-Chrome/docs/studio-005/known-limitations.md`.
Principais: sem persistência definitiva; ambíguas só no painel da automação; aba aberta é uma fotografia; cobertura e
título da aba no Floating Recorder só nas fotografias da gravação; janela fixa de 3 s; **modo estrito** (o texto das
mensagens da página nunca é mostrado; o catálogo de mensagens seguras é uma evolução futura); e o **achado de segurança
da captura técnica antiga**, que faz com que o
Recorder **não deva ser usado com dados reais** até a correção na origem.

## 20. Preparação para STUDIO-006

- Persistência: serializar `ObservedEvidence` (já imutável e com identidade) com a etapa e as não atribuídas com a
  automação, e guardar a sequência confirmada por gravação para reconciliar depois de reabrir.
- Migração: etapas sem evidências continuam válidas (`UNAVAILABLE`/`NOT_APPLICABLE`); o campo `observedOutcome` continua.
- Revisão do achado de privacidade da captura técnica (missão própria).
- Eventual associação manual de evidências ambíguas: precisa de decisão, porque D-S005-09 proíbe editar evidências.

## Critérios de aceite

| Critério | Situação | Onde |
|---|---|---|
| CA-001 evidências reais posteriores | atendido | homologação 1–8, IT 13/13 |
| CA-002 estruturadas e sanitizadas | atendido | contrato fechado, 4 barreiras |
| CA-003 mensagens de erro com sinais suficientes | atendido | cenário 1 (`ROLE_ALERT` + `ERROR_HINT`) |
| CA-004 status ≠ erro | atendido | cenários 2 e 5, regras |
| CA-005 validação sem alterar a página | atendido | cenário 4 (formulário não enviado) |
| CA-006 documento e rota preservados | atendido | cenário 6 |
| CA-007 enriquecer etapas confirmadas | atendido | mensagem tardia, evidência tardia no histórico |
| CA-008 nenhuma etapa duplicada | atendido | contagem de etapas em todos os cenários; envio tardio |
| CA-009 identidade documental e temporal | atendido | cenário 7 (aviso no iframe não atribuído), "Pedido lento" |
| CA-010 ambíguas não atribuídas | atendido | cenário 3 |
| CA-011 retransmitidas não duplicadas | atendido | reconexão (IT e homologação) |
| CA-012 porta apropriada | atendido | `EvidenceIntake` |
| CA-013 baseline imutável | atendido | `s005027E033…`, homologação |
| CA-014 Undo/Redo não modifica evidências | atendido | domínio, aplicação, Ctrl+Z/Ctrl+Y reais |
| CA-015 procedência preservada | atendido | etapa continua RECORDED, sem revisão manual |
| CA-016 negócio separado | atendido | `ObservationSummary`, contrato sem campo |
| CA-017 nenhum sucesso/fracasso inventado | atendido | tipos fechados, testes de texto |
| CA-018 ausência ≠ ausência de erro | atendido | "Nenhum sinal observado — isso não significa que não houve erro" |
| CA-019 CAPTURE_ONLY = não executada | atendido | sem observador; `EVIDENCE_NOT_APPLICABLE` |
| CA-020 contadores consistentes | atendido | Floating Recorder (testes e homologação) |
| CA-021 evidências e linha do tempo | atendido | painel e aba |
| CA-022 quatro temas | atendido | `s005AcessibilidadeETemas` |
| CA-023 nada sensível pelo Bridge | atendido | cenário 8, IT, host; revisão P1-a: campo limpo, removido, em Shadow DOM e dado de terceiro |
| CA-024 extensão independente | atendido | suíte 224/224; sem `evidence/1` nada muda |
| CA-025 versões anteriores compatíveis | atendido | Studio antigo × extensão nova; extensão antiga × Studio novo |
| CA-026 nenhum PageMap por evento | atendido | fixtures inválidas, leitura estrita |
| CA-027 suítes existentes aprovadas | atendido | Studio 337 (Win/Linux); extensão 237/133/226 |
| CA-028 novos testes S005 aprovados | atendido | `test-plan.md` |
| CA-029 homologação Windows | atendido | 50/0 |
| CA-030 PRs sem merge | atendido | dois PRs abertos para revisão |

## Anexo — revisão técnica dos PRs #6

A revisão aprovou a arquitetura do Studio e pediu duas correções P1 e uma P2 na extensão, mais o registro explícito do
achado de segurança. O procedimento seguiu a mesma ordem das revisões anteriores: **teste que reproduz → correção
localizada → regressão completa**, nas branches atuais (sem PR novo).

| Item | Reprodução (antes) | Correção | Prova (depois) |
|---|---|---|---|
| P1-a — valor digitado que some do campo | no Chrome real, "O código Kq7Segredo expirou." atravessou extensão → host → Studio com o texto disponível, porque a página tinha limpado o campo | impressões digitais salgadas dos valores (só hashes, na memória do documento) tiradas antes da reação da página; comparação também nos shadow roots; regra fail-closed pela forma nas 4 barreiras, inclusive no Studio (`Message.of`) | IT `s005SensitiveFieldGone` (campo limpo, removido, em Shadow DOM e dado de terceiro): tudo `SUPPRESSED`, nada no evento, no armazenamento, no host ou no Studio; testes Node, host e domínio |
| P1-b — reinício do service worker | `OBSERVATION_COMPLETE` com `eventId: null` depois do reinício (teste Node) | vínculo mínimo exato persistido com a janela + marca "etapa emitida"; sem etapa → perda declarada; invariante: só a ambígua sai sem etapa | 2 testes Node e IT com reinício **real** do service worker: `eventId` exato, janela encerrada uma vez, fila reconciliada (`pending = 0`) |
| P2 — prioridade no host (S005-055) | sem teste específico | teste novo `HostConcurrencyTest.s005055…` | motor ocupado e rajada de 300 evidências: pausa e finalização repassadas em 1 ms e 0 ms, antes do fim da rajada; 300/300 entregues |
| Achado de segurança (captura técnica antiga) | — | mantido separado, como pedido | explícito nas limitações: **não usar com dados reais** até a correção na origem |

### Regressão depois das correções

| Suíte | Resultado |
|---|---|
| Studio Windows | 337/337 |
| Studio Linux (Docker, extensão `b9d4e06`) | 337/337 |
| Extensão Java / Node / navegador | 237/237 · 133/133 · 226/226 |
| Homologação real `Studio005Homologation` | 50/0 (evidências atualizadas em `reports/studio-005/evidence/`) |

## Anexo 2 — revisão final: modo estrito de privacidade

A revisão final aprovou a arquitetura e as correções (P1-b e P2 encerrados) e decidiu que **o texto livre das mensagens
da página não atravessa o Bridge**, como comportamento padrão do Nekkora Recorder.

| Repositório | Commit | O que mudou |
|---|---|---|
| Extensão | `281d249` | origem (content script), service worker e Native Host: qualquer texto → `SUPPRESSED` + `MESSAGE_SUPPRESSED`; sem texto → `NONE`; o observador não lê mais valores de campos |
| Extensão | `52b39d6` | documentação e anexo |
| Studio | este PR | `Message.of` (4ª barreira) estrito; testes do domínio, do Recorder, da aplicação e da homologação atualizados; documentação |

- As evidências estruturadas foram preservadas: erro, status, validação, carregamento, navegação, estado e correlação
  continuam verificados, e só o texto passou a ser "Texto omitido por privacidade".
- Há teste explícito do dado de terceiro só em minúsculas, nunca digitado (`o cliente joão já existe`), no laboratório,
  no IT da extensão, em Node, no host, no Recorder e no domínio do Studio.
- Os testes de reprodução dos P1 continuam na suíte.
- Não houve nova configuração, IA ou mudança de correlação. O contrato não foi alterado.

**Regressão:** Studio 337/337 (Windows) e 337/337 (Linux, contra a extensão `281d249`); extensão 237/133/226;
**homologação real 50/0** (log e capturas atualizados). Nos arquivos de evidência da execução real da extensão há 38
mensagens e nenhuma com texto.

**Próximos passos combinados:** encerrar a STUDIO-005 (merge depois da conferência final), depois CHROME-SECURITY-001
(exposição de valores na captura técnica antiga, avaliando todos os canais da extensão) e só então a STUDIO-006
(persistência).

## Anexo 3 — revisão 3: metadados controlados (P1) e `Message` sem `AVAILABLE` (P2)

| Repositório | Commit | O que mudou |
|---|---|---|
| Extensão | `8efbf13` | região e campo só do vocabulário controlado; título `null`; três barreiras; reproduções (Node, host, IT no Chrome real) |
| Studio | `8407f9d` | `ObservedEvidence`: região e campo pelo `EvidenceVocabulary`, título `null`; **P2**: `Message` recusa `AVAILABLE`; reprodução no Recorder; homologação |

| Onde | Antes (reproduzido) | Depois |
|---|---|---|
| Node (`studio-evidence.test.js`, `REV-final P1`) | `region` com o `aria-label`, `field` com o rótulo, `title` com o título | só vocabulário; título `null` |
| Native Host (`EvidencePrivacyTest.revisaoFinalMetadadosComTextoDaPaginaNaoAtravessamOHost`) | metadados mascarados, texto passava | fora do vocabulário → `null` |
| Studio (`RecorderEvidenceTest.revFinalMetadadosComTextoDaPaginaNaoEntramNoStudio`) | região/campo/título "seguros" passavam | fora do vocabulário → `null`; título `null` |
| Chrome real (`Studio005EvidenceIT.s005MetadataStrict`, lab `metadados.html`) | `"region":"mensagem de erro o cliente joão já existe"` | `region: "mensagem de erro"`, `SUPPRESSED`/`null`, `MESSAGE_SUPPRESSED`; campo `"campo de telefone"`; título `null` |

**Novo achado, para a CHROME-SECURITY-001:** o canal de **etapas** (STUDIO-003) leva o nome acessível do elemento.
O teste de metadados registrou `"telefone do cliente joão"`. Também o caminho das URLs sanitizadas pode conter texto.
Nada disso foi alterado nesta revisão, e a restrição de não usar o Recorder com dados reais continua.

**Regressão:** Studio 338/338 (Windows) e 338/338 (Linux, contra `8efbf13`); extensão 238/134/227; **homologação real
50/0** (log e capturas atualizados). Nas evidências da extensão, nenhuma mensagem com texto, regiões e campos só do
vocabulário, títulos `null`.
