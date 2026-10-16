# STUDIO-007 — Resultados determinísticos

O mesmo estado da automação, com a mesma versão das regras, produz o mesmo relatório — os mesmos objetos
(`equals`) e os mesmos bytes em JSON.

## Como

- **Sem instantes e sem identificadores gerados**: nada de relógio, UUID novo ou aleatoriedade no motor (o teste de
  independência recusa `Instant.now`, `UUID.randomUUID`, `Random`, `System.currentTimeMillis`).
- **Identificador estável do apontamento**: `CÓDIGO:identidade[:detalhe]`, onde a identidade é o `StepId`, `SessionId`
  ou `AutomationId`. Um apontamento sobre a mesma etapa, pelo mesmo motivo, tem sempre o mesmo id; editar uma etapa não
  muda o id dos apontamentos das demais (teste `s007032e033Estaveis`).
- **Ordem**: automação → sessões (na ordem da automação; o apontamento da sessão antes das suas etapas) → etapas (na
  ordem da sessão) → regras (ordem de declaração em `FindingCode`, depois o detalhe). Duplicatas (mesmo código e
  detalhe) são unificadas.
- **JSON canônico**: campos sempre presentes, ordem fixa, ausência como `null`, recuo de dois espaços, `\n`.
- **Versão das regras** no relatório (`rules: nekkora-validation-rules/1.0.0`). Mudar o significado de um código exige
  código novo e nova versão; a fixture congelada acusa qualquer mudança não revisada.

## Projeto salvo e reaberto

As identidades são preservadas pela STUDIO-006; por isso o relatório de um projeto reaberto é idêntico ao de antes de
fechar (teste `s007037a039ProjetoSalvo` e cenário 4 da homologação).

Testes: `AutomationValidatorTest` S007-031 a 033, `ValidationContractTest.fixtureCongelada`.
