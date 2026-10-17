# STUDIO-009 — Plano de testes e cobertura da matriz (§6 da missão)

Camadas:
- **Unidade/contrato:** Studio (Windows e Linux) e extensão (Java e Node).
- **Chrome real automatizado:** `Studio009GuidedDebugIT`, 11 cenários.
- **Homologação real no Windows:** `Studio009Homologation`, com Studio, Chrome for Testing, host instalado, mouse e teclado do sistema, e o Claude como TEST_AGENT. Detalhes em [homologation.md](homologation.md).

Legenda de onde cada item foi provado:
- **U:** unidade/contrato.
- **IT:** Chrome real automatizado.
- **H:** homologação real (Hn = G8, Pn = G9).
- **—:** não testado. Ver [known-limitations.md](known-limitations.md).

## Mapeamento e confirmação

| Item | Onde | Resultado |
|---|---|---|
| Alvo único correto proposto e confirmado | U `GuidedDebugUiTest.continuar`; H3 | ✔ |
| Proposta errada → Corrigir → selecionar → comparar → aplicar | U `corrigir`; H7, P8 | ✔ |
| Rejeição + segunda alternativa (descartar e escolher de novo) | H8 (incompatível → descartar → compatível) | ✔ |
| Cancelar seleção / voltar / fechar janela | U (cancelPick, previous, finish); H (Encerrar em todos os blocos) | ✔ |
| Reabrir projeto e reusar decisões | H5 (salvar 1.2.0 → fechar → abrir → REUSE ×2) | ✔ |
| Substituição incompatível recusada | U `incompativel`; H8 (botão para "preencher", dentro de iframe) | ✔ |
| Desfazer/refazer | U (`continuar`: Ctrl+Z desfaz a decisão); H9 (Ctrl+Z/Ctrl+Y da substituição) | ✔ |
| Salvar/recuperar, baseline imutável | U `ProjectFormat12ContractTest` e `TargetDecisionDomainTest`; H5/H7 (capturado intacto) | ✔ |
| Troca de sessão/automação durante o debug | — | não exercitado na homologação |

## Robustez (variantes do laboratório, H6/H13)

Invariante verificada em todas as variantes:
- comprovado (RESOLVED) **só** sobre o elemento certo, conferido pela caixa da extensão contra o retângulo do elemento esperado;
- indistinguível nunca comprovado;
- estado inadequado nunca reconfirmado;
- nenhuma interação com a página.

| Variante | Etapa 1 (campo) | Etapa 2 (botão) |
|---|---|---|
| `id-mudado` | RESOLVED pela identidade, revisão (atributos mudaram) | NOT_FOUND |
| `reordenado`, `rerender`, `tardio` | reconfirmado | reconfirmado (re-render: nó substituído, mesma identidade) |
| `errado` (#consultar no Limpar) | reconfirmado | RESOLVED sobre o **Consultar** real, revisão (IT G2-05; Python P5: IDENTITY_MISMATCH) |
| `oculto`, `desabilitado` | reconfirmado | comprovado, **Requer revisão** (D-S009-16) |
| `removido` (some e volta) | reconfirmado | NOT_FOUND → após voltar, reconfirmado |
| `duplicado` | RESOLVED (o campo do 1º formulário mantém o id gravado) | AMBIGUOUS, sem destaque sólido, sem sonda |
| `iframe`, `iframe-aninhado`, `shadow-aberto`, `shadow-fechado` | NOT_FOUND honesto (identidade gravada no documento principal) | idem |
| `iframe-ambiguo` | não comprovado | não comprovado |
| `rotulo-suprimido` | INSUFFICIENT_EVIDENCE, revisão | idem |
| Seleção dentro de iframe e de shadow aberto | IT G8-01; H13b | ✔ |
| Navegação/reload | IT G2-06, G8-02; H10 (época 1 → 3, sem roubar foco) | ✔ |
| Contexto indisponível / falha de captura | U Node "falha de captura" | ✔ |
| Seletor manual ruim | U `GuidedPlanTest` (manual não promove identidade) | ✔ |

## Integração e privacidade

| Item | Onde | Resultado |
|---|---|---|
| Sem permissão / origem não autorizada | IT G2-07, G6-02; P4 | ✔ |
| Revogação de permissão | U Node (`onPermissionsRemoved`); navegação para origem não permitida encerra (D-S009-20) | ✔ (unidade) |
| Desconexão Studio⇄extensão | U `canalPerdido`; H16 (desliga/religa no painel) | ✔ |
| Aba fechada | IT G8-02; H11 | ✔ |
| Timeout, resposta atrasada/duplicada, idempotência | U Node (`requestId`/`operationId`), sonda repetida não repete o clique (IT G6-01); Python P6 (TIMEOUT) | ✔ |
| Versão não suportada | U `indisponivel` (UNKNOWN_COMMAND → "Atualize a extensão"); IT G2-07 (capacidade desconhecida) | ✔ |
| Reinício da extensão | U Node ("reinício volta DEGRADED") | ✔ (unidade); — no Chrome real |
| Queda do Native Host (processo) | — | não exercitado |
| Nenhum valor em payloads/logs/contratos/relatórios/capturas | fixtures inválidas `s009-*` e 1.2.0; H15; G9 (varredura) ; máscara nas capturas (D-S009-22) | ✔ |
| Sem JS arbitrário | IT G2-07 (comando de execução recusado pelo host) | ✔ |
| Overlay não altera a página | IT G2-02 (zero eventos); H3 e H13 (contadores da página) | ✔ |

## UX

| Item | Onde | Resultado |
|---|---|---|
| Janela não cobre o alvo | H3, H14 (100% e 150%) | ✔ |
| Não rouba o foco | H10 | ✔ |
| Destaque visível | H3 (diagnóstico da extensão) | ✔ |
| Ações sempre alcançáveis com conteúdo grande | H6 (achado → D-S009-18), H14 | ✔ |
| 4 temas | H14 100% e 150% | ✔ |
| 150% | execução separada (`uiScale=1.5`), 11/11 | ✔ (escala da JVM; a escala do Windows não foi trocada) |
| 1366×768 | — | não exercitado (tela de 1920×1080 da máquina de homologação) |
| Captura e log correlacionados | log da homologação + `depuracao.jsonl` por StepId/instante | ✔ |

## Python final (G9)

As verificações P1 a P12 estão em [homologation.md](homologation.md):
- script e execução real;
- 2º debug correlacionado;
- oráculo;
- quebra deliberada, diagnóstico, reparo rastreável e nova execução;
- timeout, bloqueio de ação não autorizada e parada limpa;
- ambiente resetável (`/api/studio009/reset` antes de cada execução).

## Regressões finais

| Repositório | Resultado |
|---|---|
| Studio, Windows (`./mvnw verify`) | 560/560, golden 4/4 |
| Studio, Linux (`tools/golden-linux.sh verify`) | 560 (559 + 1 pulado), golden 4/4 |
| Extensão, Java | 284/284 |
| Extensão, Node | 161/161 |
| Extensão, Chrome real (grupos G1–G8) | 262 testes; `BrandedChromeIT` falhou uma vez por tempo (`session.synchronize` logo após mutação → `PENDING`) e passou ao repetir |
