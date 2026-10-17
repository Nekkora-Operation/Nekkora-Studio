# STUDIO-009 — G0: auditoria e contrato da missão

Data: 17/10/2026.

| Repositório | Base | Branch |
|---|---|---|
| Studio | `main` = `a439a65` (merge do PR #11, STUDIO-008) | `feat/nekkora-studio-009` |
| Extensão | `main` = `2c7c06b` (merge do PR #7, CHROME-SECURITY-001) | `feat/nekkora-studio-009-guided-debug` |

Linhas de base de teste, herdadas das últimas regressões publicadas:

| Ambiente | Resultado |
|---|---|
| Studio, Windows | 525 testes |
| Studio, Linux | 524 aprovados + 1 pulado |
| Golden screenshots | 4/4 |
| Extensão, Java | 271 testes |
| Extensão, Node | 148 testes |
| Extensão, Chrome | 251/251 testes |

Esta auditoria foi feita por leitura de código, documentos e testes dos dois repositórios. As afirmações citam o arquivo.
Na tabela da §5, "a provar" indica o que será demonstrado em Chrome real no gate correspondente.

## 1. Inventário de reutilização

| Necessidade da missão | O que já existe | Onde | Lacuna |
|---|---|---|---|
| Modelo da automação | `Automation → LogicalSession → AutomationStep`, ações, parâmetros, variáveis e semântica | `nekkora-studio-domain` | nenhuma: o plano é uma **projeção** desse modelo, sem segundo agregado |
| Alvo original × efetivo | `capturedElement` (imutável) e `element` efetivo; `CapturedBaseline`; `StepProvenance`/`RecordingObservation` | `AutomationStep`, `CapturedBaseline` | o efetivo hoje só troca o **localizador preferencial** (`withPreferredLocator`). Substituir o alvo por outro elemento (Corrigir → Selecionar) é operação nova |
| Localizador manual | `ManualLocator`, sempre `NOT_VERIFIED` | `element/ManualLocator` | a verificação ao vivo "é de missão futura" (L1 da STUDIO-007): é esta missão |
| Identidade lógica do alvo | `TargetIdentity` (assinatura, discriminadores, sósias, cadeia de frames, âncora D7) e `TargetResolver` | extensão, `java-core/core/.../identity`, `.../resolve` | **reutilizar sem duplicar no Studio** |
| Resultado com evidência | `ResolutionStatus` ∈ {RESOLVED, NOT_FOUND, AMBIGUOUS, IDENTITY_MISMATCH, STALE_TARGET, SCOPE_UNAVAILABLE, INSUFFICIENT_EVIDENCE}, mais diagnósticos de frame/shadow e `TechnicalContinuity` | `ResolutionResult` | as `evidence` do resolvedor são frases livres (podem citar valores da assinatura): **não** podem cruzar o Bridge. É preciso mapear para códigos |
| Vínculo etapa ↔ identidade | cada etapa gravada leva `targetId`, `nodeId`, `captureId` e `documentId` em `CorrelationKeys`; o `targetId` foi registrado por `recorder.element.describe` | `studio-steps.js:567`, `RecorderCommands` | nenhuma: o vínculo existe e é persistido no `.nekkora` (`ProjectCodec`, campo `correlation`) |
| Destaque rosa sem interação | overlay `<nekkora-overlay>`, shadow root fechado, `pointer-events:none`, acompanha scroll/re-render via rAF, rosa oficial `#C2466D` | `content/highlighter.js` | reutilizar; acrescentar o estado visual do debug (rótulo curto por código) |
| Seleção pontual no navegador | `inspector.js`: modo INSPECT intercepta pointer/mouse/click/submit na fase de captura (a ação do elemento **não** dispara); suspenso enquanto o Recorder grava | `content/inspector.js` | hoje só o painel da extensão aciona. Falta acionar pelo Studio e devolver a referência compacta |
| Inspeção → identidade → verificação | `selectNode`: inspect, `target.register`, `cs.verifyLocators`, `locators.verify`, destaque. `resolveTarget`: captura, `target.resolve`, destaque **só** com RESOLVED | `background/inspection.js` | reutilizar como motor; falta a superfície Studio |
| Canal Studio ⇄ extensão | `nekkora-bridge/1`: UDS autenticado, host valida **toda** mensagem contra `studio-bridge.schema.json`, lista **explícita** de comandos repassados (sem relay genérico), controlador (`instanceId`), concessão (lease), códigos de erro fechados | `BridgeServer`, `StudioLink`, `BridgeContract`, `studio-recording.js` | comandos de debug entram como **acréscimo** do contrato (capacidade negociada), como fizeram `steps/1` e `evidence/1` |
| Aba autorizada | `browser.tabs.list` com `tabRef` opaco e classificação LAB/AUTHORIZED/…; política central de acesso | `access-policy.js`, `BrowserTab`, `TabAccess` | reutilizar: o debug só abre em aba LAB ou AUTHORIZED |
| Privacidade | `nekkora-privacy/2` (vocabulário controlado, atributos estruturais, analisador de expressões, URL origem + modelo); 4 barreiras; `CapturedDataPolicy` no `.nekkora` | extensão `shared/privacy.js`, `contracts/privacy/*`; Studio `CapturePrivacy`, `CapturedDataPolicy` | **intocada**. Todo dado novo que sai da página passa pelas mesmas funções |
| Janela flutuante que não rouba foco | `FloatingRecorderWindow`: `autoRequestFocus=false` e `focusableWindowState` temporariamente falso (contorno do Windows), sempre no topo | `nekkora-studio-ui/.../recorder` | reutilizar o padrão para a janela do debug |
| Laboratório | `LabServer` + `LabApi` (dados fictícios), páginas por missão, origens `127.0.0.1`/`localhost` (LAB) | extensão `test-lab`, `java-core/.../lab` | falta o site do caso, com variantes de DOM, reset e oráculo independentes |
| Validação estática e contrato semântico | `AutomationValidator` (regras 1.1.0) e `SemanticContract` 1.0.0 | Studio | entram no plano como incertezas; `NO_STATIC_BLOCKERS` ≠ sucesso de execução |
| Persistência | `.nekkora` 1.0.0/1.1.0 congelados, leitor estrito, migração explícita com cópia | `nekkora-studio-persistence` | decisões de confirmação exigem **formato 1.2.0** (§6) |
| Homologação real | Robot + cliente CDP mínimo; dados e Bridge isolados | `Studio00xHomologation`, `BrowserLab` | reutilizar |

## 2. Recuperação da identidade depois de reabrir o projeto

A pergunta do G0 é se, depois de salvar e reabrir o `.nekkora`, ou de reiniciar Studio, Chrome e host, o Studio consegue
pedir com segurança "reencontre o alvo desta etapa".

| Caminho | Fato verificado | Conclusão |
|---|---|---|
| (a) `targetId` no registro do host | `TargetRegistry` persiste em `targets/registry-v2.json`, conteúdo sob `nekkora-privacy/2`. **Só para origens de laboratório** (`Privacy.isLabOrigin`); origens reais ficam só em memória (REAL-001). O registro é do host **desta máquina** e não viaja com o `.nekkora` | funciona no laboratório e na mesma máquina; **não é portátil** e some para sites reais quando o host reinicia |
| (b) Dono do alvo | `target.resolve` recusa `TARGET_NOT_OWNED` quando a aba atual não é a aba de registro. Depois de reiniciar o Chrome, o `tabKey` sempre muda | usar `target.resolve` como está **falha sempre** após reinício. O javadoc do registro promete "resolução explícita por targetId", mas o código a recusa |
| (c) `ElementReference` persistido | o `.nekkora` guarda tipo, papel, nome (vocabulário), tag, id/name seguros, até 5 localizadores analisados por inteiro, trajetória, âncoras de contexto e sósias. Tudo é ponto fixo da `nekkora-privacy/2` (`CapturedDataPolicy`) | é um **descritor recuperável** que já existe e respeita a privacidade, mas é mais pobre que a assinatura completa (sem label, formulário, chave de linha, textos de contexto) |

**Decisão proposta (D-S009-03):**

1. **Resolução de debug somente leitura, por `targetId`, na aba do debug.**
   - O host compara a identidade registrada com a captura atual da aba autorizada da sessão de debug.
   - Não atualiza o registro nem transfere o dono. O isolamento da CHROME-002 continua valendo para reencontro automático e exportação.
   - Esta é a "resolução explícita" que o javadoc já previa, agora restrita à sessão de debug.
2. **Sem `targetId` utilizável, o descritor recuperável (c) entra como fallback.** Exemplos: alvo desconhecido no registro, outra máquina, origem não laboratorial depois de reinício.
   - A extensão monta uma identidade **transitória** a partir do descritor e roda o **mesmo** `TargetResolver`. Nada é registrado.
   - O resultado declara `IDENTITY_SOURCE=DESCRIPTOR`: a prova é mais fraca e o reuso de confirmação é restrito (§5).
3. **Nenhum caminho promove "um localizador casou" a identidade.** O status sempre vem do resolvedor; com só o descritor e
   sósias, o resultado é `AMBIGUOUS` ou `INSUFFICIENT_EVIDENCE`.

Isto não é incompatibilidade material:
- a privacidade não muda: só sai do host o resultado em códigos, e a assinatura já está sob a `privacy/2`;
- o contrato só ganha acréscimos.

Por isso o G0 não para. A mudança de comportamento do item 1 fica registrada para revisão.

## 3. O que não existe e precisa ser construído (lacunas)

1. **Plano intermediário (G1).** Projeção determinística das etapas e dependências, com incertezas e critérios de verificação, sem GUI e sem valores.
2. **Superfície de debug no Bridge (G2).** Sessão de debug numa aba autorizada, com controlador e concessão. Comandos tipados:
   - inspecionar o alvo de uma etapa;
   - destacar e limpar;
   - selecionar no navegador;
   - cancelar e encerrar.

   Eventos: seleção feita, contexto alterado, sessão degradada.

   Requisitos: cancelamento, idempotência por `requestId`, tempo-limite e negociação por capacidade (`guided-debug/1`).
3. **Barreira de privacidade do debug no host (G2).** `DebugPrivacy`, como `StepPrivacy`/`EvidencePrivacy`. O resultado sai como status, códigos, contagens e a referência compacta já filtrada; nunca as frases livres do resolvedor.
4. **Janela flutuante do debug (G3).**
   - Mostra etapa, reconhecimento, motivo, evidências, limitações, Continuar/Corrigir e detalhes expansíveis.
   - Estados visuais: proposto, confirmado pelo ator, verificado tecnicamente, requer revisão, não verificável.
5. **Decisões no domínio (G4).**
   - `TargetConfirmation`: durável, com ator, autoridade e revisão, separada da `TargetVerification`, que é situada e envelhece.
   - Substituição do alvo efetivo por referência nova: atômica, com Undo/Redo, sem tocar `capturedElement`/baseline.
6. **Formato `.nekkora` 1.2.0 (G4).** Grava confirmações e substituições, com migração explícita a partir de 1.1.0.
7. **Reuso (G5).** Regra de correspondência confirmação × verificação atual.
8. **Sondas de laboratório (G6).** Entrada fictícia por **chave de catálogo** e clique de consulta, só em origem LAB. A interação continua sujeita ao contrato de privacidade (§7).
9. **Contexto read-only para o operador Claude (G7).** Exporta plano, resultados e decisões; nunca o gabarito do laboratório.
10. **Laboratório do caso.**
    - Páginas com variantes: id/classe mudados, reordenação, re-render, duplicados, iframe simples/aninhado/ambíguo, shadow aberto/fechado, oculto/desabilitado, remoção temporária, navegação.
    - Reset verificável.
    - Oráculo independente no servidor: registra o que foi consultado, sem ler a página.
11. **Caso Python/Playwright (G9).** Gerado a partir do plano congelado.

## 4. Menor desenho vertical

```
Studio (Swing)                         Host nativo (Java)                  Extensão (MV3)
──────────────                         ──────────────────                  ──────────────
GuidedDebugPresenter ── studio.debug.* ──► StudioLink (lista explícita +    studio-debug.js
  └ janela flutuante                    │  schema + DebugPrivacy)  ──────► ├ reusa inspection.js (captura,
DebugPlan (projeção)                    │                                  │   target.resolve, highlight)
TargetConfirmation / Verification       ◄── resultado em códigos ─────────── ├ reusa inspector.js (seleção)
  (domínio, .nekkora 1.2.0)                                                └ reusa highlighter.js (rosa)
```

- **Inspecionar a etapa N.**
  1. O Studio envia `targetId` (se houver) e o descritor compacto da etapa.
  2. A extensão captura ou sincroniza a aba e pede ao host a resolução de debug: registro, ou descritor se não houver.
  3. A extensão destaca **só** com RESOLVED e devolve o status, os códigos e a geometria relativa da caixa (para a janela não cobrir o campo).
- **Continuar.** Registra `TargetConfirmation`, com ator e base de evidência. Não executa nada.
- **Corrigir → Selecionar no navegador.**
  1. Liga o inspetor na aba.
  2. O clique é interceptado e devolvido como referência compacta nova, pelas mesmas barreiras de uma etapa gravada.
  3. O Studio compara antiga × nova (tipo, papel, compatibilidade com a ação, contexto) e pede confirmação do ator.
  4. Uma operação atômica troca o alvo efetivo.
- **Reuso.** Na segunda passagem, a confirmação vale sem perguntar de novo quando:
  - a verificação atual é RESOLVED na mesma fonte de identidade;
  - o contexto é compatível (origem, modelo de URL, cadeia de frames);
  - ação e parâmetros não mudaram desde a confirmação.

  Qualquer outra situação pede revisão.

## 5. Contratos propostos (a confirmar nos gates seguintes)

| Contrato | Natureza | Motivo |
|---|---|---|
| `nekkora-bridge/1` + capacidade `guided-debug/1` | acréscimo ao `studio-bridge.schema.json`, com fixtures válidas e inválidas; Studio antigo e extensão nova (e vice-versa) degradam por capacidade | evita protocolo paralelo |
| `nekkora-studio/guided-debug-plan/1.0.0` | JSON determinístico do plano (G1), sem valores | entrada do debug, do operador Claude e do gerador Python |
| `nekkora-studio/guided-debug-record/1.0.0` | exportação das verificações e decisões de uma passagem (ator, estado, timestamps, códigos, `requestId`) | evidência e contexto read-only. **Uma** forma para confirmação e verificação, em vez dos dois schemas sugeridos (`target-confirmation` e resultado de verificação): os dois são lidos juntos e versionados juntos |
| `nekkora-project` 1.2.0 | formato do projeto com confirmações e substituições de alvo | 1.0.0 e 1.1.0 ficam congelados |

A matriz de reason codes, timeouts, limites e idempotência será fixada no G1/G2 com fixtures.

## 6. Ator e autoridade

- O domínio ganha `DecisionActor` ∈ {`DEVELOPER`, `TEST_AGENT`}. **Não** existe `HUMAN_CONFIRMED`, `USER_CONFIRMED` nem `AI_VERIFIED` para esta decisão.
- `TEST_AGENT` é atribuído quando o Studio roda em **modo de laboratório**, configuração explícita de inicialização usada só pelos roteiros de homologação. A interface mostra "Agente de teste (laboratório)" em toda decisão assim registrada.
- No produto, Continuar registra `DEVELOPER`: "confirmado na interface do Studio por quem a operava". O Studio não prova que foi uma pessoa; o relatório declara isso.
- Uma decisão `TEST_AGENT` nunca sobrescreve uma decisão `DEVELOPER`.
- O plano exporta o ator. O gerador Python e o reuso mostram quando a base é só `TEST_AGENT`.

## 7. Privacidade

- **Nada novo sai da página fora da `privacy/2`.**
  - A referência selecionada no navegador passa pelas barreiras de uma etapa gravada.
  - O resultado do debug sai em códigos; as frases do resolvedor ficam no host, que já existia, sem logs crus novos.
- **Rótulo no overlay.** O texto do destaque é desenhado **dentro** da própria página, pelo content script, e não atravessa limite. Mesmo assim, usaremos só códigos e o tipo do elemento.
- **Entradas da sonda.**
  - O Studio envia uma **chave** de catálogo sintético do laboratório (ex.: `lab:registro-001`), nunca o valor.
  - A extensão só a resolve em origem LAB, a partir de um catálogo empacotado.
  - Em outra origem: `PROBE_NOT_ALLOWED`.
- **Valores no Python.** Vêm de um arquivo de dados fictícios do caso, fora do `.nekkora` e dos logs.

## 8. Riscos

| Risco | Mitigação |
|---|---|
| A redução de capacidade da `privacy/2` (reidentificação lógica 355 → 178) gera muitos `AMBIGUOUS`/`INSUFFICIENT_EVIDENCE` | é o resultado honesto: o laboratório terá alvos discrimináveis por atributos estruturais e casos que precisam de correção |
| Descritor mais pobre que a assinatura | o resultado declara a fonte; o reuso por descritor é restrito |
| Mudança do isolamento por dono (§2, item 1) | só na sessão de debug, somente leitura, com testes de não regressão do isolamento |
| Janela tapando o alvo | geometria da caixa devolvida em coordenadas de tela aproximadas; a janela se reposiciona |
| Clique real do Robot no modo de seleção | o inspetor intercepta na captura; o oráculo do laboratório confere que **nenhuma** consulta foi feita |
| Volume da missão | gates com commits pequenos e evidências por gate; regressões sequenciais (lição da CHROME-SECURITY-001: memória) |
| Playwright Python ausente | instalação isolada em venv no G9, com versão fixada; navegadores já presentes em `ms-playwright` |

## 9. Passagem do G0

Não há incompatibilidade material de privacidade nem de contrato. As mudanças são aditivas:
- capacidade `guided-debug/1`;
- formato 1.2.0;
- resolução de debug somente leitura.

O trabalho segue para o G1. As decisões D-S009-01 a 08 estão em `decisions.md`.
