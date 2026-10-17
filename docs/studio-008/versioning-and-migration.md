# STUDIO-008 — Versionamento e migração

## Versões

| Contrato | Antes | Agora |
|---|---|---|
| Pacote `.nekkora` (`ProjectFormat`) | 1.0.0 | **1.0.0 ou 1.1.0** — a menor capaz de representar o conteúdo |
| Modelo de automação (`ModelVersion`) | 1.0.0 | 1.0.0 (sem variáveis) ou **1.1.0** (com catálogo) |
| Política de privacidade | `nekkora-privacy/2` | inalterada |
| Relatório de validação | 1.0.0 | 1.0.0 (regras 1.0.0) e **1.1.0** (regras 1.1.0) |
| Contrato semântico | — | **1.0.0** (novo) |

Cada versão do pacote tem exatamente um modelo: 1.0.x → modelo 1.0.0; 1.1.x → modelo 1.1.0.

## Formato 1.1.0

Igual ao 1.0.0 mais **uma** entrada: `variables.json` (`{"variables":[...]}`, ao menos uma variável), com todos os
campos sempre presentes (`null` onde não há valor) e decimais como texto. Schema:
`nekkora-studio-persistence/src/test/resources/contracts/nekkora-project/1.1.0/nekkora-project.schema.json`. Fixtures
válidas (projeto e recuperação) e 17 inválidas no mesmo diretório.

## Escritor

`ProjectFormat.versionFor(documentos)`: sem variáveis → 1.0.0, com exatamente os mesmos documentos de antes (conferido
entrada por entrada contra a fixture 1.0.0 congelada); com variáveis → 1.1.0. Remover a última variável volta a
gravar 1.0.0.

## Leitor

| Arquivo | Resultado |
|---|---|
| 1.0.x sem `variables.json`, modelo 1.0.0 | lido como antes (catálogo vazio) |
| 1.0.x com `variables.json` | `UNEXPECTED_ENTRY` (estrito: nada é ignorado) |
| 1.1.x com `variables.json`, modelo 1.1.0 | lido; regras do domínio reaplicadas a cada variável |
| 1.1.x sem `variables.json` | `MISSING_ENTRY` |
| 1.1.x com catálogo vazio | `INVALID_CONTENT` (o escritor nunca produz isso) |
| modelo incoerente com o formato | `UNSUPPORTED_VERSION` |
| 1.2 ou mais novo | `NEWER_VERSION` (recusado antes de interpretar) |

## Migração ao salvar

Gravar uma automação com variáveis **sobre o próprio arquivo** de formato 1.0 (ou de formato desconhecido, como após
uma recuperação) é migração:

1. confirmação explícita "Atualizar o formato do projeto?" — explica que versões anteriores do Studio não abrem 1.1.0,
   o nome da cópia e que nada é executado nem há pedido de credenciais. Cancelar não grava nada;
2. no worker, antes de gravar: lê o arquivo atual, confere que é o conteúdo esperado (senão **conflito**: nada é
   copiado nem gravado) e grava a cópia `<nome> (formato 1.0.0).nekkora` (ou ` 2`, ` 3`…) por temporário + movimento
   **sem substituir** arquivo existente, conferindo os bytes;
3. grava o projeto 1.1.0 pelo salvamento atômico de sempre (com releitura completa antes de confirmar);
4. a notificação de sucesso informa a cópia.

"Salvar como" em outro arquivo não toca no original (não é migração). Abrir, validar, ver ou fechar um projeto 1.0.0
não o altera (sha e data de modificação conferidos na homologação). Os projetos recentes registram a versão gravada.

## Ajustes autorizados em artefatos 1.0.0

Ver `decisions.md` §3: `formato-mais-novo.json` e `s006049Versao` usam 1.2.0 como "mais novo"; o schema 1.0.0 do
relatório é comparado com o subconjunto 1.0.0; `s007034a036` espera `VARIABLE_NOT_DECLARED`. Nenhum binário `.nekkora`
congelado mudou.
