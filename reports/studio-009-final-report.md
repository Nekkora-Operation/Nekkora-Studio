# NEKKORA-STUDIO-009 — Relatório final

**Guided Visual Debugger & AI-Validated Automation Proof.**

| Repositório | Branch | Base |
|---|---|---|
| Studio | `feat/nekkora-studio-009` | `main` em `a439a65` |
| Extensão | `feat/nekkora-studio-009-guided-debug` | `main` em `2c7c06b` |

PRs separados, abertos **sem merge** (o merge é da usuária).

**Resumo.** O Studio ganhou o **debug guiado no navegador**:
- verifica cada etapa gravada no Chrome sem executar nada;
- a extensão (única autoridade de identidade web) reencontra o elemento pela identidade registrada, destaca em rosa e devolve só códigos;
- a janela flutuante explica e permite Continuar (decisão com ator) ou Corrigir (escolher no navegador → comparar → aplicar, só o alvo muda);
- a passagem seguinte reconfirma sem pergunta o que continua comprovado.

As decisões vão para o `.nekkora` 1.2.0, e o contexto somente leitura vai para o registro `guided-debug-record/1.0.0`.

A prova vertical termina no **Python + Playwright por último**: gerado do plano congelado, executado de verdade e conferido pelo oráculo do laboratório. Ele quebra de propósito, é reparado no modelo e roda de novo.

**Restrições respeitadas:**
- sem merge, push na `main` ou release;
- só dados fictícios e origens locais;
- operador sempre `TEST_AGENT`, nunca confirmação humana;
- `nekkora-privacy/2` intocada;
- sem JavaScript arbitrário pelo Bridge;
- `.nekkora` 1.0.0/1.1.0 congelados (só a versão nova 1.2.0, com migração confirmada);
- Python só depois do debug e do modelo homologados.

## 1. Baseline

| Repositório | Resultado |
|---|---|
| Studio `a439a65` | Windows 525/525; Linux 524 + 1 pulado; golden 4/4 |
| Extensão `2c7c06b` | Java 271; Node 148; Chrome 251 |

## 2. Gates

| Gate | Entrega | Evidência |
|---|---|---|
| G0 | auditoria: inventário de reutilização, recuperação da identidade após reabrir (registro só em laboratório, `TARGET_NOT_OWNED` → resolução somente leitura + descritor), menor desenho vertical | [g0-audit.md](../docs/studio-009/g0-audit.md), `ebc7fa5` |
| G1 | plano `guided-debug-plan/1.0.0` (projeção determinística, sem GUI, sem valores, negativos) | `de048a8`, `GuidedPlanTest`/`GuidedPlanApiTest` |
| G2 | `guided-debug/1`: inspeção somente leitura, destaque, seleção interceptada; Studio→Bridge→Host→Extensão tipado e correlacionado | ext `c23f790` `5d19aea` `07f4ee7`, Studio `3db67a7`; IT G2-01..07 |
| G3 | janela flutuante (4 temas, 100/150%, não rouba o foco, não cobre o alvo), Ctrl+Shift+D | `6d077ca`; H3, H10, H14 |
| G4 | decisões (CONFIRMED/REJECTED, ator, base, expectativa), `replaceTarget` atômico, `.nekkora` 1.2.0 | `d7f2a98`; `TargetDecisionDomainTest`, `ProjectFormat12ContractTest`; H5, H7, H9 |
| G5 | reuso inteligente (`ReusePolicy`), inclusive depois de reabrir | `d7f2a98`; H4, H5 |
| G6 | sonda LAB (chave de catálogo sintético, idempotente, fatos separados) e oráculo | ext `180c80f`, Studio `8590fcd`; IT G6-01/02; H3 |
| G7 | registro `guided-debug-record/1.0.0` (só códigos, ator explícito) | `d6e6a86`; `GuidedDebugRecordTest` |
| G8 | homologação real 106/106 + 150% 11/11; regressões | `b602333`, `36c167b`, `ca18b36`, ext `6c2635a` e `3a9f966` (evidências sem valores); [homologation.md](../docs/studio-009/homologation.md) |
| G9 | Python + Playwright: 30/30 com oráculo, 2º debug, quebra e reparo no modelo | `fd3a6ef`; [python/](../docs/studio-009/python/README.md), `evidence/g9` |
| G10 | documentação, relatório, PRs sem merge | este relatório |

## 3. Contratos

Detalhes em [contracts.md](../docs/studio-009/contracts.md). Todas as mudanças de contrato são aditivas e versionadas, com schemas e fixtures válidas e inválidas:
- `nekkora-studio/guided-debug-plan/1.0.0`;
- `nekkora-bridge/1` + capacidade `guided-debug/1` (comandos `studio.debug.*`, eventos `picked`/`changed`, erros novos);
- `nekkora-nm/1` + `debug.resolve`/`debug.node.state`;
- `.nekkora` 1.2.0;
- `nekkora-studio/guided-debug-record/1.0.0`;
- interface do script Python do caso.

Compatibilidade:
- Studio novo + extensão antiga → `UNKNOWN_COMMAND` ("Atualize a extensão");
- capacidade desconhecida → `BAD_REQUEST`;
- 1.0/1.1 continuam lidos e gravados byte a byte quando não há decisões.

## 4. Resultados por ambiente

| Ambiente | Resultado |
|---|---|
| Studio, Windows 11 (`./mvnw verify`) | **560/560**, golden 4/4 |
| Studio, Linux (contêiner Temurin 21 fixado, `git archive HEAD`) | **560 (559 + 1 pulado)**, golden 4/4 |
| Extensão, Java | **284/284** |
| Extensão, Node | **161/161** |
| Extensão, Chrome for Testing real (8 grupos) | **262**; 1 falha intermitente de `BrandedChromeIT` (CHROME-002, tempo), passou ao repetir |
| Homologação real G8 | **106/106** (100%), **11/11** (150%) |
| Homologação real G9 (Python) | **30/30** |

Os números do Studio são da rodada anterior à reorganização dos commits. Depois disso, só mudaram:
- o hash de revisão do plano (`GuidedPlanJson`, com teste do módulo verde);
- o roteiro de homologação;
- documentação.

A regressão final completa foi refeita antes dos PRs (§9).

## 5. Defeitos encontrados e corrigidos durante a missão

Os da homologação real estão em [homologation.md](../docs/studio-009/homologation.md#achados-da-homologação-todos-corrigidos-e-cobertos-por-teste). Os de maior impacto:
- O **Bridge do Studio descartava todos os eventos do debug**. A seleção no navegador, a navegação e o fim de sessão nunca chegavam. Os testes de interface com navegador falso não viam isso: só a homologação real viu.
- A **navegação não avançava a época** sem captura (extensão).
- **Canal perdido** deixava Continuar habilitado sobre uma verificação antiga.
- O pacote **`domain/target` nunca tinha sido versionado**: o `.gitignore` `target/` também pegava o código. A regressão Linux, a partir do `HEAD`, acusou.
- A **revisão do plano mudava com as decisões**: o plano validado nunca casava com o registro.
- **Valor fictício visível em captura** (corrigido com máscara; evidências regeneradas).

Antes da homologação:
- etiqueta do destaque cobrindo a página (removida, D-S009-09);
- `devicePixelRatio` 1.0 perdendo a ida e volta;
- campo `notes` fora do schema no evento `picked`.

## 6. Critérios de aceite (§7 da missão)

| Critério | Situação |
|---|---|
| Python só no fim | ✔ G9 depois de G0–G8 homologados |
| Extensão como autoridade de identidade (sem duplicar o `TargetResolver`) | ✔ o Studio só pede e apresenta (D-S009-02) |
| Match único ≠ identidade | ✔ debug só propõe com RESOLVED; script confere tag/nome (P5) |
| Sem confirmação humana fictícia | ✔ `DecisionActor` ∈ {DEVELOPER, TEST_AGENT}; fixtures inválidas recusam `HUMAN_CONFIRMED`/`AI_VERIFIED` |
| Não reusar sem revalidar | ✔ `ReusePolicy` exige RESOLVED agora, mesma fonte, mesma expectativa, estado adequado |
| `NO_STATIC_BLOCKERS` ≠ sucesso | ✔ plano `STATIC_ONLY`; registro `NO_EXECUTION_NO_BUSINESS_OUTCOME`; script decide só pelo oráculo |
| Sem dados/credenciais reais | ✔ catálogo sintético; origens locais |
| `privacy/2` intocada | ✔ |
| Sem seleção automática silenciosa, correção sem comparação, mutação histórica | ✔ ambíguo nunca escolhido; comparar antes de aplicar; capturado intacto, desfazível |
| Sem merge/push na `main`/release | ✔ PRs abertos sem merge |
| Compatibilidade e negativos como gates | ✔ G8 (matriz em [test-plan.md](../docs/studio-009/test-plan.md)) |
| Distinguir produto × Claude × fixture × Chrome real | ✔ o gerador Python é declarado do caso (escrito pelo Claude); decisões rotuladas TEST_AGENT; resultados por ambiente separados |

## 7. Testes feitos e não feitos

Matriz completa em [test-plan.md](../docs/studio-009/test-plan.md). **Não exercitados:**
- 1366×768;
- escala do Windows (o 150% usou a escala da JVM);
- reinício da extensão e queda do Native Host no Chrome real (só unidade);
- troca de sessão/automação durante o debug;
- seleção em iframe de outra origem e em shadow fechado.

Ver [known-limitations.md](../docs/studio-009/known-limitations.md).

## 8. Pontos que pedem decisão da usuária

1. **D-S009-12:** fixtures "formato mais novo" do 1.0.0/1.1.0 passaram a 1.3.0 (o 1.2.0 virou real). É o mesmo tipo de ajuste autorizado na STUDIO-008.
2. **D-S009-15:** o teste da STUDIO-007 que fixa os itens do menu Automação agora inclui "Depurar no navegador…".
3. **L11 (resolvido):** uma primeira versão das capturas do G8 não tinha máscara. Os commits locais foram reorganizados antes do primeiro push, e a branch publicada só tem as capturas mascaradas.
4. **L1:** sósias indistinguíveis nunca comprovados (política da CHROME-SECURITY-001), mesmo após escolha explícita.

## 9. Regressão final e PRs

Regressão final do Studio em `630fc56`:
- Windows `clean verify` 560/560;
- Linux 560 (559 + 1 pulado);
- golden 4/4.

Depois disso, só mudaram:
- rótulos do roteiro de homologação (fonte de teste sem `@Test`);
- documentação;
- evidências regeneradas (G8 106/106, 150% 11/11, G9 30/30).

| Repositório | PR | Base | Estado |
|---|---|---|---|
| Extensão | [#8](https://github.com/Nekkora-Operation/Nekkora-Extension-Chrome/pull/8) | `main` | aberto, **sem merge**; fazer primeiro |
| Studio | [#12](https://github.com/Nekkora-Operation/Nekkora-Studio/pull/12) | `main` | aberto, **sem merge**; depois da extensão |

Os commits locais de ambas as branches foram reorganizados **antes** do primeiro push, para que nenhuma captura ou evidência com valor (nem fictício) entrasse no histórico publicado. O conteúdo final não mudou.
