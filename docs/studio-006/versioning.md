# STUDIO-006 — Versionamento

## Três versões independentes (§27)

| Versão | Valor atual | Onde | Responsabilidade |
|---|---|---|---|
| Formato do projeto | `nekkora-studio/project` **1.0.0** | `manifest.format`, `manifest.formatVersion` | estrutura do pacote: entradas, manifesto, documentos e campos |
| Modelo de automação | `nekkora-studio/automation` **1.0.0** (`ModelVersion.SCHEMA`/`CURRENT`) | `manifest.automationModel`, `automation.json` (`schema`, `modelVersion`) | contrato do domínio |
| Política de privacidade | `nekkora-privacy/2` (`CapturePrivacy.VERSION`) | `manifest.privacyPolicy` | garantias sobre as informações capturadas |

Elas não são equivalentes e mudam por motivos diferentes. Mudar uma classe Java não muda nenhuma delas: o codec é
explícito e a fixture congelada 1.0.0 (`fixtures/valid/consulta-clientes.nekkora`) tem de continuar abrindo e sendo
reescrita igual (`ProjectFormatContractTest`). Se um teste desses falhar depois de uma mudança no domínio, a mudança
quebrou a compatibilidade com arquivos já salvos.

## Regras de leitura (esta versão do Studio)

| Arquivo declara | Resultado | Código |
|---|---|---|
| `formatVersion` 1.0.x | lido (o PATCH não muda a estrutura) | — |
| `formatVersion` 1.1.0, 2.0.0… (mais nova) | recusado **antes** de ler o resto, com a mensagem "criado por uma versão mais recente" | `NEWER_VERSION` |
| `formatVersion` 0.x ou inválida | recusado | `UNSUPPORTED_VERSION` |
| `automationModel` / `modelVersion` ≠ 1.0.0 | recusado | `UNSUPPORTED_VERSION` |
| `privacyPolicy` ≠ `nekkora-privacy/2` (anterior **ou** futura) | recusado | `PRIVACY_POLICY_UNSUPPORTED` |
| `metadata.encrypted = true` | recusado (o formato não tem criptografia) | `UNSUPPORTED_VERSION` |

Nenhum arquivo é migrado: não existem migrações nesta versão (§27, "não implementar migrações fictícias"). Um arquivo
mais novo nunca é aberto "pela metade" e salvo de novo sem os campos que esta versão não conhece — o leitor recusa
campos desconhecidos em vez de descartá-los.

## Como evoluir (para as próximas missões)

- **PATCH** (1.0.x): correção que não muda a estrutura nem o significado (ex.: texto do `notice`).
- **MINOR** (1.1.0): campos ou entradas novos. Um Studio 1.0 recusa (`NEWER_VERSION`); o Studio 1.1 lê 1.0 e 1.1. Ler
  1.0 num Studio 1.1 exige uma migração explícita, testada com a fixture 1.0.0 congelada.
- **MAJOR** (2.0.0): mudança incompatível.
- Nova política de privacidade: só com uma migração explícita que **não consiga** reincorporar dados proibidos;
  dados capturados sob política anterior nunca são classificados automaticamente como compatíveis.
- Sempre: congelar a fixture da versão nova ao lado das anteriores (`contracts/nekkora-project/<versão>/`).

Testes: `ProjectValidationTest` S006-049 e S006-072, fixtures `formato-mais-novo`, `politica-privacidade-anterior`,
`metadados-criptografado`.
