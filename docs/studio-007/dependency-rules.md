# STUDIO-007 — Dependências não comprováveis

| Referência | O Studio confirma | O Studio NÃO afirma | Código |
|---|---|---|---|
| `VariableReference` | que a referência existe e tem nome sintaticamente válido (o domínio já garante) | que a variável está declarada ou terá valor | `VARIABLE_RESOLUTION_UNAVAILABLE` (!, limita) |
| `CredentialReference` | idem | que existe uma credencial disponível | `CREDENTIAL_RESOLUTION_UNAVAILABLE` (!, limita) |

## Por que aviso e não bloqueio

A referência é a configuração CORRETA esperada (o usuário fez o que podia); o que falta é um contrato de resolução que
o modelo ainda não tem (catálogo de variáveis, cofre de credenciais). Bloqueio diria que falta configurar algo; não
falta. Mas também não é informação neutra: a resolução não pode ser demonstrada. Por isso **aviso que limita a
avaliação**, e o estado nunca passa de `NOT_FULLY_ASSESSABLE` enquanto houver uma.

## O que o motor não faz

- Não consulta o Windows Credential Manager nem qualquer cofre.
- Não lê variáveis de ambiente, arquivos ou rede.
- Não inventa valores, nem para teste.
- Não coloca o nome da variável nem o apelido da credencial no relatório.

STUDIO-008 (proposta) poderá declarar variáveis e configurar dependências; o código atual nunca será reinterpretado:
uma regra nova terá código novo numa nova versão das regras.

Testes: `AutomationValidatorTest` S007-008/009, `ValidationContractTest.s007027SemDadosPrivados`.
