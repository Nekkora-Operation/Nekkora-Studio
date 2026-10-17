# NEKKORA-STUDIO-010 — Relatório final

**Visual PDD Generator.**

| Repositório | Branch | Base |
|---|---|---|
| Studio | `feat/nekkora-studio-010` | `main` em `1f7dbac` |
| Extensão | `feat/nekkora-studio-010-safe-capture` | `main` em `15112b6` |

PRs separados, abertos **sem merge** (o merge é da usuária), extensão primeiro.

**Resumo.** O Studio gera um **PDD visual local** a partir do modelo:
- uma seção por sessão; um bloco por grupo de etapas consecutivas da mesma tela;
- títulos e instruções numeradas por modelos determinísticos (sem IA), revisáveis, com pendências que bloqueiam a exportação;
- dividir, juntar, desfazer e refazer sem tocar na automação;
- cada bloco recebe uma captura **real** do Chrome, **sanitizada na fronteira** (política `nekkora-visual/1`, lista de permissão: só rótulos de interface; campos viram molduras sintéticas; o resto é opaco) e verificada pixel a pixel em quatro pontos;
- marcadores 1/2/3 pelo resolvedor de identidade da STUDIO-009;
- a prévia é o próprio PDF; a exportação gera `pdd-visual-v001.pdf`, `v002`… sem sobrescrever, com manifesto sem textos e detecção de PDF desatualizado.

**Restrições respeitadas:**
- captura só em origens de laboratório, com dados sintéticos, e só com o gesto do usuário no Chrome (Alt+Shift+K) depois da armação pelo Studio;
- nenhuma imagem bruta gravada ou enviada (nem em evidência de teste);
- nenhum canal paralelo ao Bridge;
- uma permissão nova justificada (`activeTab`) e um atalho;
- `nekkora-privacy/2`, Recorder, contratos da 009 e `.nekkora` 1.0/1.1/1.2 intactos;
- operador sempre `TEST_AGENT`;
- sem merge, push na `main` ou release.

## 1. Baseline

| Repositório | Resultado (G0) |
|---|---|
| Studio `1f7dbac` | Windows 560/560; Linux 560 (559 + 1 pulado); golden 4/4 |
| Extensão `15112b6` | Java 284; Node 161; Chrome 262 |

## 2. Gates

| Gate | Entrega | Evidência |
|---|---|---|
| G0 | auditoria; experimentos no Chrome real (permissão de captura, `activeTab` + atalho, persistência da concessão, `OffscreenCanvas`); proposta de captura segura | [g0-audit.md](../docs/studio-010/g0-audit.md) |
| G1 | módulo `nekkora-studio-pdd`: `ProcessProjection`, `DocumentDraft` 1.0.0, leitura estrita | `PddDraftTest` (determinismo, não mutação, rastreabilidade, sem dados internos) |
| G2 | agrupamento conservador, dividir/juntar | `PddDraftTest.agrupamento`, `fronteiras`, `dividirJuntar` |
| G3 | instruções e títulos determinísticos; política de texto | `instrucoes`, `nomeForaDaPolitica`, `textoRevisado`, `pendencias` |
| G4 | captura sanitizada no laboratório real | `studio-pdd.test.js`, `PddVisualPrivacyTest`, `VisualAndStoreTest`, `Studio010SafeCaptureIT` G4-01..05 (7 testes com G5-01/02) |
| G5 | marcadores (100%, 150%, rolagem; omitidos com motivo) | IT G4-01, G5-01, G5-02 |
| G6 | janela de preparação (4 temas, desfazer/refazer, acessibilidade, mudanças detectadas) | `PddUiTest`; homologação P2, P7, P8 |
| G7 | PDF (PDFBox 3.0.5 + Inter) | `PddPdfTest.pdf`, `variasPaginas`; homologação P6 |
| G8 | versões, manifesto, desatualizado | `PddPdfTest.versoes`, `recusas`, `desatualizado`; homologação P5, P7 |
| G9 | homologação real integrada **55/55** | [homologation.md](../docs/studio-010/homologation.md) |
| G10 | documentação | [docs/studio-010](../docs/studio-010/README.md) |

## 3. Contratos

Todos novos e aditivos ([contracts.md](../docs/studio-010/contracts.md)):
- Bridge `pdd-capture/1`: `studio.pdd.capture.arm`/`cancel`, evento `studio.pdd.captured`, erro `VISUAL_CAPTURE_NOT_ALLOWED`;
- política visual `nekkora-visual/1`;
- rascunho `nekkora-studio/pdd-visual` 1.0.0 (armazenamento auxiliar, fora do `.nekkora`);
- manifesto `nekkora-studio/pdd-visual-manifest` 1.0.0.

Manifesto da extensão: `activeTab` + `commands.nk-pdd-capturar`.

## 4. Resultados por ambiente

| Ambiente | Baseline | Esta missão |
|---|---|---|
| Studio, Windows 11 (`./mvnw clean verify`) | 560/560 | **584/584** (581 + 3 golden; +20 PDD, +4 interface) |
| Studio, Linux (contêiner Temurin 21 fixado, `git archive HEAD`) | 560 (559 + 1 pulado) | **584 (583 + 1 pulado)** |
| Extensão, Java (core + host) | 284 | **290/290** (+6 `PddVisualPrivacyTest`) |
| Extensão, Node | 161 | **169/169** (+8 `studio-pdd.test.js`) |
| Extensão, Chrome for Testing real, grupos G1–G8 | 262 | **262/262** |
| `Studio010SafeCaptureIT` (Chrome real, gesto pelo teclado do sistema) | — | **7/7** |
| Homologação real integrada (G9) | — | **55/55** |

Na 1ª rodada, o teste de rolagem (G5-02) falhou na pré-condição: o limiar de rolagem exigido era maior que o máximo possível da página (969 px). O limiar foi corrigido e a classe passou inteira (7/7). A regressão Studio final foi feita no `HEAD` `1279b9e` (antes deste relatório, que é só documentação).

## 5. Defeitos encontrados e corrigidos durante a missão

No produto:
- **Prioridade dos avisos**: "PDF exportado" escondia "A automação mudou" (D-S010-14; `PddUiTest`).
- **Marcador sobre o controle vizinho**: o marcador 2 caía sobre o campo ao lado; agora testa quatro posições evitando regiões e outros marcadores.
- **Aspas no PDF**: o glifo de U+201D da Inter extraía outro caractere; o PDF usa `"` (D-S010-12).
- **Alt+Shift+P** não é atribuído pelo Chrome 155: atalho passou a Alt+Shift+K, e o Studio orienta quando o atalho está vazio (D-S010-11).

No laboratório e nos testes:
- o campo do laboratório transbordava sobre "Consultar" (o produto recusou o portador corretamente; CSS do laboratório corrigido);
- falso positivo do detector de sentinela em franjas de ClearType de link azul (detector estreitado + controle positivo na tela bruta em memória);
- no PDF renderizado, o rosa dos marcadores (#C2466D) caía na janela do detector (janela de razão estreitada para a homologação).

Na homologação (roteiro e evidências; detalhes em [homologation.md](../docs/studio-010/homologation.md#achados-durante-a-homologação)):
- duas capturas da janela do PDD tinham pixels do Chrome (janela coberta e borda invisível do Windows 11);
- **só no diretório temporário local, nunca em commit**;
- corrigido trazendo a janela à frente, recortando a área visível e varrendo **todas** as PNG da rodada.

## 6. Critérios de aceite (DoD da missão)

| Critério | Situação |
|---|---|
| PDD com ≥ 2 imagens reais, diferentes, sanitizadas | **cumprido**: 3 imagens reais (login, consulta, resultado) na homologação, 0 pixel-sentinela |
| Prévia | **cumprido**: a prévia é o PDF real rasterizado |
| PDF inspecionado e versionado | **cumprido**: v001/v002, texto extraído, metadados, renderização inspecionada visualmente |
| Contratos e testes (não mutação, rastreabilidade, independência, revisão detectável) | **cumprido**: `PddDraftTest`, homologação P5/P7 |
| Privacidade adversarial passa e caso inseguro é bloqueado | **cumprido**: sentinelas por classe (IT G4-02), modal, página mudando, origem não autorizada, imagem adulterada no disco, texto revisado com dado |
| Regressões reportadas | **cumprido** (§4, §9) |
| Documentação e relatório | **cumprido** |
| PRs sem merge, extensão primeiro | ver §9 |

## 7. Implementado × testado × não validado

| Item | Situação |
|---|---|
| Projeção, rascunho, agrupamento, textos, pendências | implementado; unidade + homologação real |
| Captura sanitizada + 4 barreiras | implementado; unidade + IT no Chrome real + homologação real |
| Janela de preparação, desfazer/refazer, prévia | implementado; teste de interface + homologação real (Robot) |
| PDF, versões, manifesto, desatualizado | implementado; unidade + homologação real |
| 150% | testado com **zoom do Chrome** (IT G5-01); escala do Windows **não validada** |
| 1366×768 | **simulado** pelo tamanho da janela numa tela 1920×1080; tela física **não validada** |
| Linux | módulo, interface e PDF na regressão Linux; captura no Chrome real **só no Windows** |
| Várias páginas | teste de unidade; homologação real com uma página |
| Origens reais / sites de clientes | **fora do escopo** (bloqueado por desenho) |

## 8. Pontos que pedem decisão da usuária

1. **Permissão `activeTab`** e o atalho `Alt+Shift+K` na extensão (D-S010-07, D-S010-11).
2. **PDFBox 3.0.5** (Apache-2.0) como dependência nova do Studio (D-S010-12).
3. **Ajustes em testes de missões anteriores** (D-S010-13): `StudioScopeTest` passa a permitir "PDF" só nos pacotes do PDD; a expectativa do menu Automação (STUDIO-007) inclui "Gerar documentação".

## 9. Regressão final e PRs

- Extensão: [PR #9](https://github.com/Nekkora-Operation/Nekkora-Extension-Chrome/pull/9) (`feat/nekkora-studio-010-safe-capture`), aberto **sem merge**.
- Studio: PR desta branch (`feat/nekkora-studio-010`), aberto **sem merge**, depois do da extensão.

Varredura de privacidade antes de cada push: toda PNG de **todos** os commits da branch (e do índice) passou pelo detector da cor-sentinela:
- Studio: 14 imagens, 0 ocorrência;
- extensão: 3 imagens, 0 ocorrência.

Nenhuma evidência regenerada de missões anteriores entrou em commit.
