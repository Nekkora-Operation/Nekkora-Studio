# S5 — Homologação funcional real

## 1. Studio pela interface: `CodegenQuality001Homologation`

Comando: `run_studio.sh int1`, que é o lançador da STUDIO-AGENT-COMPAT-001. Ele roda a classe de homologação com o Studio candidato (`f879af5`, classes do `clean verify` da S4), o Chrome for Testing com a extensão `Nekkora-Extension-Chrome` e diretórios de dados isolados. Usa só sites públicos (pt.wikipedia.org) e uma lista sintética de 5 artigos, sem credenciais.

Passos da jornada pedida e onde cada um foi coberto:

| Passo | Coberto por |
|---|---|
| 1. abrir o Studio | G1 |
| 2. criar o projeto de teste e gravar pela extensão | G1/G2 |
| 3. validar as etapas: PDD, V0–V3 | G3/G4 |
| 4–5. gerar o Python e o pacote completo | G4, "Desenvolver" |
| 6. conferir o manifesto e os hashes | V4/V5/VQ |
| 7. executar a automação em teste autorizado | G6 |
| 8. conferir o resultado de negócio | G6: planilha reaberta, conferida tipo a tipo; a pessoa viva fica com "Morte" vazia |
| 9–10. fechar e reabrir o projeto com as informações consistentes | o roteiro salva o `.nekkora`; `evidencias/Reabrir.java` reabre o projeto num processo novo (persistência real, mesmo diretório de dados) e confere estrutura, especificação, PDD e código regerado — **5/5** (`evidencias/s5-reabrir.txt`) |

**Reabertura (passos 9–10):** o projeto volta com 5 sessões e 10 etapas e o mesmo `automationId` do pacote. A especificação volta com as 11 decisões, inclusive a coluna opcional, e o PDD revisado também. O pipeline, a partir do projeto reaberto, gera os **10 arquivos idênticos** aos da interface.

**Resultado: 105 verificações, 0 falhas**, executado agora e não reaproveitado. Está em `evidencias/s5-studio-int1-verificacoes.txt`. Camadas: V1 PDD_READY 10/10, V2 13 capacidades, V3 lista → planilha, V4 5/5, V5 12 testes de contrato, VQ aprovada, V6 execução real com 5 linhas.

Pacote gerado (`biografias-python`): 9 arquivos declarados mais o manifesto. Os SHA-256 estão em `evidencias/s5-pacote-int1-sha256.txt`. O código é idêntico ao pacote homologado `lc1` (S2 §2).

## 2. Integração Studio → Agent → API

Laboratório descartável `agentlab-005`:
- sub-rede 10.251.51.0/24, porque a 50 continua com o `agentlab-004` preservado;
- API **`main` consolidada `e04a57c`** (já com o PR #36), migrations 001–028 em instalação limpa;
- ferramentas do Agent da **`main` `ce30397`** (com o PR #6), num worktree descartável;
- executável do Agent gerado do commit `7986e84`, com `src` idêntico ao da `main` (`2ab110d1…`).

Nenhuma alteração em ambiente persistente. A sub-rede 51 foi uma cópia local não versionada do compose.

**Preparação** (`ciclo.py preparar`, só rotas autenticadas; resultado em `evidencias/s5-ciclo-preparacao.json`):
- a pessoa autorizada criou o workspace, a automação, a versão DRAFT e enviou o ZIP;
- a API conferiu o pacote;
- com step-up, a pessoa publicou e autorizou o Worker;
- o SHA-256 do servidor é igual ao local (`4188dd70…`), e a impressão `/1` é `8d07bc39…`;
- canal antes da autorização: `0|0|0`; trilha de atos: 1,2,3,5,6;
- **nenhuma versão publicada sem o ato de publicação da API**, ou seja, nenhuma fixture SQL de publicação;
- as únicas fixtures, declaradas, são a identidade da pessoa e os limites da organização.

A primeira tentativa recebeu o 401 esporádico do TOTP, uma armadilha conhecida, e só deixou uma identidade órfã no banco descartável. A segunda passou.

**Jornada** (`jornada-ciclo.ps1`, Agent empacotado real, Studio fechado): **9/9 conferências OK**. Evidências em `evidencias/s5-jornada-20261030T024613Z/`.

| | Resultado |
|---|---|
| L1 | o pareamento trouxe a autorização da versão publicada com a impressão do pacote |
| L2 | o Agent validou o pacote (a impressão que ele calculou é igual à da API) e a execução ficou vinculada (`PLATFORM_RUN_LINKED`) |
| L3 | com o nginx pausado, a automação seguiu sem ser relançada |
| L4 | SUCCEEDED; 293 eventos guardados, 293 assentados e 0 recusas; consulta autenticada com versão, autorização, Worker, Agent e execução; supervisor SUCCEEDED/KNOWN; histórico **COMPLETE** |
| L5 | com Agent e automação encerrados à força, o resultado foi OUTCOME_UNKNOWN sem relançamento, também na Platform; só existem as 2 execuções iniciadas pela pessoa |
| L6 | nenhum segredo em evidências, dados do Agent ou logs |

**Resultado de negócio:** a planilha da execução tem 5 linhas e 6 colunas. A pessoa viva ficou com "Morte" vazia; nada foi inventado.

O laboratório `agentlab-005` foi parado com os volumes preservados.
