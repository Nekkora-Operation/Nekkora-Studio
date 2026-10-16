# STUDIO-006 — Formato do projeto `.nekkora` (1.0.0)

Contrato versionado: [`nekkora-project.schema.json`](../../nekkora-studio-persistence/src/test/resources/contracts/nekkora-project/1.0.0/nekkora-project.schema.json)
(JSON Schema 2020-12), fixtures válidas congeladas e 18 fixtures inválidas na mesma pasta. O leitor do Studio é a
autoridade: o schema descreve a estrutura; as regras semânticas estão abaixo e em [hydration.md](hydration.md).

## Contêiner

- ZIP (deflate ou store), extensão `.nekkora`. Entradas em ordem fixa e com data fixa (1980-01-01): o mesmo conteúdo
  produz os mesmos bytes, sem relógio local no contêiner.
- **Não é criptografado** e **não é assinado**. É legível por qualquer ferramenta de ZIP/JSON. O `metadata.json` diz
  isso e a interface também (barra de status, diálogo de saída, diálogo de recuperação).
- Nada é extraído para o disco ao abrir: as entradas são lidas para a memória, uma a uma, com limite de bytes
  efetivamente lidos.

## Entradas (padrão fechado)

| Entrada | Conteúdo | Definição no schema |
|---|---|---|
| `manifest.json` | identificação, versões, política, identidades, limites, lista de entradas com SHA-256 e tamanho | `Manifest` — ver [manifest.md](manifest.md) |
| `metadata.json` | produtor, `encrypted: false`, aviso | `Metadata` |
| `automation.json` | dados da automação e **ordem das sessões** | `Automation` |
| `sessions.json` | sessões (nome, descrição, notas) e **ordem das etapas** de cada uma | `Sessions` |
| `steps/<stepId>.json` | uma etapa: `original` + `effective` | `Step` |
| `evidence/<stepId>.json` | evidências atribuídas à etapa (só existe se houver ao menos uma) | `StepEvidence` |
| `evidence/unattributed.json` | evidências não atribuídas da automação (pode ser lista vazia) | `UnattributedEvidence` |

Expressão aceita para o nome (qualquer outro nome recusa o arquivo inteiro, `UNSAFE_ENTRY`):

```text
(manifest|metadata|automation|sessions)\.json | steps/<uuid>\.json | evidence/(<uuid>|unattributed)\.json
```

Isso exclui caminhos absolutos, `..`, diretórios, profundidade, scripts, executáveis, imagens e PageMaps.

**A ordem nunca vem do ZIP**: `automation.json.sessions` dá a ordem das sessões e `sessions.json[i].steps` a das etapas.

## Etapa: original × efetivo (§9)

```json
{
  "stepId": "…",
  "createdAt": "2026-10-16T12:00:10Z",
  "original": {
    "provenance": { "origin": "RECORDED", "correlation": { "RECORDING_ID": "rec-1", "RECORDED_EVENT_ID": "ev-3" },
                    "recording": { "mode": "CAPTURE_AND_INTERACT", "observation": "INTERACTION_OBSERVED",
                                   "businessOutcome": "NOT_VALIDATED", "evidence": ["FORM_SUBMITTED"], "limitations": [] } },
    "capturedElement": { "accessibleName": "Entrar", "…": "referência compacta, nekkora-privacy/2" },
    "context": { "application": "Navegador", "screenTitle": "Login", "location": "http://127.0.0.1:18765/{…}/{…}/clientes.html?id={…}" },
    "observedOutcome": "Envio de formulário observado (resultado de negócio não validado).",
    "baseline": { "name": "Clicar no botão Entrar", "actionType": "CLICK", "parameters": { "value": null, "option": null }, "preferredLocator": 0, "description": "…" },
    "derivedFrom": null
  },
  "effective": {
    "name": "Entrar no sistema", "description": "…", "actionType": "CLICK",
    "parameters": { "value": { "kind": "VARIABLE", "name": "cliente.usuario" }, "option": null },
    "preferredLocator": 0,
    "manualLocators": [ { "syntax": "CSS", "expression": "form button.entrar", "note": "…", "proposedAt": "…", "verification": "NOT_VERIFIED" } ],
    "comments": [ { "text": "…", "createdAt": "…", "editedAt": null } ],
    "fieldEdits": { "NAME": "2026-10-16T12:00:20Z" },
    "lastManualChange": "2026-10-16T12:00:34Z"
  }
}
```

- `original` nunca é substituído por `effective`. "Restaurar original" continua funcionando depois de abrir (S006-034).
- `parameters.value` é uma de três formas: `LITERAL` (texto digitado **no Studio**), `VARIABLE` (nome) ou `CREDENTIAL`
  (**apelido**, nunca o segredo). Não existe campo para valor digitado na página.
- `manualLocators[].verification` só aceita `NOT_VERIFIED`.

## Evidência

Todos os campos de `ObservedEvidence` (ver schema `Evidence`). `message.text` é **sempre `null`**: um arquivo com texto
da página é recusado. Ver [evidence-persistence.md](evidence-persistence.md).

## Regras semânticas (leitor; o schema não as expressa)

| Regra | Erro |
|---|---|
| Ordem das sessões em `automation.json` = sessões de `sessions.json` | `INTEGRITY` |
| Toda etapa referenciada existe; nenhuma etapa duas vezes; nenhuma etapa ou documento de evidência solto | `INTEGRITY` |
| Nome da entrada = identidade no documento (`steps/<id>.json` com `stepId` = id) | `INTEGRITY` |
| Preferencial dentro da lista e apontando um localizador validado | `INTEGRITY` |
| Evidência atribuída com `eventId` da etapa; evidência não atribuída sem etapa; identidades de evidência únicas | `INTEGRITY` |
| Invariantes do domínio (textos normalizados, parâmetros compatíveis com a ação, original coerente com a procedência, comentários sem aparência de dado sensível…) | `INVALID_CONTENT` |
| Campos capturados como ponto fixo da `nekkora-privacy/2` | `PRIVACY_VIOLATION` |
| `kind=RECOVERY` ⇔ `recoveryOf` presente | `INVALID_CONTENT` |
| Identidade da automação no manifesto = `automation.json` | `INTEGRITY` |

## Limites do leitor

| Limite | Valor | Observação |
|---|---|---|
| Arquivo `.nekkora` | 128 MiB | conferido antes de abrir o ZIP |
| Uma entrada descompactada | 8 MiB | bytes lidos de fato (o tamanho declarado não é confiável) |
| Soma das entradas | 512 MiB | bomba de descompressão para no limite |
| Etapas | 20.000 | 5.000 etapas realistas = 7,4 MiB ([performance.md](performance.md)) |
| Sessões | 2.000 | |
| Entradas | 40.005 | 5 fixas + 2 por etapa |

Os limites declarados no manifesto são informativos; o leitor usa os próprios.
