# STUDIO-006 — Modelo de persistência

## Auditoria (§2): o que é persistido e o que é estado temporário

| Objeto | Persistido? | Onde / por quê |
|---|---|---|
| `Automation` (id, nome, descrição, status, criação, atualização, `ModelVersion`, revisão) | sim | `automation.json` |
| `AutomationCatalog` | não | lista das automações abertas nesta sessão do Studio |
| `LogicalSession` (id, nome, descrição, notas, ordem das etapas) | sim | `sessions.json` |
| `AutomationStep` | sim | `steps/<id>.json` (original + efetivo) |
| `StepProvenance`, `RecordingObservation`, `CorrelationKeys` | sim | `original.provenance` |
| `CapturedBaseline` | sim | `original.baseline` |
| `ElementReference` capturado | sim | `original.capturedElement` (o efetivo = capturado + preferencial efetivo) |
| `StepContext`, resultado observado, `derivedFrom`, criação | sim | `original.*` |
| `ManualRevision` (instante por campo editado, última intervenção) | sim | `effective.fieldEdits`, `effective.lastManualChange` |
| `ManualLocator`, `StepComment`, `ActionParameters` | sim | `effective.*` |
| `ObservedEvidence` (atribuída e não atribuída) | sim | `evidence/*.json` |
| `ObservationSummary` | não | derivado das evidências e da procedência a cada leitura |
| `Automation.Snapshot`, `EditHistory` | **não** | desfazer/refazer é da sessão de edição (D-S006-09) |
| `StudioState` (seleção, abas, avisos), `StudioOperation` | não | estado de interface |
| `ProjectState` (arquivo, geração, erro) | não | estado de persistência desta sessão |
| `RecorderController`, `EvidenceInbox`, `StepIntake`, `EvidenceIntake` | não | gravação em andamento: salvar exige quiescência (§22) |
| `CapturePrivacy`, `DisplaySafety` | não | regras; a versão vai no manifesto (`privacyPolicy`) |

## DTOs próprios

O formato não serializa objetos Java. `ProjectCodec.documents(Automation)` percorre o agregado **na EDT** e produz
árvores JSON novas (`Map`/`List`/`String`/`Long`/`Boolean`), sem referência aos objetos do domínio. Essa árvore é o
`ProjectFiles.Snapshot` — equivalente ao `ProjectDocument`/`AutomationDocument`/`SessionDocument`/`StepDocument`/
`EvidenceDocument` sugeridos: cada documento JSON é um DTO com campos fixos, documentado no schema.

Na leitura, `ProjectCodec.read` interpreta os documentos com `Doc` (campos exatos e tipos) e produz os records de
reconstrução `AutomationRestoration.AutomationData`, `SessionData` e `StepData` — DTOs imutáveis do domínio, que só
viram agregado depois de `AutomationRestoration.restore` reaplicar os invariantes.

## Estado de persistência por automação (§20)

`ProjectState` (um por `AutomationId`, em `StudioState`):

| Campo | Significado |
|---|---|
| `path` | arquivo associado (vazio = nunca salvo) |
| `projectId` | identidade do projeto |
| `fingerprint` | tamanho + SHA-256 do arquivo na última leitura/gravação (detecção de alteração externa) |
| `generation` | contador de alterações que precisam ser salvas — **independente** de `Automation.revision()` |
| `savedGeneration` | a geração que está no arquivo |
| `recoveryGeneration` | a geração já fotografada para recuperação |
| `lastSavedAt`, `saving`, `lastError`, `recovered` | estado visual |

- `generation` sobe em toda alteração salvável: edições, chegada de etapa gravada **e chegada de evidência** (que não
  mexe na revisão do domínio, para não entrar no desfazer). `StudioState.markChanged` é chamado nesses pontos.
- Salvar fotografa a geração atual; quando a gravação termina, só **essa** geração vira `savedGeneration`. Uma
  alteração feita enquanto o worker grava mantém o projeto não salvo (S006-057).
- `dirty() = generation != savedGeneration`; o título da aba ganha ` *` e a barra de status mostra a situação.

Situações mostradas: `Salvo`, `Alterações não salvas`, `Salvando…`, `Erro ao salvar`, `Arquivo alterado externamente`,
`Recuperado — alterações não salvas`. "Recuperação disponível" é o diálogo oferecido ao abrir o Studio.
