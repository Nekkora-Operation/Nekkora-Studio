# STUDIO-009 — Privacidade

A política `nekkora-privacy/2` **não mudou**. O debug guiado passa pelas mesmas barreiras de uma etapa gravada, e
acrescenta as suas.

## 1. O que nunca sai do navegador

| Dado | Onde é barrado |
|---|---|
| Valor digitado ou sondado | não existe campo no contrato; a sonda LAB recebe só a **chave** do catálogo sintético (`lab:registro-ativo`…), e o valor é resolvido dentro da extensão |
| Texto da página, frases do resolvedor | service worker (códigos de vocabulário fechado), host `DebugPrivacy`, schema estrito, 4ª barreira no `GuidedDebugClient` |
| Nomes fora do vocabulário | substituídos pelo vocabulário (ex.: rótulos com nome de pessoa fictícia na variante `rotulo-suprimido` → limitação declarada, nunca o nome) |
| PageMap, `nodeId`, URL completa | só modelo de URL sanitizado; `nodeId` vira `candidateRef` válido só na época atual |
| JavaScript arbitrário | não há comando genérico; `UNKNOWN_COMMAND` para o que não está no contrato |

## 2. O que o Studio guarda

- `.nekkora` 1.2.0: decisão (ator, base **em códigos**, expectativa) e substituto com a mesma forma do elemento
  capturado (passou pela política de captura). Fixtures inválidas provam a recusa de frase livre, texto da página,
  URL completa e atores humanos/IA.
- Plano e registro do debug: só códigos, `exclusions` explícitas.

## 3. Operador

Decisões do Claude são `TEST_AGENT`, ativadas só em modo de laboratório explícito
(`-Dnekkora.studio.decisionActor=TEST_AGENT`), visíveis na janela ("Decisões registradas como: Agente de teste
(laboratório)"). `TEST_AGENT` nunca sobrescreve uma decisão do `DEVELOPER` e seu reuso não vale para um desenvolvedor.
Nenhum rótulo de confirmação humana existe no código, nos contratos ou nos arquivos.

## 4. Evidências e capturas

- Dados só fictícios (catálogo sintético do laboratório, usado por chave: `lab:registro-ativo`…), origens locais (`127.0.0.1:18765`).
- **Nenhum valor digitado em captura, nem fictício** (D-S009-22): as capturas da homologação cobrem os campos editáveis
  da página (documento, iframes da mesma origem, shadow aberto); o 2º debug do Python usa `mask` do Playwright.
- Varredura automática (H15 e G9): projeto `.nekkora`, registro, plano, logs da homologação, logs e dados do host, dados
  do Studio, scripts, resultados e depuração do Python — sem o valor digitado/sondado e sem o nome fictício do rótulo
  suprimido.
- Proteção da máquina (achado do G8): antes de cada clique, tecla ou captura, o roteiro confere que o primeiro plano é
  do Studio ou do Chrome de teste. Outro programa na frente interrompe sem clicar, teclar ou capturar. Uma rodada
  interrompida por um mensageiro na frente teve **todas** as capturas apagadas; nenhuma foi versionada.

## 5. Python

Entradas só por variável de ambiente da execução (`NK_ENTRADA_REGISTRO_CODIGO`), nunca em argumentos, logs, resultado,
depuração ou script. Só a origem do laboratório é aceita; outra origem é recusada antes de conectar.
