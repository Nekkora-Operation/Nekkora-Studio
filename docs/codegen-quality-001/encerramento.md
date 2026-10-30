# NEKKORA-CODEGEN-QUALITY-001 — Termo de aceite e encerramento local

Data: 21/10/2026 · Branch `feat/codegen-quality-001` (só local) · base `main` = `1cbf669`

Estado: **COMPLETED_LOCALLY / APPROVED_BY_OWNER / REMOTE_PUBLICATION_BLOCKED**

## Decisão da titular

A titular leu a rodada 5 e aprovou a etapa. Declaração exata na conversa:

> "Honestamente, eu achei que o resultado ficou muito bom, dessa forma ficou mais simples de entender, além de que mesmo
> que esse seja um projeto com poucas regras de negócio, ele cumpriu bem o objetivo, considero sim, aprovado essa etapa"

**HUMAN_READABILITY_ACCEPTED: SATISFEITO** por aprovação humana explícita. A titular **não** digitou a frase literal
"está entendivel" (o critério combinado): o aceite foi dado com outras palavras, de forma inequívoca, e reconhecido no
termo de aceite que ela entregou. O aceite cobre a legibilidade do código da rodada 5. Não é aceite de testes novos nem
autorização de Git remoto.

## Baseline aceito

| Item | Valor |
|---|---|
| Commit do gerador | `838e86b` feat(codegen): rodada 5 — repr sem valores, chave pelo efeito e article_url |
| Commit da pasta de revisão | `9a842b9` test(codegen-quality): rodada 5 pela interface real + pasta de revisão humana |
| Amostras | `human-review/rodada-5/` — Wikipédia (`final/`, `desenvolver/`), The Internet, RPA Challenge |
| Layout gerado | `automation/selectors.py`, `automation/pages.py`, `automation/process.py`, `main.py`, `selectors.json` (runtime 0.2.0/0.3.0) |
| Idioma | identificadores, docstrings e README técnico em inglês (glossário `nekkora-studio/naming-pt-en/1.2.0`); contratos do cliente literais |
| Geradores nas amostras | `nekkora-studio-codegen/1.1.0` (the-internet), `1.2.0` (Wikipédia), `1.3.0` (tabular) |

Os manifestos não guardam commit do Studio (por desenho: guardam revisões da automação, do plano e da configuração); o
commit testado está no README da rodada e nos relatórios de suíte.

## Gates

| Gate | Estado | Base |
|---|---|---|
| Implementação (gerador, IR, VQ, contratos V5) | SATISFEITO | rodadas 1–5, decisões D-CQ-01…D-CQ-49 |
| Revisão técnica independente | SATISFEITO | parecer da rodada 5: aprovação técnica sem pedir rodada 6 |
| HUMAN_READABILITY_ACCEPTED | SATISFEITO | declaração da titular acima |
| Publicação remota | BLOQUEADO | Studio e Agent continuam públicos no GitHub (verificado pela titular em 21/10/2026) |

## Testes: executados × reportados

**Executados por mim** no commit `838e86b` (logs em `human-review/rodada-5/evidencias/suites/`):

| Ambiente | Testes | Falhas | Pulados | Aprovados |
|---|---|---|---|---|
| Windows 11, JDK 21, Python do framework | 767 | 0 | 0 | 767 |
| Linux (contêiner `golden-linux.sh verify`) | 767 | 0 | **24** | 743 |

Os 24 pulados do Linux **não** contam como aprovados: 23 dependem do Python do framework (ausente no contêiner) e 1
(`AtomicSaveTest`) não se aplica a root. Esses 23 rodaram e passaram no Windows.

Homologações pela interface real (executadas por mim): Wikipédia 89/89 (1ª tentativa) + conferidor independente 24/24
células; The Internet 12/12 (COMPLETED); RPA Challenge 27/27; site real rpachallenge.com 2× 10/10, placar 100% (70/70);
réplica do laboratório 10/10; conferência estática da pasta 23/23 (refeita sobre o ZIP).

**Reportados pela revisão independente** (feitos por ela sobre o ZIP, sem reexecutar suítes, navegador nem Studio):
82 arquivos sem duplicatas; SHA256SUMS 40/40; hashes dos manifestos 36/36; SelectorKey/TARGETS ↔ selectors.json 30/30;
20/20 módulos pela AST; `final/` = `desenvolver/`; 1 vínculo `previousTargetKeys` (rodada 4) e 10 (E2E-001).

## Pendências transferidas (fora deste aceite)

- **NEKKORA-SPEC-OPTIONAL-COLUMNS-001** — opcionalidade declarada e revisada no PDD, da leitura até o XLSX. "Morte"
  continua obrigatória na base homologada até o novo contrato ser aprovado e testado.
- Integração Studio↔Agent: não começa por causa deste aceite.
- Agent↔Platform (trabalhos, logs, métricas): missão de integração operacional; nenhum canal Studio↔Platform.
- Push, PR remoto, merge, tag, release, PyPI/TestPyPI e mudança de visibilidade: decisão separada da titular.

Rascunho local do PR (não aberto; a branch nunca foi enviada ao remoto): [pr-rascunho.md](pr-rascunho.md).
