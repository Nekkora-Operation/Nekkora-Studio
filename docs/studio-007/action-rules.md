# STUDIO-007 — Regras por tipo de ação

As regras usam os metadados que o domínio já declara em `ActionType` (`target()`, `acceptsValue()`, `acceptsOption()`).
Nenhum catálogo paralelo foi criado. O domínio continua aceitando etapas incompletas: o motor aponta, nunca recusa nem
impede a edição, e um projeto `.nekkora` válido continua abrindo mesmo com bloqueios.

| Ação | Alvo (`TargetRequirement`) | Verificações |
|---|---|---|
| `CLICK` | EXPECTED | elemento (`ELEMENT_MISSING`), localizadores |
| `FILL` | EXPECTED | elemento, localizadores, origem do valor (`REQUIRED_VALUE_MISSING`), dependências |
| `SELECT_OPTION` | EXPECTED | elemento, localizadores, opção (`OPTION_NOT_CONFIGURED`) |
| `CHECK`, `UNCHECK` | EXPECTED | elemento, localizadores |
| `PRESS_KEY` | EXPECTED | elemento, localizadores, tecla (`KEY_NOT_CONFIGURED`, `KEY_NOT_RECOGNIZED`) |
| `NAVIGATE` | NONE | origem do valor (`NAVIGATION_TARGET_MISSING`, `NAVIGATION_TARGET_NOT_ADDRESS`); um elemento presente é ignorado |
| `WAIT_FOR` | OPTIONAL | `WAIT_CONDITION_NOT_MODELED` (aviso que limita a avaliação); localizadores só se houver elemento |
| `OBSERVE` | OPTIONAL | `OBSERVE_IS_NOT_VALIDATION` (informação) |
| `UNKNOWN` | OPTIONAL | `ACTION_UNCLASSIFIED` (bloqueio de classificação) |

## Decisões

- **`WAIT_FOR`** é aviso (não bloqueio): a etapa pode ter sido descrita corretamente em texto; o que falta é um contrato
  formal de condição, que o modelo ainda não tem. Por isso o estado vira, no máximo, `NOT_FULLY_ASSESSABLE`.
- **`PRESS_KEY`** aceita os nomes de `KeyboardEvent.key` mais comuns (Enter, Tab, Escape, setas, Home/End, PageUp/Down,
  Backspace/Delete/Insert, espaço, F1–F12). O Recorder só produz Enter (STUDIO-003).
- **`NAVIGATE`** com literal sem `http://`/`https://` é aviso, não bloqueio (a forma do endereço pode ser válida para o
  produtor futuro); com variável, entra em [dependency-rules.md](dependency-rules.md).
- **`PARAMETER_NOT_APPLICABLE`** é defensivo: o editor e o domínio já impedem parâmetros incompatíveis, mas o motor não
  presume isso.

Testes: `AutomationValidatorTest` S007-004 a 018.
