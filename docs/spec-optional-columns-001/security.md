# Segurança, privacidade e proveniência — NEKKORA-SPEC-OPTIONAL-COLUMNS-001

- **A decisão não guarda dado.** `OUTPUT_REQUIREMENT` é um código de vocabulário fechado (`REQUIRED`,
  `OPTIONAL:ABSENT_FOR_SOME_ITEMS`, `OPTIONAL:NOT_APPLICABLE_FOR_SOME_ITEMS`) com o ator, a base, a data e o fingerprint da
  etapa. Nenhum texto livre, nenhum valor de página, nenhum nome de pessoa. A justificativa é o motivo do vocabulário.
- **Nada decide por conta própria.** O Studio não deduz a opcionalidade do tipo, do nome da coluna, do rótulo ou de uma
  execução; a IA não tem proposta para este campo (o verificador de propostas recusa o tipo: `KIND_UNKNOWN`, testado). Só a resposta revisada na
  janela Desenvolver automação conta.
- **Proveniência estrutural.** O manifesto 1.5.0 aponta a regra para a especificação revisada que a autorizou
  (`output.requirement.specRevision`, a mesma revisão de `pdd.specRevision` que liberou a geração); cada coluna liga o
  cabeçalho externo ao campo interno e diz `REQUIRED`/`OPTIONAL` com o motivo. Nenhum conteúdo do cliente.
- **Logs e motivos.** Coluna obrigatória vazia recusa com `DADO_OBRIGATORIO_VAZIO:<campo>` (nome do campo, nunca o
  conteúdo). Coluna opcional vazia não gera evento nem motivo. Os eventos JSONL do framework não levam valores (conferido
  no teste com Chromium: nenhum nome, data ou nacionalidade das fichas sintéticas nos eventos).
- **Dataclasses sem `repr` de valores** (herdado da CODEGEN-QUALITY-001, `repr=False`): `death_date: str | None` não muda
  isso.
- **Planilha.** Célula opcional vazia NÃO é escrita (o framework pula `""`); a planilha continua tudo-ou-nada e gravada de
  forma atômica: um item recusado (ex.: sem "Nascimento") impede a planilha inteira (testado).
- **Sites.** Os testes automatizados usam uma CÓPIA temporária da réplica de leitura do laboratório (dados fictícios,
  127.0.0.1); o laboratório do framework não foi alterado. A homologação pela interface lê cinco artigos públicos da
  Wikipédia (só pesquisa e leitura), uma vez, sem conta nem dado sensível.
- **Publicação.** Nada foi enviado a repositório remoto, mergeado ou publicado (PyPI/TestPyPI); o framework não mudou.
