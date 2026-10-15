# STUDIO-005 — Privacidade (Studio)

As três primeiras barreiras estão na extensão: o content script, o service worker e o Native Host (ver
`Nekkora-Extension-Chrome/docs/studio-005/privacy.md`). O Studio é a **4ª barreira**, e não confia nas anteriores.

## Na leitura (`RecordedEvidence.parse`)

- Só os campos conhecidos do contrato são lidos. Um evento com campos extras (`html`, `value`, `pageMap`…) é
  **descartado** inteiro.
- Vocabulários fechados (tipo, fonte, relação, confiança, classificação, sinais, limitações, atributos de estado): o
  valor desconhecido descarta o evento.
- `SUPPRESSED`/`NONE` com texto descarta o evento (inconsistente).
- `AVAILABLE`: `Message.of(text)` exige até 160 caracteres, `DisplaySafety.isSafeToDisplay` (e-mail, CPF, CNPJ, cartão,
  JWT, Bearer e identificadores internos), nenhuma sequência de 4 ou mais dígitos e, desde a revisão do PR #6 da
  extensão, a regra **fail-closed pela forma**: palavra com letras e dígitos ("Kq7Segredo"), maiúscula interna
  ("JoanaPrado") ou nome próprio fora do início da frase ("Cliente Maria Souza"). É a mesma regra das outras três
  barreiras. Se falhar, vira `SUPPRESSED` com a limitação `MESSAGE_SUPPRESSED` (teste `s005047QuartaBarreira…` com um
  CPF que "escapou"; `EvidenceDomainTest.s005047…` com os casos da revisão).
- O Studio não conhece os valores digitados. A proteção contra o valor que a página repete depois de limpar ou remover
  o campo está na extensão (impressões digitais salgadas, ver o `privacy.md` da extensão).

## No domínio

- O construtor de `Message` recusa texto fora da política.
- Região, campo, título e URLs inseguros viram `null` (rótulos seguros ou nada).
- Nenhum identificador interno é exibido: `recordingId`, `eventId` e `evidenceId` ficam no domínio. O teste de interface e
  a homologação procuram `rec-…`, `ev-…`, `evd-…`, `route-…`, `tgt-…`, `cap-…` e `doc:` na tela e não encontram.

## Homologação

Os valores digitados (usuário, senha, nome, código) e os dados das mensagens (CPF, e-mail, JWT, HTML) **não aparecem**
no domínio, na tela do Studio, no `storage.session` da extensão nem nos logs do host (cenário 8, 8 verificações).

As capturas de tela da homologação mostram a **página de teste** com os dados fictícios que o Robot digitou nos campos
da própria página. Isso é a página, não o Studio. O produto não tira capturas de tela.
