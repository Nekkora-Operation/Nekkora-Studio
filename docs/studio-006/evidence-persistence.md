# STUDIO-006 — Persistência de evidências

As evidências da STUDIO-005 são **acontecimentos observados**. Salvar e abrir não os altera, não os recria e não os
reinterpreta.

## O que é gravado

Para cada evidência, todos os campos de `ObservedEvidence`: `evidenceId`, `sequence`, `recordingId`, `eventId`,
`observedAt`, `type`, `source`, `relation` (relação temporal), `confidence`, `classification`, `document` (quadro,
Shadow DOM, endereço já sanitizado), `region`, `message` (disponibilidade; texto sempre `null`), `field`, `state`,
`navigation`, `signals`, `candidates`, `signalsObserved`, `limitations`.

- Atribuídas a uma etapa: `evidence/<stepId>.json`, na ordem da etapa (ordem técnica, nunca pela hora do sistema).
- Não atribuídas (ambíguas): `evidence/unattributed.json`.

## Regras (§11) e como são garantidas

| Regra | Garantia | Prova |
|---|---|---|
| não gerar novos `EvidenceId` | identidade lida do arquivo | S006-028 (`ProjectRoundTripTest`), homologação cenário 3 |
| não duplicar | identidade única na automação; a reconstrução recusa repetição | S006-033, S006-050 |
| não modificar classificações | enums lidos tal como gravados | S006-027 |
| preservar a sequência técnica | lista na ordem gravada; a reconstrução confere a ordem | S006-029 |
| preservar limitações | campo `limitations` | S006-027 |
| preservar não atribuídas | documento próprio | S006-030 |
| respeitar os limites do domínio | máximo por etapa e de não atribuídas aplicados na leitura | `Doc.each` com os limites de `Automation` |
| evidência só em etapa gravada executada, com o `eventId` dela | regra da reconstrução | `EVIDENCE_NOT_APPLICABLE`, `EVIDENCE_STEP_MISMATCH` |

## Privacidade

`message.text` é sempre `null` no arquivo, com `availability` `SUPPRESSED` quando havia texto. Um arquivo com texto é
recusado (fixture `texto-da-pagina-em-evidencia`). O domínio descarta silenciosamente o que não aceita (região, campo,
endereço); no arquivo isso é tratado como conteúdo inválido, não "consertado". Abrir um projeto não consulta a página
de novo: persistir uma evidência não significa executá-la novamente.

## Resultado de negócio

`NOT_VALIDATED` é preservado (S006-031). O resumo da observação (`ObservationSummary`) não é gravado: é recalculado das
evidências a cada leitura, e o cálculo nunca transforma evidência em sucesso funcional. Homologação: a mensagem de erro
real do laboratório (evidência `VISIBLE_ERROR_MESSAGE`, `ERROR_INDICATOR`) continuou com "Não validado" e sem o texto
depois de salvar e reabrir.

## Evidências e alterações não salvas

A chegada de uma evidência não mexe na revisão do domínio (não entra no desfazer), mas **aumenta a geração de
persistência**: o projeto fica não salvo (S006-056/037). Ver [undo-redo.md](undo-redo.md).
