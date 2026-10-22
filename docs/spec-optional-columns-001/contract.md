# Contrato — obrigatoriedade das colunas de saída (NEKKORA-SPEC-OPTIONAL-COLUMNS-001)

## 1. Onde a regra mora

A planilha de saída já é definida pela **especificação funcional** da automação (complemento estruturado do PDD, mostrado
no apêndice "Dados do processo" do PDD): cada etapa Observar decide `OUTPUT_COLUMN` (`COLUMN:<cabeçalho>`,
`FALLBACK:<etapa>` ou `NONE`). A obrigatoriedade é uma decisão a mais, **por coluna**:

| Campo | Valores (vocabulário fechado) | Padrão sem decisão |
|---|---|---|
| `OUTPUT_REQUIREMENT` (por etapa Observar que dá uma coluna própria) | `REQUIRED` · `OPTIONAL:ABSENT_FOR_SOME_ITEMS` (o dado não existe em alguns itens) · `OPTIONAL:NOT_APPLICABLE_FOR_SOME_ITEMS` (o dado não se aplica a alguns itens) | `REQUIRED` |

- A decisão guarda o ator (`DEVELOPER`/`TEST_AGENT`), a base (`ANSWER`), a data e o **fingerprint da etapa**: se o alvo
  da leitura mudar, a decisão deixa de valer e a coluna volta a ser obrigatória (nada é herdado em silêncio).
- O motivo é a justificativa revisada, em vocabulário fechado (nenhum texto livre: a especificação não guarda dados).
- Esquema `nekkora-studio/functional-spec` **1.2.0**, gravado só quando o campo é usado; 1.0.0 e 1.1.0 continuam lidas e
  gravadas como antes. O campo num documento 1.0.0/1.1.0 é recusado (`SPEC_VERSION`).
- **Nada infere `OPTIONAL`**: nem o Studio (tipo, nome, rótulo), nem a IA (não há proposta para este campo), nem uma
  execução que viu o dado faltar. Só uma decisão revisada.
- A coluna-chave (endereço lido, sem alvo) não pode ser opcional: `SPEC_OUTPUT_KEY_OPTIONAL` (bloqueio).

## 2. Três conceitos diferentes

| Conceito | O que é | Onde |
|---|---|---|
| Elemento ausente na página | o alvo não existe neste documento (`Actions.exists` = `False`) | DOM/observação, com identidade verificada |
| Fonte alternativa | o PDD manda ler outro dado quando o principal falta | `OUTPUT_COLUMN = FALLBACK:<etapa>` |
| Coluna de saída opcional | depois de TODAS as fontes autorizadas, a coluna pode ficar sem valor e o item continua válido | `OUTPUT_REQUIREMENT = OPTIONAL:<motivo>` |

## 3. Precedência (por coluna, por item)

1. Fonte principal: lida pelo localizador verificado. Coluna opcional → leitura `exists()` sem espera, DEPOIS de a tela
   estar pronta (`wait_loaded` pela primeira leitura OBRIGATÓRIA com alvo da mesma tela; sem nenhuma, o gerador recusa:
   `OPTIONAL_READ_WITHOUT_REQUIRED_ANCHOR`).
2. Fontes alternativas expressamente aprovadas (`_filled(principal) or _filled(alternativa)`).
3. Sem valor depois de todas: `REQUIRED` → `BusinessRuleRejected("DADO_OBRIGATORIO_VAZIO:<campo>")` (o nome do campo,
   nunca o conteúdo); `OPTIONAL` → `None` na linha → **célula vazia** na planilha (`OutputColumn(..., required=False)` do
   nekkora-rpa 0.3.0 não escreve a célula).

Ausência (`None`) só vem de: elemento inexistente, ou elemento presente sem texto (vazio ou só espaços — o leitor do
framework normaliza e descarta itens vazios). Nunca de:

| Situação | Resultado |
|---|---|
| Alvo ambíguo ou identidade divergente | erro do framework (`TARGET_AMBIGUOUS` / `TARGET_IDENTITY_MISMATCH`), item não concluído |
| Data presente e incompleta/impossível | `DATA_INCOMPLETA_OU_AMBIGUA` (no gerador) ou `date_invalid_<campo>` (no sink) |
| URL presente e inválida | `url_invalid_<campo>` (sink) |
| Falha técnica (página não carregou, tempo esgotado) | erro classificado do framework |

Os valores de saída são texto (o leitor devolve texto); o tipo da coluna (`TEXT`, `ISO_DATE`, `URL`) é validado pelo
sink. Nenhum julgamento por "truthiness": `_filled` só testa `None`/vazio/só espaços; um texto "0" ou "False" é valor.

## 4. Da decisão ao arquivo

| Camada | Coluna obrigatória (legado e padrão) | Coluna opcional |
|---|---|---|
| Configuração de geração (`SingleConfig`) | 1.2.0, `Column` sem `required` | **1.3.0**, `required: false` + `requirementReason` (todas as colunas ganham os dois campos) |
| Leitura | `_text` / `_date` (alvo precisa existir) | `_text_if_present` / `_date_if_present` (`exists()` → `None`) |
| Modelo de dados (`ProfileData`) | `str` | `str \| None` (docstring: "absence foreseen in the PDD: … an optional column") |
| Linha da planilha (`build_spreadsheet_row`) | `for field, value in row.items(): if value is None: raise …` (idêntico ao de antes) | `REQUIRED_FIELDS = (…)` e `for field in REQUIRED_FIELDS:` |
| `main.py` | `OutputColumn("Nascimento", "birth_date", ColumnKind.ISO_DATE)` | `OutputColumn("Morte", "death_date", ColumnKind.ISO_DATE, required=False)` |
| Manifesto | 1.4.0 | **1.5.0**: `output.columns[].requirement` (`REQUIRED`/`OPTIONAL`) + `requirementReason`; `output.requirement` = `{schema: nekkora-studio/output-requirement/1.0.0, source: functional-spec, specRevision, default: REQUIRED, emptyCell: absent}` |
| PDD (apêndice "Dados do processo") | texto de antes | a coluna diz "OPCIONAL (<motivo>): sem o dado, a célula fica vazia" |
| README do projeto | — | "Optional in the PDD (the cell stays empty when the data is missing; nothing is invented): `Morte`." |

**Compatibilidade:** sem decisão `OPTIONAL`, a configuração continua 1.2.0, o manifesto 1.4.0 e o Python byte a byte o
de antes (testado; `REQUIRED` explícito também). Projetos gerados antes continuam executáveis como estão. Regerar sobre
uma pasta antiga segue as regras de sempre (`previous-generation/`, arquivos editados protegidos).

## 5. Contratos externos preservados

Cabeçalhos ("URL", "Nome completo", "Nascimento", "Morte", "Nacionalidade", "Ocupação"), colunas da lista de entrada
(`termo_de_busca`, `endereco_do_artigo`), rótulos da página nos localizadores, códigos de recusa
(`DADO_OBRIGATORIO_VAZIO:<campo>`, `DATA_INCOMPLETA_OU_AMBIGUA`, `ENDERECO_DIFERENTE_DO_ESPERADO`), aba "Dados", formato
da célula (texto) e a gravação tudo-ou-nada: nada muda. A coluna opcional NÃO é removida da planilha; só a célula fica
vazia.

## 6. Verificação (V5/VQ)

O contrato V5 do projeto gerado (`contrato_projeto.py`) ganhou `OUTPUT_REQUIREMENT_CONSISTENT` para projetos com
planilha: manifesto, `main.SPREADSHEET` e `REQUIRED_FIELDS` do processo precisam dizer o mesmo (cabeçalhos na ordem,
opcional só onde declarado, chave obrigatória, a linha exigindo exatamente as obrigatórias). Divergência →
`OUTPUT_REQUIREMENT_MISMATCH` (controle negativo testado). A conferência da planilha do teste autorizado
(`conferir_planilha.py`) já usava `OutputColumn.required` do esquema declarado.

## 7. Fora do alcance

- Emissor tabular (planilha de ENTRADA → formulário, RPA Challenge): não tem planilha de saída por colunas.
- Obrigatoriedade das colunas de ENTRADA (`tabular.Column.required`): não muda nesta missão.
- Tipos numérico/booleano de coluna: o framework 0.3.0 só tem `TEXT`, `ISO_DATE`, `URL`. Extensão futura documentada em
  [decisions.md](decisions.md).
