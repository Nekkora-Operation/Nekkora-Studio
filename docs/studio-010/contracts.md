# STUDIO-010 — Contratos

Nenhum contrato anterior mudou: `nekkora-privacy/2`, Recorder, `guided-debug/1` (STUDIO-009) e `.nekkora`
1.0/1.1/1.2 ficam como estavam. Tudo abaixo é **novo e aditivo**.

## 1. Bridge — `pdd-capture/1`

Esquema: `contracts/studio-bridge.schema.json` da extensão (cópia de teste em
`nekkora-studio-bridge/src/test/resources/contracts`).

| Mensagem | Direção | Conteúdo |
|---|---|---|
| `studio.pdd.capture.arm` | Studio → extensão | `captureId`, `tabRef` (`t-…`), `leaseMs` (5 000–120 000), `steps[1..32]`: `stepRef`, `targetId` (ou nulo), `descriptor` recuperável já persistido, `actionType`. Resposta `PddArmResult`: `armed`, `duplicate`, `shortcut` (atalho efetivo; vazio se removido) |
| `studio.pdd.capture.cancel` | Studio → extensão | `captureId`. Vale com a captura armada **ou em andamento** (depois do gesto, até a publicação). Resposta `PddCancelResult` |
| `studio.pdd.captured` | extensão → Studio (evento) | `CAPTURED`: `policy` = `nekkora-visual/1`, `observedAt`, `width`, `height`, `devicePixelRatio` (≥ 0,25), `png` (base64, já sanitizado), `sha256`, `regions` (tipo + retângulo em px do dispositivo), `annotations` (por `stepRef`: `PLACED` + retângulo normalizado, ou `OMITTED` + motivo), `limitations`. `FAILED`: só `captureId` + `code` |

Erro novo: `VISUAL_CAPTURE_NOT_ALLOWED` (origem fora do laboratório). Códigos de falha do evento:
`NOT_ARMED`, `ARM_EXPIRED`, `TAB_MISMATCH`, `TAB_CLOSED`, `PAGE_CHANGED`, `PAGE_CHANGED_DURING_CAPTURE`,
`GEOMETRY_MISMATCH`, `SANITIZATION_CHECK_FAILED`, `SANITIZED_IMAGE_TOO_LARGE`, `PERMISSION_REVOKED`, `CAPTURE_BUSY`,
`CAPTURE_DENIED`, `REGION_OUT_OF_VIEWPORT`, `CARRIER_TOO_LARGE`, `CARRIER_OVERLAPS_FIELD`, `TOO_MANY_REGIONS`,
`REGION_KIND_UNKNOWN`, `CONTENT_UNAVAILABLE`, `CANCELLED`, `ACTIVE_TAB_CHANGED` (revisão R1: a aba ativa mudou entre o
gesto e a publicação), `INTERNAL`. Motivos de anotação omitida: `NOT_FOUND`,
`AMBIGUOUS`, `IDENTITY_MISMATCH`, `STALE_TARGET`, `SCOPE_UNAVAILABLE`, `INSUFFICIENT_EVIDENCE`, `CONTEXT_UNAVAILABLE`,
`NODE_NOT_CONNECTED`, `TARGET_OUTSIDE_VIEWPORT`, `TARGET_IN_FRAME`, `NO_TARGET`, `RESOLUTION_FAILED`.

O evento sempre passa pelo host (`PddVisualPrivacy`): não existe canal paralelo ao Bridge.

## 2. Extensão — manifesto

- Permissão nova: `activeTab` (justificativa: G0 E1/E2 — `captureVisibleTab` exige `<all_urls>` ou `activeTab`).
- `commands.nk-pdd-capturar`, sugerido `Alt+Shift+K` (D-S010-11).
- Nenhuma permissão de host nova.

## 3. Rascunho — `nekkora-studio/pdd-visual` 1.0.0

Arquivo `<dataDir>/pdd/<automationId>/rascunho.json` (exemplo real: [evidence/g9/S010-rascunho.json](evidence/g9/S010-rascunho.json)).

| Campo | Conteúdo |
|---|---|
| `schema`, `version`, `automationId`, `processRevision` | identificação; `processRevision` = sha-256 dos fatos documentados (D-S010-03) |
| `sections[]` | `sessionId`, `number`, `title` (`DocText`), `blocks[]` |
| `blocks[]` | `blockId` (`blk-` + 16 hex), `stepIds`, `title`, `instructions[]` (`stepId`, `ordinal`, `kind` OBSERVED/DECLARED, `text`), `screenKey` (opaca), `capture` (sha-256 ou vazio) |
| `DocText` | `value`, `state` (`DERIVED`, `NEEDS_REVIEW`, `REVIEWED`), `reviewer` (`DEVELOPER`, `TEST_AGENT` ou vazio) |
| `captures{sha}` | `width`, `height`, `devicePixelRatio`, `observedAt`, `pageEpoch`, `policy`, `screenKey`, `forSteps`, `regions` (tipo + retângulo), `annotations`, `limitations` |

Leitura estrita: campo desconhecido, versão diferente ou valor fora do domínio = arquivo recusado (o Studio recomeça o
rascunho a partir da automação, sem apagar o arquivo). Imagens em `capturas/<sha>.png`, sempre já sanitizadas.

## 4. Exportação

- `pdd-visual-vNNN.pdf` (v001, v002…): nunca sobrescreve; temporário na mesma pasta → reabrir e verificar → movimento
  atômico **sem** substituição; colisão = próxima versão.
- `pdd-visual-vNNN.manifest.json` — `nekkora-studio/pdd-visual-manifest` 1.0.0: `documentVersion`, `exportedAt`,
  `status`, `processRevision`, `documentRevision`, `pages`, `sections`, `blocks`, `captures` (sha-256),
  `limitations`, `visualPolicy`, `exclusions`. **Sem textos.**
- PDF: metadados título "PDD visual", produtor/criador "Nekkora Studio", sem autor, assunto, palavras-chave nem XMP.
