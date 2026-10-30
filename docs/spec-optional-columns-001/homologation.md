# Homologação — NEKKORA-SPEC-OPTIONAL-COLUMNS-001

Commit testado: **`6828edc`** (branch local `feat/spec-optional-columns-001`). Framework `nekkora-rpa` 0.3.0 sem mudança
(instalado pelo próprio Studio no ambiente da automação). Data: 21–22/10/2026.

## 1. Suítes completas (executadas por mim)

| Ambiente | Testes | Falhas | Pulados | Aprovados |
|---|---|---|---|---|
| Windows 11, JDK 21, Python do framework | 780 | 0 | 0 | 780 |
| Linux (`tools/golden-linux.sh verify`, contêiner fixado por digest) | 780 | 0 | **27** | 753 |

Os 27 pulados do Linux NÃO contam como aprovados: 26 dependem do Python do framework, que o contêiner não tem (3 deles são
desta missão: matriz em Python real, planilha real e laboratório com Chromium); 1 (`AtomicSaveTest`) não se aplica a root.
Os 26 rodam e passam no Windows. Detalhe em `human-review/rodada-1/evidencias/suites/`.

Uma primeira tentativa da suíte Linux foi interrompida pelo Claude Code por falta de memória na máquina (não foi falha);
rodada de novo depois que a titular liberou memória.

## 2. Matriz G5 (`OptionalColumnsTest`, 13 testes, todos executados no Windows)

| Cenário | Resultado |
|---|---|
| Contrato da decisão (valores, versão 1.2.0 só quando usada, campo novo em versão antiga recusado) | OK |
| A IA não decide (proposta `OUTPUT_REQUIREMENT` → `KIND_UNKNOWN`) | OK |
| Sem decisão = obrigatória; cada coluna (menos a chave) é INFORMAÇÃO revisável; chave opcional bloqueia; alvo mudou → decisão não vale | OK |
| A obrigatoriedade chega à janela Desenvolver (orquestrador) | OK — achado na interface, ver §4 |
| Geração com "Morte" opcional (`str \| None`, `_date_if_present`, `REQUIRED_FIELDS`, `required=False`, manifesto 1.5.0) | OK |
| Genérico: a mesma decisão em "Nacionalidade"; nenhum nome de coluna escrito no código do gerador/planejador | OK |
| Legado: sem decisão ou com `REQUIRED` explícito, o Python é byte a byte o de antes (configuração 1.2.0, manifesto 1.4.0) | OK |
| Mudar a obrigatoriedade deixa o código e o gate anteriores desatualizados | OK |
| Apêndice "Dados do processo" do PDD | OK |
| Configuração estrita (opcional sem motivo, chave opcional, 1.3.0 sem coluna opcional) | OK |
| Python real: Morte ausente/em branco → `None`; presente → data; inválida → `DATA_INCOMPLETA_OU_AMBIGUA`; ambígua → `TARGET_AMBIGUOUS`; Nascimento ausente/em branco → recusa; Nacionalidade em branco → `DADO_OBRIGATORIO_VAZIO:nationality` sem conteúdo; legado → Morte ausente recusa | OK |
| Planilha pelo framework: célula Morte verdadeiramente vazia, cabeçalho igual, sem célula extra; `missing_birth_date`, `date_invalid_death_date` (inclusive "N/A"); V4/V5/VQ PASS; controle negativo `OUTPUT_REQUIREMENT_MISMATCH` | OK |
| Chromium + cópia temporária do laboratório: Ana/Gina (falecidas) → data; Bruno (vivo) → célula vazia; legado recusa Bruno; sem Nascimento, Morte inválida ou Morte dupla → nenhuma planilha (tudo ou nada); nenhum valor nos eventos | OK |

## 3. Homologação pela interface REAL do Studio (G6)

Roteiro `CodegenQuality001Homologation` com `-Dnekkora.homolog.optionalDeath=true`, diretórios isolados
(`~/.nekkora-oc-t3`), Robot + Chrome for Testing + extensão; operador TEST_AGENT. A automação de biografias é GRAVADA DE
NOVO (a base antiga não é tocada), o PDD é revisado, e na janela Desenvolver automação → Pendências a coluna "Morte" é
declarada **Opcional: o dado não existe em alguns itens** (clique real). O teste autorizado roda com cinco artigos da
Wikipédia: os quatro de antes (falecidos) e **Fernanda Montenegro** (viva: o quadro não tem a linha "Morte").

**Resultado: 105 verificações, 0 falhas** (3ª execução; ver §4). Entre elas:

- antes da decisão: 5 informações "obrigatória (padrão, sem decisão)" e o código exige "Morte";
- depois: "Morte" opcional com o motivo, as outras quatro obrigatórias; nova revisão da especificação (o código anterior
  não vale) e código de novo `CODE_VALIDATED` (V4/V5/VQ);
- `main.py` com `required=False` só em "Morte"; `ProfileData.death_date: str | None`; `REQUIRED_FIELDS` sem `death_date`;
  manifesto 1.5.0 com a regra por coluna;
- apêndice "Dados do processo" do PDF diz "OPCIONAL (o dado não existe em alguns itens): sem o dado, a célula fica vazia";
- cancelar o teste autorizado não grava planilha; o teste completo grava e confere a planilha; V6 comprovada;
- planilha (conferência por tipos, openpyxl): cabeçalho "Morte" preservado, 5 linhas, nenhuma célula extra; pessoa viva →
  célula Morte VAZIA e Nascimento como data; falecidas → Morte AAAA-MM-DD; obrigatórias preenchidas.

**Conferidor independente** (reabre o .xlsx e compara com os dados visíveis de cada página, lidos à parte):
**29/30 células**. Morte da pessoa viva: OK (vazia = página sem a linha). A célula divergente é o "Nome completo" da
pessoa viva — ver achado F-OC-01 em [decisions.md](decisions.md). Não foi relacionada à opcionalidade e o conferidor NÃO foi
ajustado para passar.

## 4. Instabilidades e achados (separados de defeito de negócio)

| Execução | O que houve | Tratamento |
|---|---|---|
| Interface, 1ª | Pendências sem as informações de obrigatoriedade: o orquestrador só levava AVISOS do gate para a janela | **defeito real**, corrigido no `6828edc` + teste no nível do orquestrador (`chegaAJanela`) |
| Interface, 2ª | 103/105: duas verificações antigas da CQ-001 exigiam o manifesto 1.4.0 (com coluna opcional ele é 1.5.0, como especificado) | expectativa do roteiro ajustada só no modo da missão |
| Laboratório (teste) | `http.server` do Python travou depois de execuções seguidas do Chromium (navegação inicial esgotou o tempo) | infraestrutura do teste: um servidor por execução |
| Suíte Linux, 1ª | interrompida por falta de memória na máquina | rodada de novo depois |

## 5. O que NÃO foi feito / não executado

- Nenhum push, PR remoto, merge, tag, release, publicação ou mudança de visibilidade.
- O framework não foi alterado nem republicado.
- Emissor tabular (RPA Challenge) não muda (sem planilha de saída por colunas): não reexecutado nesta missão.
- Wikipédia real: uma execução completa por rodada do roteiro (três no total, por causa do defeito e da expectativa
  acima), só pesquisa e leitura, sem conta.
