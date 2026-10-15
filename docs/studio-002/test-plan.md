# STUDIO-002 — Plano e matriz de testes

Níveis: **U** = unidade (Studio), **X** = unidade ou contrato na extensão (Java/Node), **C** = cruzado entre os
repositórios (processo real do Native Host), **B** = navegador real (Chrome for Testing + host instalado +
`Studio002BridgeIT`), **H** = homologação real no Windows (Studio de verdade + Chrome + host instalado, teclado e mouse
reais; `Studio002Homologation`).

| Id | Cenário | Onde | Resultado |
|---|---|---|---|
| S002-001 | Studio abre sem Chrome, sem login nem licença | U `RecorderStudioTest.s002001…`; H "Studio abre sem Chrome" | ✅ |
| S002-002 | Extensão funciona sem Studio | X `StudioLinkTest.s002002…` (sem opt-in o host nem procura); B `s002002SemStudio` (com opt-in e sem Studio, a inspeção funciona); regressão completa de 186 testes | ✅ |
| S002-003 | Descoberta e conexão autenticada | U `BridgeServerTest.conexaoAutenticada…`; X `StudioLinkTest.s002003…`; C `RealNativeHostBridgeTest.cadeiaReal`; B `s002006`; H | ✅ |
| S002-004 | Credencial inválida rejeitada | U `BridgeServerTest.recusa…`; X `StudioLinkTest.s002004…` e `autenticacaoMutua…` | ✅ |
| S002-005 | Protocolo incompatível rejeitado com erro explícito | U `BridgeServerTest.recusa…` (`PROTOCOL_UNSUPPORTED`); X `StudioLinkTest.s002005…` (`bridge.rejected`) | ✅ |
| S002-006 | Lista de abas autorizada e sanitizada | X Node "lista de abas…"; B `s002006ListaDeAbas`; U `RecorderUiTest.abasEScolha`; H "lista de abas sem URL completa" | ✅ |
| S002-007 | Página não autorizada: início recusado | X Node; B `s002007NaoAutorizada`; U `RecorderStudioTest.s002007…`; H "site sem autorização…" | ✅ |
| S002-008 | Página autorizada: captura técnica inicia | B `s002008CicloCompleto`; H "consumidor próprio na CaptureSession" e "gravação em site autorizado pelo usuário" | ✅ |
| S002-009 | Recorder e inspeção coexistem | X Node "coexistência"; B `s002009Coexistencia`; H | ✅ |
| S002-010 | Encerrar o Recorder mantém a inspeção | B `s002009Coexistencia`; H "inspeção continua ativa após finalizar o Recorder" | ✅ |
| S002-011 | Pausa confirmada pela extensão | U `RecorderControllerTest.pausaConfirmada…`; B (`lastEnded.reason = STUDIO_RECORDER_PAUSED`); H | ✅ |
| S002-012 | Retomada confirmada, captura restabelecida | B (consumidor readquirido, `segments = 2`); H | ✅ |
| S002-013 | Alternância de modo confirmada | U; X; B `modeChanged`; H "modo 'Capturar' confirmado pela extensão" | ✅ |
| S002-014 | Finalizar sem exportar PageMap | B (arquivos em `pagemaps` antes = depois; `pageMapExported=false`); H "nenhum PageMap exportado" | ✅ |
| S002-015 | Cancelar libera o consumidor sem mexer nas etapas | U `RecorderStudioTest.s002029…` (cancela); X Node; B (cancelamentos) | ✅ |
| S002-016 | Comandos repetidos não duplicam | X Node "início real… idempotência"; B `s002016Duplicidade` | ✅ |
| S002-017 | Finalizar e cancelar simultâneos: um desfecho | X Node; B `s002016Duplicidade` | ✅ |
| S002-018 | Perda do Studio: concessão expira e o consumidor é liberado | X Node "concessão" e "concessão vencida" (renovação atrasada ou concorrente com a expiração não reativa); U `RecorderControllerTest.renovacaoRecusada…`; B `s002018ConcessaoExpira` (5,05 s com concessão de 5 s; a inspeção ficou); H processo do Studio derrubado (10,15 s com concessão de 10 s; a inspeção ficou) | ✅ |
| S002-019 | Perda do Chrome: Studio DEGRADED | X `StudioLinkTest.s002019…`; C (`CHROME_DISCONNECTED`); U `perdaDaLigacao…`; H "Chrome encerrado…" | ✅ |
| S002-020 | Reinício do service worker reconciliado | X Node "reinício do service worker"; B `s002020ReinicioDoServiceWorker` (novo host reconectado, gravação RECORDING com o consumidor) | ✅ |
| S002-021 | Fragmentação sem intercalação | X `HostConcurrencyTest.s002021…` (153 fragmentos e 361 mensagens de controle concorrentes; 0 intercalações) | ✅ |
| S002-022 | Operação longa não atrasa os comandos do Recorder | X `HostConcurrencyTest.s002022…` (motor ocupado 6 s: ida e volta da pausa 4 ms); B `s002022OperacaoLonga` (motor ocupado 8 s: pausa confirmada em 3 ms) | ✅ |
| S002-023 | Eventos externos nunca alteram Swing fora da EDT | U `RecorderStudioTest.s002023…` (RepaintManager verificador: 0 violações com eventos de outra thread) | ✅ |
| S002-024 | Floating Recorder: mover, minimizar, restaurar | U (recolher e restaurar); H "janela movida pelo cabeçalho", "recolher…", "restaurada…" | ✅ |
| S002-025 | Quatro temas | U `RecorderStudioTest.s002025…`; H capturas nos 4 temas | ✅ |
| S002-026 | IDs e credenciais ocultos | U `RecorderStudioTest.s002026…`, `BridgeServerTest.publica…SemSegredoNosLogs`; X `respostaForaDoContratoNaoVaza`; C `contratoAplicadoPeloHostReal` | ✅ |
| S002-027 | Permissão revogada: captura encerrada e gravação degradada | X Node; B `s002027PermissaoRevogada`; H "revogação: Recorder interrompido" | ✅ |
| S002-028 | Aba fechada: estado apropriado | X Node; B `s002028AbaFechada`; U `interrupcaoDaPagina…` | ✅ |
| S002-029 | Nenhuma etapa artificial | U `RecorderControllerTest.s002029…`, `RecorderStudioTest.s002029…`; H "nenhuma etapa artificial" | ✅ |
| S002-030 | Sem sensores fictícios, IA, execução ou licença | U `StudioScopeTest.semFuncionalidadesFicticias` (estendido aos módulos novos e a "sensor"/"etapas gravadas"/"cliques capturados"); interface com os avisos da versão | ✅ |

## Testes adicionais

- Máquina de estados: `RecorderTransitionsTest` (alcançabilidade; nada confirmado sem passar pelo pendente; terminais).
- Atualização incremental (seção 20): `RecorderStudioTest.atualizacaoIncremental`. A sessão afetada é relida, as outras
  não são reconstruídas, as seguintes são renumeradas e a seleção é preservada.
- Saída e fechamento: `RecorderStudioTest.fecharESairComGravacao`; H 18.1.
- Contrato compartilhado: `BridgeContractFixturesTest` (Studio) e `BridgeContractTest` (extensão).

## Regressão

| Suíte | Antes | Depois | Observação |
|---|---|---|---|
| Studio Windows (`./mvnw clean verify`) | 162 | ver o relatório final | — |
| Studio Linux (`tools/golden-linux.sh verify`) | 162 | ver o relatório final | os golden Linux continuam aprovados |
| Extensão: Java | 208 | ver o relatório | — |
| Extensão: Node | 26 | 42 | — |
| Extensão: navegador | 186 | 186 + 11 | — |

Três testes da STUDIO-001 tiveram a premissa alterada por ordem desta missão, porque o botão "Gravar" agora é real. Eles
foram ajustados **com a mesma força** e estendidos aos módulos novos, sem relaxar nenhum critério; os detalhes estão no
relatório final.
