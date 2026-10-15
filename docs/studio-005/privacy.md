# STUDIO-005 — Privacidade (Studio)

As três primeiras barreiras estão na extensão: o content script, o service worker e o Native Host (ver
`Nekkora-Extension-Chrome/docs/studio-005/privacy.md`). O Studio é a **4ª barreira**, e não confia nas anteriores.

## Na leitura (`RecordedEvidence.parse`)

- Só os campos conhecidos do contrato são lidos. Um evento com campos extras (`html`, `value`, `pageMap`…) é
  **descartado** inteiro.
- Vocabulários fechados (tipo, fonte, relação, confiança, classificação, sinais, limitações, atributos de estado): o
  valor desconhecido descarta o evento.
- `SUPPRESSED`/`NONE` com texto descarta o evento (inconsistente).
- **Modo estrito** (decisão da revisão final dos PRs #6): `AVAILABLE` com texto, se algum dia chegar (o contrato ainda o
  permite), vira `SUPPRESSED` + `MESSAGE_SUPPRESSED` em `Message.of`. O Studio **não recebe nem guarda texto livre de
  mensagens da página**, nem os inofensivos ("Usuário ou senha inválidos."), nem dado de terceiro em minúsculas
  ("o cliente joão já existe"). Testes: `RecorderEvidenceTest.s005047QuartaBarreira…` e `EvidenceDomainTest.s005047…`.
- A evidência continua completa no que importa: tipo, classificação, sinais, região (descrição controlada), documento, horário,
  confiança e etapa. A tela mostra "Texto omitido por privacidade".

## No domínio

- O construtor público de `Message` recusa `AVAILABLE` (P2 da revisão 3): nenhum caminho do domínio cria mensagem com
  texto livre, e `NONE`/`SUPPRESSED` não aceitam texto.
- **Metadados controlados (revisão 3):** região e campo só aceitam as descrições do vocabulário
  (`EvidenceVocabulary.REGION_LABELS`, `FIELD_LABELS` — as mesmas da extensão e do Native Host); qualquer outro valor,
  como `"mensagem de erro o cliente joão já existe"` ou `"telefone do cliente joão"`, vira `null`. O título do documento
  é sempre `null`. URLs inseguras viram `null`. Testes: `RecorderEvidenceTest.revFinalMetadadosComTextoDaPaginaNaoEntramNoStudio`
  e `EvidenceDomainTest`.
- Nenhum identificador interno é exibido: `recordingId`, `eventId` e `evidenceId` ficam no domínio. O teste de interface e
  a homologação procuram `rec-…`, `ev-…`, `evd-…`, `route-…`, `tgt-…`, `cap-…` e `doc:` na tela e não encontram.

## Homologação

Os valores digitados (usuário, senha, nome, código) e os dados das mensagens (CPF, e-mail, JWT, HTML) **não aparecem**
no domínio, na tela do Studio, no `storage.session` da extensão nem nos logs do host (cenário 8, 8 verificações).

As capturas de tela da homologação mostram a **página de teste** com os dados fictícios que o Robot digitou nos campos
da própria página. Isso é a página, não o Studio. O produto não tira capturas de tela.
