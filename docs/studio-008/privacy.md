# STUDIO-008 — Privacidade

Regra: o Studio guarda **declarações**, nunca valores de execução.

| Onde | O que existe | O que nunca existe |
|---|---|---|
| Domínio | nome, tipos, significado, finalidade, escopo, origem, sensibilidade, restrições; valor fixo só para `PUBLIC`/`INTERNAL` | valores de entrada, saídas de etapas, segredos |
| `.nekkora` (`variables.json`) | a declaração; o valor fixo permitido, em texto claro (o arquivo não é criptografado e diz isso) | valores de execução, CPF/e-mail de verdade, senhas |
| Recuperação | a mesma declaração (é uma fotografia do projeto) | idem |
| Relatório de validação | códigos, severidades, identidades (`variableId`) | nomes de variáveis, valores, finalidade |
| Contrato semântico | nome simbólico declarado, tipos, escopo, origem (`constantDeclared` true/false), sensibilidade, restrições, identidades | valor fixo, literais, apelidos de credencial, nomes de automação/sessões/etapas, finalidade, nomes não declarados, conteúdo de página, localizadores, evidências (lista `exclusions` no próprio contrato) |
| Logs | códigos e contagens (como antes) | nomes, valores |
| `toString()` de `VariableDefinition`, `VariableOrigin`, `VariableId`, `SemanticContract` | tipo/contagens | nome e valor |

## Defesas no domínio

- Valor fixo recusado para `PERSONAL_DATA`/`SECRET` (`VARIABLE_CONSTANT_FORBIDDEN`) e quando parece dado sensível
  (`DisplaySafety.looksSensitive` → `VARIABLE_CONSTANT_LOOKS_SENSITIVE`) — por exemplo, um CPF digitado como valor fixo.
- Finalidade recusada quando parece dado sensível (`VARIABLE_PURPOSE_LOOKS_SENSITIVE`).
- Significados CPF e e-mail exigem no mínimo "Dados pessoais"; a sensibilidade nunca é rebaixada automaticamente.
- As mesmas regras são reaplicadas na leitura de um projeto (um arquivo editado à mão não contorna nada).

## Interface

O formulário mostra o aviso "Não criptografado" sempre que a origem é Valor fixo. As linhas do painel nunca mostram o
valor fixo. Não há campo para valor de execução.

## Credenciais e cofres

`CredentialReference` continua sendo só apelido; nenhum cofre é acessado. Nada da política `nekkora-privacy/2` foi
reclassificado nem recuperado.

## Conferido em

`SemanticContractTest.semDadosPrivados`, `ValidationContract11Test.semDadosPrivados`,
`ProjectFormat11ContractTest.escritaAtualConforme`, fixtures inválidas `constante-em-dado-pessoal` e
`valor-de-execucao-gravado`, `VariablesUiTest.constanteSensivelRecusada` e a homologação (logs e recuperação
varridos).
