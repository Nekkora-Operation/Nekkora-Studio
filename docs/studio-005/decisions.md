# STUDIO-005 — Decisões

| Decisão | Enunciado | Como foi cumprida | Prova |
|---|---|---|---|
| D-S005-01 | Evidências representam acontecimentos observados, não resultados presumidos | `ObservedEvidence` registra o que a página mostrou (tipo, sinais, momento real); nenhum tipo diz sucesso ou falha | `EvidenceDomainTest.s005064…`, fixture inválida `s005-evidence-business-outcome` |
| D-S005-02 | A Chrome Extension continua responsável pela observação das páginas | observador e regras vivem na extensão (`feedback-observer`, `feedback-rules`); o Studio só recebe eventos | arquitetura |
| D-S005-03 | O Studio não executa JavaScript arbitrário para investigar elementos | o Studio só lê `studio.recording.evidence`; não existe comando de inspeção | `BridgeProtocol` sem comando novo de leitura |
| D-S005-04 | Evidências serão estruturadas e sanitizadas | vocabulário fechado e quatro barreiras de privacidade | `RecordedEvidence` (leitura estrita), `privacy.md` |
| D-S005-05 | O resultado de negócio permanece separado da observação | `ObservationSummary.businessOutcome()` é sempre `NOT_VALIDATED`, inclusive com mensagem de erro ou "sucesso" no texto | `s005034…`, homologação (cenários 1, 2 e 5) |
| D-S005-06 | Evidências devem possuir procedência e correlação | `evidenceId`, sequência, `recordingId`, `eventId`, fonte, relação, confiança e documento | `EvidenceInbox`, `Automation.recordedStep` |
| D-S005-07 | Eventos posteriores podem enriquecer etapas já confirmadas | `EvidenceIntake.attachEvidence` acrescenta à etapa existente, sem recriá-la | `s005027E033…`, mensagem tardia |
| D-S005-08 | Atualizações posteriores devem ser idempotentes | mesmo `evidenceId` não muda nada (domínio e Recorder) | `s005024E026…`, reconexão |
| D-S005-09 | Evidências originais não podem ser modificadas pelo editor manual | lista só de leitura, fora do estado editável; nenhuma operação de edição as toca | `s005031E032…`, aba Evidências só de leitura |
| D-S005-10 | Cobertura parcial e ausência de evidência devem ser explícitas | estados do resumo (indisponível, nada observado ≠ sem erro, incompleta, não se aplica) e limitações | `s005034ResumoDistingueAsAusencias` |
| D-S005-11 | Nenhum texto sensível deverá atravessar o Bridge | política de mensagem na extensão e no host; 4ª barreira no Studio | IT e homologação de dados sensíveis |
| D-S005-12 | O sistema não deverá armazenar PageMaps completos por etapa | não existe campo para isso; a fixture inválida prova a recusa | `s005-evidence-with-pagemap` |
| D-S005-13 | A observação não poderá alterar o comportamento da aplicação web | sem eventos disparados, sem `reportValidity`, sem escrita na página | homologação (formulário não enviado pela observação) |
| D-S005-14 | A extensão continua independente do Studio | sem `evidence/1`, nada muda; as suítes existentes da extensão passam | 224/224 no navegador |
| D-S005-15 | O enriquecimento será negociado por capacidade | `evidence/1` (exige `steps/1`), com recuo para `steps/1` e depois para gravação técnica | `RecorderEvidenceTest.s005005…` |
| D-S005-16 | A STUDIO-005 não executa nem valida automações | nada de execução, nem de validação | escopo e revisão do código |

## Decisões de implementação tomadas nesta missão

| Id | Decisão | Motivo |
|---|---|---|
| I-S005-01 | Evidências ficam **fora** do `AutomationStep.State` e da revisão | desfazer/refazer não apaga nem reaplica, e a chegada não invalida o histórico |
| I-S005-02 | Evidência ambígua fica na **automação** (não atribuída) | não escolher etapa arbitrária |
| I-S005-03 | Evento de aplicação próprio (`StudioChange.EVIDENCE`) | não confundir com edição de conteúdo nem disparar o histórico |
| I-S005-04 | Aba "Evidências" como **última** aba dos detalhes | preservar as posições das abas existentes (atalhos e homologação S004) |
| I-S005-05 | Espaçamento horizontal menor nas abas dos detalhes | com 6 abas a faixa rolava e escondia "Geral"; títulos e ordem iguais |
| I-S005-06 | `NAVIGATION_OCCURRED` dividido em `ROUTE_CHANGED`/`DOCUMENT_CHANGED`; `OBSERVATION_COMPLETE` acrescentado | regras de produção diferentes; "nada observado" ≠ "indisponível" |
| I-S005-07 | Duplicar não copia evidências; mover preserva | a cópia não é interação observada; mover não muda a identidade |
