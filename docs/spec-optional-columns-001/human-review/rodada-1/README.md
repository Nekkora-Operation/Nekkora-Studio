# Revisão humana — rodada 1 (NEKKORA-SPEC-OPTIONAL-COLUMNS-001)

**Estado: AWAITING_HUMAN_REVIEW.** Só a titular aprova esta mudança funcional, lendo estes arquivos. O aceite da
CODEGEN-QUALITY-001 não vale aqui. Testes verdes e a opinião do agente não substituem o aceite.

- Python ORIGINAL gerado pela interface real do Studio (cópia byte a byte; SHA-256 em `*/SHA256SUMS.txt` e no
  `manifest.json`). Nada foi editado à mão.
- Studio: branch local `feat/spec-optional-columns-001`, commit testado `6828edc` (nada enviado, mergeado ou publicado).
  Framework `nekkora-rpa` 0.3.0 sem mudança.
- Regra que autoriza a célula vazia: a decisão revisada **"Morte" → Opcional: o dado não existe em alguns itens**
  (especificação funcional, janela Desenvolver automação → Pendências), registrada no manifesto em
  `output.requirement.specRevision` e no apêndice "Dados do processo" do PDD
  ([evidencias/interface/pdd-apendice-trecho.txt](evidencias/interface/pdd-apendice-trecho.txt)).

## O que ler (≈ 5 minutos)

1. [antes-depois.md](antes-depois.md) — a diferença inteira em relação ao código aprovado na CQ-001 (três arquivos).
2. `final/automation/pages.py:82` — `death_date: str | None` (as outras datas continuam `str`).
3. `final/automation/pages.py:56-68` — `_date_if_present`: ausente ou sem texto → `None`; data presente e incompleta
   continua recusando; ambíguo continua erro.
4. `final/automation/pages.py:139` — a leitura de "Morte" usa `_date_if_present`, DEPOIS da espera pela leitura
   obrigatória "Nascimento" (`:134`).
5. `final/automation/process.py:49-72` — `REQUIRED_FIELDS` (sem `death_date`) e a montagem da linha; a docstring diz a regra
   de "Morte".
6. `final/main.py:35` — `OutputColumn("Morte", "death_date", ColumnKind.ISO_DATE, required=False)`; o cabeçalho "Morte"
   continua igual.
7. `final/manifest.json` → `output` — cada coluna com `requirement` e o esquema da regra.

`desenvolver/` é a primeira geração depois da decisão (mesmo código; o manifesto registra outra revisão da automação).

## Planilhas (sem dado real)

| Arquivo | O quê |
|---|---|
| [evidencias/planilhas/wikipedia-5-artigos-sanitizada.xlsx](evidencias/planilhas/wikipedia-5-artigos-sanitizada.xlsx) | a planilha REAL do teste autorizado, com cada valor trocado pelo tipo (`<texto>`, `<data AAAA-MM-DD>`, `<url>`); a célula vazia continua vazia — linha 6 (pessoa viva), coluna "Morte" |
| [evidencias/planilhas/laboratorio-ficticio-ana-bruno-gina.xlsx](evidencias/planilhas/laboratorio-ficticio-ana-bruno-gina.xlsx) | execução no Chromium contra a réplica do laboratório (dados FICTÍCIOS); "Bruno Modelo" (vivo) com a célula "Morte" vazia |

## Resultados

| Verificação | Resultado |
|---|---|
| Interface real do Studio (gravação, PDD, Pendências, geração, teste autorizado com 5 artigos, V6) | 105/105 |
| Conferidor independente da planilha real | 29/30 células — a divergente é a chamada de nota no "Nome completo" da pessoa viva (achado F-OC-01, decisão sua); a célula "Morte" vazia confere |
| Matriz G5 (`OptionalColumnsTest`) | 13/13 no Windows |
| Suíte Windows (commit `6828edc`) | 780 testes, 0 falhas, 0 pulados |
| Suíte Linux (commit `6828edc`) | 780 testes, 0 falhas, 27 pulados (753 aprovados; motivos em [evidencias/suites/linux.md](evidencias/suites/linux.md)) |

Detalhes, instabilidades e o que não foi executado: [../../homologation.md](../../homologation.md).

## Para você decidir

1. Aceite desta rodada (legibilidade e comportamento da coluna opcional).
2. F-OC-01: manter o texto visível da célula como está (chamadas de nota incluídas) ou removê-las, em missão própria.
3. Publicação/push: continua bloqueada.
