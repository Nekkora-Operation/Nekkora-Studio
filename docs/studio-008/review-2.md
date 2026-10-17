# STUDIO-008 — Revisão 2 do PR #11

A segunda revisão técnica do PR #11 (HEAD revisado `9992b0a`) considerou P1 e P2 resolvidos. Ela registrou uma
observação não bloqueante: o modelo aceitava formatos de texto que contradizem o significado. A recomendação era impedir
as combinações manifestamente incompatíveis antes do merge, ou registrá-las como limitação. **Corrigido aqui**, no mesmo
PR, sem merge.

| Antes (código de `9992b0a`) | Resultado |
|---|---|
| `STRING` + `EMAIL` + `BRAZILIAN_CPF_MASKED`, confirmado | aceito; nenhum apontamento |
| `STRING` + `BRAZILIAN_CPF` + `EMAIL_SHAPE` | aceito; a validação dizia "representação não declarada" em vez de apontar a contradição |

## Ordem do trabalho

1. **Reprodução primeiro** (commit `a19e6e4`): três testes novos, escritos para compilar no código revisado, falham nele
   (`evidence/revisao-2/R2-antes-da-correcao.txt`):
   - `VariableDomainTest.formatoCoerenteComSignificado`
   - a fixture inválida `nekkora-project/1.1.0/.../formato-incoerente-com-significado.json`
   - `VariablesUiTest.formatoIncoerenteRecusado`
2. **Correção** e regressão dos módulos afetados.
3. **Homologação real focada** (Windows, Robot): 7/7.
4. **Regressão completa** no Windows e no Linux (números em `reports/studio-008-final-report.md` §17).

## Critério: só o manifestamente contraditório

Um formato contradiz o significado quando **nenhuma forma válida do significado o satisfaz**. O teste prova o critério
com formas fictícias de cada significado, sem lista escrita à mão:

| Significado | Formas válidas (fictícias) | Formatos admitidos | Recusados |
|---|---|---|---|
| CPF | `12345678909`, `123.456.789-09` | Qualquer texto, Somente dígitos, Letras e dígitos, Máscara de CPF | Forma de e-mail |
| E-mail | `pessoa@exemplo.test` | Qualquer texto, Forma de e-mail | Somente dígitos, Letras e dígitos, Máscara de CPF |
| Não especificado, texto livre, identificador | — (sem forma canônica) | todos | nenhum |
| Valor monetário, porcentagem, data | — | só "Qualquer texto": não são `STRING`, regra anterior | — |

- **Admitir não é declarar a representação.** Um CPF com "Letras e dígitos" é admitido, porque `12345678909` satisfaz o
  formato. Mas ele continua sem representação declarada e mantém o aviso `VARIABLE_REPRESENTATION_UNSPECIFIED` da
  revisão 1.
- **Lista explícita para CPF e e-mail.** Um formato novo acrescentado ao enum entra recusado para esses dois até ser
  avaliado. Os demais significados continuam abertos.
- **Vale para significado confirmado ou proposto.** A contradição existe nos dois casos.

## Onde a regra vale

| Camada | Comportamento |
|---|---|
| Domínio (`VariableRules.checkConstraints`, `SemanticType.admits`) | declarar ou editar com formato contraditório é recusado com o novo `ValidationProblem.VARIABLE_FORMAT_INCOMPATIBLE`. É a mesma regra que já recusa uma moeda fora de "Valor monetário": nada é corrigido em silêncio |
| Formulário real | a mensagem "O formato contradiz o significado (ex.: máscara de CPF num e-mail). Escolha um formato compatível." aparece no campo **Formato (texto)**, com a aba Restrições em primeiro plano; nada é declarado nem alterado |
| Abertura de `.nekkora` 1.1.0 e de recuperação | recusada como conteúdo inválido (`INVALID_CONTENT`), como as demais regras semânticas do domínio. O schema JSON não cobre a regra (`schemaRejects: false`), assim como já não cobria `significado-incompativel.json` |
| Validação e contrato semântico | sem código novo. Uma automação não consegue mais conter a combinação; se uma definição incoerente chegasse ao validador por outro caminho, `VariableRules.problem` já a reporta como `VARIABLE_DECLARATION_INCONSISTENT` |

## Contratos e fixtures

- **Formato `nekkora-project` 1.1.0:** schema inalterado. Há uma fixture **inválida nova**, a
  `formato-incoerente-com-significado.json`, e nenhuma fixture existente foi alterada ou regenerada.
- **Contrato semântico 1.0.0 e relatório de validação 1.1.0:** intocados, com fixtures idênticas.
- **Arquivos já gravados com a combinação:** só poderiam ter sido produzidos por builds deste PR, que ainda não foi
  integrado. Um exemplo é o e-mail com máscara de CPF. Esses arquivos agora são recusados ao abrir.

## Homologação real focada (7/7)

`Studio008Review2Homologation` usa as mesmas travas da revisão 1: dados isolados, Robot, foco conferido antes de cada
atalho, sem navegador, dados fictícios. Evidências em `evidence/revisao-2/`.

| Verificação | Captura |
|---|---|
| Nova variável: e-mail confirmado + "Máscara de CPF" é recusado no campo de formato; o diálogo continua aberto na aba Restrições | `S008-R2-01` |
| nada foi declarado | — |
| com "Forma de e-mail" a variável é declarada | — |
| Editar: significado trocado para CPF mantendo "Forma de e-mail" é recusado; a variável continua e-mail | `S008-R2-02` |
| com "Somente dígitos" a edição é aceita | — |
| Validar (Ctrl+Shift+V real): nenhuma declaração inconsistente nem aviso de representação | `S008-R2-03` |
| logs sem nomes de variáveis | — |

Na primeira execução da homologação, a mensagem original era longa e saía cortada com reticências no diálogo de 760 px.
O corte escondia a orientação "Escolha um formato compatível". A mensagem foi encurtada e a homologação repetida: as
capturas mostram o texto inteiro.

A homologação completa (40/40) e a focada da revisão 1 (12/12) não foram repetidas. A mudança é uma regra de recusa no
domínio, mais o posicionamento da mensagem no formulário. Os fluxos integrados não mudaram: gravação, migração,
abertura, renomeação, painéis, aviso de representação e contrato. A regressão completa os cobre.

## Fora do escopo (limitação aceita)

A regra cobre só o vocabulário atual: CPF e e-mail são os únicos significados com forma conhecida. Representações
de porcentagem (`15` × `0.15`), de moeda ou de identificadores ficam para uma missão própria, como já registrado na
revisão 1.
