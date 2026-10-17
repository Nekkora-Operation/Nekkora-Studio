# STUDIO-010 — Plano de testes (missão × onde foi provado)

Legenda: **U** = teste de unidade/contrato; **IT** = integração no Chrome for Testing real
(`Studio010SafeCaptureIT`, gesto pelo teclado do sistema); **H** = homologação real integrada
(`Studio010Homologation`, Studio + Chrome + host + Robot; ver [homologation.md](homologation.md)).

| Requisito da missão | Provas |
|---|---|
| Sessões → seções; etapas consecutivas → blocos; ordem original | U `PddDraftTest.agrupamento`, `fronteiras`; H P1, P6 |
| Fronteira de tela (troca de tela, navegação, tela desconhecida) e juntar através de tela recusado | U `fronteiras`, `dividirJuntar` |
| Instruções determinísticas sem IA; nome só pela `privacy/2`; senão neutro + revisar | U `instrucoes`, `nomeForaDaPolitica`; H P1 ("Autenticação" fora do vocabulário → revisão) |
| Texto revisado com dado recusado | U `textoRevisado`; H P2 |
| Revisor = DEVELOPER/TEST_AGENT, nunca "humano" | U `textoRevisado`; H P2 (estado "Revisado (agente de teste)") |
| Dividir / juntar / desfazer / refazer | U `dividirJuntar`; `PddUiTest`; H P2 |
| Pendências bloqueiam a exportação | U `pendencias`; `PddUiTest`; H P1/P2 |
| Rascunho determinístico, sem mutar a automação | U `determinismoENaoMutacao`, `revisaoDoProcesso`; H P5 (revisão da automação igual) |
| Leitura estrita do rascunho | U `leituraEstrita` |
| Rastreabilidade bloco ↔ etapas | U `rastreabilidade` |
| Captura só LAB, só com armação + gesto, uso único | U `studio-pdd.test.js` (armar, gesto sem armação, uso único); IT G4-05; H P4 |
| Identidade da aba na captura (revisão R1): troca entre o gesto e a captura, durante preparação demorada, durante a captura, ida e volta, 2ª aba do laboratório, aba movida, cancelar em andamento | U `studio-pdd.test.js` (7 testes R1); IT R1-01..05 (abas na mesma janela do Chrome real) |
| PDFBox 3.0.8 (revisão R2): exportação, paginação, acentos, fontes, imagem fiel, metadados, versões | U `PddPdfTest` (6), incluindo `caracteresEImagemFiel`; homologação real repetida |
| Aba errada / vencida / cancelada / página mudou / navegação | U `studio-pdd.test.js`; IT G4-04, G4-05; H P4 (TAB_MISMATCH e cancelar) |
| Sanitização por lista de permissão; campo nunca recebe pixel bruto | U `visual-sanitizer` (3 testes); IT G4-01, G4-02 (sentinelas por classe: pseudo-elemento, fundo, imagem, SVG, canvas, tabela, toast, iframes, shadow, campos), G4-03 (modal) |
| Verificação de pixels em 4 barreiras | U `studio-pdd.test.js` (SW), `PddVisualPrivacyTest` (6, host), `VisualAndStoreTest.quartaBarreira` (Studio), `PddPdfTest.recusas` (exportação com imagem adulterada) |
| Marcadores pelo resolvedor, normalizados; omitidos com motivo | U `studio-pdd.test.js` (PLACED só do alvo comprovado); IT G4-01, G5-01 (150%), G5-02 (página rolada; alvos fora da área visível → `TARGET_OUTSIDE_VIEWPORT`); H P3 (login 3/3, consulta 2/2, resultado 0/1 = etapa declarada sem alvo) |
| ≥ 2 imagens reais, diferentes, sanitizadas | IT G4-01; H P3 (3 imagens distintas, 0 pixel-sentinela) |
| Prévia = PDF real | `PddUiTest`; H P3/P8 (capturas da janela) |
| PDF: texto pesquisável, ordem, sem dados internos, metadados controlados | U `PddPdfTest.pdf`, `variasPaginas` (várias páginas, bloco que continua, rodapé n de N); H P6 (13 sentinelas ausentes, metadados, 3 imagens, páginas renderizadas sem cor-sentinela) |
| Versões sem sobrescrita silenciosa; manifesto sem textos | U `PddPdfTest.versoes`; H P5 (v001 intacto depois do v002), P6 |
| PDF/rascunho desatualizados quando a automação muda | U `PddPdfTest.desatualizado`; `PddUiTest`; H P7 |
| Armazenamento só com nomes técnicos | U `VisualAndStoreTest.armazenamento`; H P9 |
| Privacidade dos artefatos (rascunho, manifesto, logs, evidências) | H P9 (inclui varredura de todas as PNG da rodada) |
| 150% | IT G5-01 (zoom do Chrome) |
| 1366×768, temas | H P8 (janela no tamanho que o `SwingPddHost` usaria; 4 temas) |
| Contrato do Bridge | `BridgeContractTest` (prefixo `studio.pdd.`), validação do esquema em `studio-pdd.test.js` e no host |
| Regressões | Studio Windows e Linux, extensão Java/Node/Chrome: ver o relatório final |

Não executado: escala do **Windows** a 150% (só zoom do Chrome); tela física 1366×768; Linux com Chrome real
(captura homologada só no Windows).
