# STUDIO-005 — Modelo de evidência (domínio)

Pacote `com.nekkora.studio.domain.evidence`. Todos os tipos são **imutáveis** (records e enums).

## `ObservedEvidence`

| Campo | Significado | Invariante |
|---|---|---|
| `evidenceId` | identidade estável (deduplicação) | `[A-Za-z0-9._:-]{1,80}` |
| `sequence` | ordem técnica das evidências da gravação | ≥ 1 |
| `recordingId` | gravação técnica (interna, nunca exibida) | id válido |
| `eventId` | evento da etapa; **null** só na ambígua | ambígua ⇔ sem etapa |
| `observedAt` | instante real da observação | obrigatório |
| `type` | `EvidenceType` (14) | obrigatório |
| `source` / `relation` / `confidence` | fonte, AT/AFTER_ACTION, confiança sem percentual | ambígua ⇒ `AMBIGUOUS` |
| `classification` | erro/status/validação/sem classificação, ou null | — |
| `document` | `DocumentContext(frame, inShadow, title, url)` | título e URL sem dado sensível (senão `null`) |
| `region`, `field` | rótulos curtos | até 120, seguros para exibir (senão `null`) |
| `message` | `Message(availability, text)` | pelo modo estrito, na prática sempre `NONE` ou `SUPPRESSED` sem texto; o construtor só aceita texto com `AVAILABLE`, até 160, seguro |
| `state` | `StateChange(attribute, from, to)` | obrigatório em `ELEMENT_STATE_CHANGED` |
| `navigation` | `Navigation(newDocument, url)` | URL segura ou `null` |
| `signals`, `limitations` | vocabulários fechados | até 8 cada |
| `candidates`, `signalsObserved` | candidatas (ambígua) e sinais na janela (fim de observação) | 0–20 e 0–50 |

`Message.of(text)` aplica o **modo estrito**: havendo texto, `SUPPRESSED`; sem texto, `NONE`. O texto livre da página não
entra no domínio.

## Tipos (`EvidenceType`) e categorias

`FORM_SUBMITTED` (formulário) · `ROUTE_CHANGED`, `DOCUMENT_CHANGED` (navegação) · `VISIBLE_ALERT`,
`VISIBLE_ERROR_MESSAGE`, `VISIBLE_STATUS_MESSAGE` (mensagem) · `VALIDATION_FEEDBACK` (validação) ·
`LOADING_INDICATOR`, `LOADING_FINISHED` (carregamento) · `ELEMENT_STATE_CHANGED` (estado) · `CONTENT_CHANGED`
(conteúdo) · `OBSERVATION_COMPLETE`, `OBSERVATION_INCOMPLETE` (janela: não são sinais) · `AMBIGUOUS_FEEDBACK` (ambígua).

## Onde fica

- **Na etapa**: `AutomationStep.evidence()`, só leitura, em ordem de `sequence` (a chegada fora de ordem é reordenada).
  A lista está **fora** do `AutomationStep.State`: não entra nas fotografias do histórico nem na comparação de edição.
- **Na automação**: `Automation.unattributedEvidence()` guarda as ambíguas (até 500).
- **Limites**: 100 por etapa. Acima disso, `EVIDENCE_LIMIT_REACHED` é recusado e informado na confirmação.

## Ciclo de vida

| Operação | Efeito |
|---|---|
| `attachEvidence` | acrescenta (idempotente); não conta revisão |
| editar a etapa | não toca as evidências |
| mover a etapa | preserva (mesmo objeto) |
| duplicar | a cópia nasce **sem** evidências (não é interação observada) |
| excluir a etapa | as evidências saem com ela; desfazer a exclusão as traz de volta (mesmo objeto) |
| cancelar a gravação | `discardRecording` remove as etapas da gravação (e as evidências delas) e as não atribuídas dela |
