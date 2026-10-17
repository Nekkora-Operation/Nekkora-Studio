# STUDIO-009 — Homologação real (Windows 11, 1920×1080)

**Ambiente:**
- Studio desta branch e Chrome for Testing 155.0.8059.39 com a extensão da branch `feat/nekkora-studio-009-guided-debug`.
- Native Host instalado pelo script oficial, com diretórios de dados, bridge e Studio isolados.
- Laboratório "Consulta de registro" em `127.0.0.1:18765` (dados fictícios) e mouse/teclado do sistema (Robot).

**Operador:** Claude, como **agente de teste** (`TEST_AGENT`, modo de laboratório explícito). As decisões se baseiam no que a janela do Studio mostra. O gabarito (oráculo do servidor) só é lido para **conferir** depois.

**Roteiro:** `nekkora-studio-app/src/test/java/com/nekkora/studio/app/evidence/Studio009Homologation.java`.

**Evidências:**
- `evidence/g8/`: capturas, log, plano, registro e projeto;
- `evidence/g8/150/`: 150%;
- `evidence/g9/`: scripts, planos, registros, resultados, depuração e capturas do Python.

Proteção da máquina: antes de cada clique, tecla ou captura, o roteiro confere se o primeiro plano é do Studio ou do Chrome de teste. Também mascara todo campo editável nas capturas.

## G8 — 106/106 (100%) e 11/11 (150%)

| Bloco | O que foi provado |
|---|---|
| H1 Mapeamento | gravação real (preencher código, clicar Consultar), `targetId` por etapa, valor fora do modelo; variável `registro.codigo` declarada |
| H2 Plano | 2 etapas inspecionáveis pela identidade registrada; sem valor e sem chaves de correlação |
| H3 1ª passagem | proposta verificada, destaque rosa sobre o campo, zero interação com a página, oráculo 0; janela não cobre o alvo; sonda LAB preenche (catálogo) e clica → oráculo 1 consulta, com o código da chave `lab:registro-ativo`; 2 decisões CONFIRMED TEST_AGENT |
| H4 2ª passagem | REUSE ×2 sem pergunta; nada consultado |
| H5 Salvar/reabrir | 1.2.0 com decisões TEST_AGENT, sem valor; reaberto → REUSE ×2 |
| H6/H13 15 variantes | ver [test-plan.md](test-plan.md): comprovado só sobre o elemento certo; ambíguo nunca comprovado; oculto/desabilitado nunca reconfirmado; zero interação |
| H13b/H8 Quadro e shadow | Corrigir → selecionar **dentro do iframe**: botão para "preencher" incompatível (Aplicar desabilitado) → descartar → campo compatível; shadow aberto compatível; nada acionado |
| H10 Navegação | o usuário navega (barra de endereço) → época 1 → 3 → nova verificação; a janela não rouba o foco |
| H11 Aba fechada | sessão encerrada, a janela diz por quê |
| H16 Desconexão | desligar "Conectar ao Studio" → sessão encerrada no Studio, nada confirmável; religar → sessão órfã encerrada, nova sessão normal |
| H7/H9 Duplicado | campo do 1º formulário comprovado pelo id gravado → Continuar; botão ambíguo → revisão → Corrigir → selecionar → comparar → Aplicar; o botão idêntico **continua não comprovado** (política da CHROME-SECURITY-001); Ctrl+Z/Ctrl+Y da substituição; salvo em 1.2.0 com `targetReplacement` |
| H14 Temas | Sakura Dark, Midnight Dark, Porcelain Light, Sage Light: janela inteira na tela, ações visíveis, alvo não coberto — em 100% e 150% |
| H15 Privacidade | projeto, registro, plano, log, logs e dados do host, dados do Studio: sem o valor digitado/sondado e sem o nome fictício do rótulo suprimido |

## G9 — 30/30 (Python + Playwright por último)

Ambiente: venv com `playwright==1.63.0` ([python/requirements.txt](python/requirements.txt)), conectado por CDP ao Chrome de teste.

| Passo | Resultado |
|---|---|
| P1 | passagem de debug valida o plano (identidade comprovada sobre o elemento certo nas 2 etapas) |
| P2 | plano + registro da mesma revisão congelados; script gerado com uma função por StepId |
| P3 | execução real: `ETAPAS_EXECUTADAS`; **oráculo**: 1 consulta com o código fictício; 2º debug: caixas do script **iguais** às do Studio por StepId |
| P4 | origem fora do laboratório → `ORIGEM_NAO_AUTORIZADA`, nada feito |
| P5 | `#consultar` no botão Limpar → `IDENTITY_MISMATCH`, Limpar não clicado, oráculo 0 |
| P6 | botão oculto → `TIMEOUT`, parada limpa, oráculo 0 |
| P7 | **quebra deliberada** (ids mudados) → 1.1 `NOT_FOUND`, 1.2 `NOT_RUN`, oráculo 0 |
| P8 | diagnóstico pelo debug guiado (1.1 RESOLVED com `ID_ATTRIBUTE_CHANGED`; 1.2 NOT_FOUND) → reparo **no modelo** (Corrigir → selecionar → comparar → Aplicar), capturado intacto |
| P9 | nova validação; revisão do plano mudou; registro marca os 2 alvos substituídos |
| P10 | script editado à mão preservado (`.novo.py` + `.diff`) |
| P11 | regeneração: o diff mostra só cabeçalho/revisão e os localizadores novos (`input#codigo-v2`, `consultar-v2`) com os mesmos StepIds ([python/reparo.diff](python/reparo.diff)) |
| P12 | nova execução: oráculo 1 consulta; mesmas caixas do Studio |
| Privacidade | nenhum arquivo do G9 contém o valor fornecido na execução |

## Achados da homologação (todos corrigidos e cobertos por teste)

| Achado | Correção |
|---|---|
| O Bridge do Studio descartava `studio.debug.picked/changed` (seleção, navegação, fim de sessão nunca chegavam) | D-S009-19, `BridgeServerTest` |
| Navegar não avançava a época sem captura | D-S009-20, IT G8-02 (falha sem a correção) |
| Canal perdido deixava Continuar habilitado e a aba "ocupada" | D-S009-17, `GuidedDebugUiTest.canalPerdido` |
| Seleção que não pôde ser descrita deixava a janela esperando | presenter volta a "Corrigir" com explicação |
| Oculto/desabilitado comprovado aparecia como "Proposto" | D-S009-16, `GuidedDebugUiTest.estadoInadequado` |
| Ações da janela saíam da tela com conteúdo grande; "Usar este" cortado | D-S009-18; candidato em duas linhas |
| Pacote `domain/target` nunca versionado (`.gitignore` `target/`) | `.gitignore` só para a saída do Maven; a regressão Linux pegou |
| `planRevision` mudava com as decisões (modelo 1.2.0) | D-S009-21, `GuidedPlanTest.revisao` |
| Valor fictício visível em captura | D-S009-22 (máscara); evidências regeneradas |
| Um mensageiro veio para a frente durante o Robot | rodada interrompida, capturas apagadas, verificação de primeiro plano no roteiro |

## Como reproduzir

```sh
# 100% (6 min, usa mouse e teclado; deixe a máquina livre e o "Não incomodar" ligado)
java -Dnekkora.studio.decisionActor=TEST_AGENT -cp <classpath de teste do app> \
  com.nekkora.studio.app.evidence.Studio009Homologation <saída> <repositório-da-extensão>
# 150%: -Dsun.java2d.uiScale=1.5 -Dnekkora.s009.project=<.nekkora da rodada de 100%> ... escala
# G9:   -Dnekkora.s009.python=<python do venv com requirements.txt> ... python
```

Use `NEKKORA_BRIDGE_DIR`, `NEKKORA_DATA_DIR` e `NEKKORA_STUDIO_DATA_DIR` isolados: o Chrome do usuário não pode se conectar ao Studio de teste.
