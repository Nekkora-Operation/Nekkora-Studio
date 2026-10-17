# STUDIO-010 — Privacidade

Princípio: o PDD descreve o **processo mapeado** com textos determinísticos e imagens construídas por lista de
permissão. Nada é declarado "seguro" por ausência de detecção; na dúvida, recusa (fail closed).

## O que nunca chega ao documento

| Item | Como é garantido | Provado em |
|---|---|---|
| Valores digitados, credenciais, apelidos | o PDD lê só fatos da projeção (ação, tipo e nome seguro do alvo); `ActionParameters` nunca é lido | `PddDraftTest.semDadosInternos`; G9 P6/P9 |
| Nomes de variáveis, seletores, chaves de correlação, ids técnicos | idem; ids de etapa ficam só no rascunho (rastreabilidade), nunca no PDF/manifesto | G9 P6 (13 sentinelas ausentes do texto do PDF) |
| Texto da página (títulos, tabelas, mensagens) | política visual `nekkora-visual/1`: fora dos portadores aprovados, pixels sintéticos | `studio-pdd.test.js`, `PddVisualPrivacyTest`, `VisualAndStoreTest`, IT G4-02/03; G9 P3/P6 (detector de cor-sentinela = 0) |
| Imagem bruta | sanitização dentro do service worker; bruta liberada no `finally`; nenhum caminho de gravação | revisão de código + IT (só PNG sanitizado chega ao host) |
| Texto livre do desenvolvedor (nomes de sessão/etapa, notas) | só entra se passar por `interfaceText`; senão texto neutro + "Revisar" (bloqueia a exportação) | P1 ("Autenticação" fora do vocabulário → revisão) |
| Texto revisado com dado | `PddText.problems`: dígitos, CPF/e-mail/cartão/token, URL, seletor, expressão de variável, identificador técnico → recusado | `PddDraftTest.textoRevisado`; G9 P2 ("Login de … 123" recusado) |
| Resultado de negócio | o PDF diz "não comprova resultado de negócio"; etapas declaradas aparecem como tal | G9 P6 |

## Revisor

Revisões guardam `DEVELOPER` ou `TEST_AGENT`. Nesta missão todas as revisões foram do **agente de teste**
(`-Dnekkora.studio.decisionActor=TEST_AGENT`); nada foi apresentado como confirmação humana.

## Armazenamento

`<dataDir>/pdd/<automationId>/` com nomes só técnicos (`rascunho.json`, `capturas/<sha>.png`). Nada no `.nekkora`,
no histórico de desfazer, na área de transferência, em logs ou telemetria. Logs do host e do Studio só com códigos.

## Escopo de captura

Só origens `LAB` explicitamente autorizadas (laboratório local, dados e páginas sintéticos). Nenhuma captura de sites
ou ERPs de clientes. Dados reais continuam **não autorizados** (CHROME-SECURITY-001 §25).

## Evidências desta missão

- Publicadas apenas: imagens já sanitizadas, PDFs exportados, páginas do PDF renderizadas e capturas da **janela do
  Studio** (à frente, só a área visível).
- A homologação passa um detector da cor-sentinela (#E6007E, usada em todo texto sensível do laboratório) em **toda**
  PNG da rodada; uma única ocorrência reprova (G9 P9).
- Achado durante a G9 (só no diretório temporário local, nunca em commit): uma captura da janela do PDD pegou pixels do
  Chrome que estava à frente, e outra pegou uma faixa da borda invisível da janela do Windows 11. O roteiro passou a
  trazer a janela à frente com um clique real, recortar a área visível e varrer todas as PNG. A rodada publicada é a
  posterior às correções.
- Antes do push: varredura das imagens no índice e no histórico da branch (ver relatório final).
