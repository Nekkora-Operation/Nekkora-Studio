# STUDIO-007 — Regras de localizadores

Aplicadas quando a etapa tem elemento e a ação não é `TargetRequirement.NONE`. Nada é enviado ao navegador: a
verificação ao vivo é de uma missão futura.

```text
capturados vazios?
  ├─ sim: manuais vazios? → LOCATOR_MISSING (×)   senão → MANUAL_LOCATOR_ONLY (!)
  └─ não: referência = preferencial; senão o 1º validado (+ LOCATOR_PREFERENCE_NOT_SET, i)
          ├─ nenhuma referência: algum com matchCount > 1? → LOCATOR_MULTIPLE_MATCHES (!) senão LOCATOR_NOT_VALIDATED (!)
          └─ referência: LOCATOR_VALIDATED_AT_CAPTURE (i)
                         + estabilidade UNKNOWN → LOCATOR_STABILITY_UNKNOWN (i)
                         + HEURISTIC/OBSERVED_UNSTABLE → LOCATOR_UNSTABLE (!)
                         + estratégia POSITIONAL → LOCATOR_POSITIONAL (!)
          manuais presentes → MANUAL_LOCATOR_UNVERIFIED (i)
```

## Validação histórica

`LocatorCandidate.validated()` (encontrou exatamente o alvo na captura) gera `LOCATOR_VALIDATED_AT_CAPTURE`, cuja
explicação é "Localizador validado na captura. Sua disponibilidade atual não foi verificada." Nenhum texto do catálogo
afirma disponibilidade atual (teste `s007021ValidadoNaCaptura`).

## Localizador manual

`ManualLocator` é sempre `NOT_VERIFIED` (STUDIO-004). Sozinho, é aviso; ao lado de capturados, informação.

## Limitações declaradas pelo Recorder

Códigos estáveis de `RecordingObservation.limitations()`:

| Código do Recorder | Apontamento |
|---|---|
| `NO_UNIQUE_LOCATOR` | `RECORDER_NO_UNIQUE_LOCATOR` (i) |
| `SAFE_LOCATOR_UNAVAILABLE` | `RECORDER_SAFE_LOCATOR_UNAVAILABLE` (i) |
| `LOCATORS_WITHHELD` | `PRIVACY_LOCATORS_WITHHELD` (i) — não é defeito |
| `LIVE_CHECK_UNAVAILABLE` | `RECORDER_LIVE_CHECK_UNAVAILABLE` (i) |
| `ELEMENT_UNRESOLVED`, `AMBIGUOUS_TARGET`, `INDISTINGUISHABLE_LOOKALIKES`, `ACCESS_RESTRICTED` | já chegam como obstáculos do elemento |

O texto livre (`stabilityNotes`) nunca é lido pelas regras nem copiado para o relatório.

Testes: `AutomationValidatorTest` S007-020 a 024.
