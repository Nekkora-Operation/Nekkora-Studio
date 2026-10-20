# NEKKORA-PACKAGING-001 — G0: auditoria inicial (2026-10-20)

## Baselines

| Repositório | `main` | Branch da missão | Cópia de segurança local |
|---|---|---|---|
| Nekkora-RPA-Framework | `415004f` (merge do PR #4, E2E-002) | `feat/nekkora-packaging-001` | git bundle verificado (sem credenciais) |
| Nekkora-Studio | `75ced49` (merge do PR #21, C12 da E2E-002) | `feat/nekkora-packaging-001` | git bundle verificado (sem credenciais) |

PRs recentes, todos mergeados: Framework #4; Studio #20, #21; Extensão #15, #16. Não há PR aberto da E2E-002.

Testes de referência (mesma árvore de código): Framework 354 (Windows) / 353 + 1 pulado (Linux), 30/30 mutações;
Studio 765 (Windows) / 703 + 9 pulados (Linux).

## Inventário

| Item | Estado | Observação |
|---|---|---|
| `pyproject.toml` (setuptools, `nekkora-rpa`, versão dinâmica de `_version.py`, Python ≥ 3.12) | EXISTE | `dependencies = ["playwright>=1.63,<2"]`; extra `excel = ["openpyxl>=3.1.5,<4"]`; extra `dev` |
| `src/nekkora_rpa/_version.py` = `0.3.0` | EXISTE | nenhuma versão publicada em índice |
| `py.typed` + `contracts/*.json` (12 esquemas) no `package-data` | EXISTE | conferir no wheel e no sdist (G1) |
| `LICENSE` | **BLOQUEADO (P1)** | "Todos os direitos reservados… nenhuma licença de uso, cópia, modificação ou distribuição" |
| Classifier `Private :: Do Not Upload` | **BLOQUEADO (P1)** | o PyPI recusa o envio enquanto existir — mantido |
| `constraints/lab.txt` e `dev.txt` (versões travadas) | EXISTE | sem hashes |
| Instalação documentada | PARCIAL | README ainda manda instalar `nekkora_rpa-0.1.0-py3-none-any.whl` |
| Teste e2e com wheel em venv limpa | EXISTE | `tests/e2e/test_homologacao.py` (só wheel; o sdist é verificado à mão) |
| CI (`.github/workflows`) | **AUSENTE** | nenhum workflow |
| Pipeline de release / Trusted Publishing | **AUSENTE** | — |
| Tags/releases | AUSENTE | nenhuma tag no repositório |
| Nome `nekkora-rpa` no PyPI/TestPyPI | **BLOQUEADO (P2)** | API JSON e `simple` respondem 404 nos dois índices (também `nekkora_rpa`, `nekkora`): **nenhum projeto público visível — isso NÃO prova que o nome está livre** (projeto sem versões ou nome proibido também dão 404); a posse só se confirma pela conta titular |

### Studio

| Item | Estado | Observação |
|---|---|---|
| Requisito por contrato do gerador | EXISTE | tabular `codegen-config 1.0.0` → `nekkora-rpa[excel]==0.2.0` (`PythonGenerator`); execução única `1.1.0` → `nekkora-rpa==0.2.0` (`SingleGenerator`); moderna/LIST `1.2.0` → `nekkora-rpa[excel]==0.3.0` |
| `requirements.txt`/README gerados | PARCIAL | instruem wheel local (`<nekkora_rpa-0.2.0-…whl>`, `<…0.3.0…whl>`) e `-c constraints/lab.txt` do checkout do framework |
| Escolha do Python (`PythonChecks.resolve`) | PARCIAL | configurado → `NEKKORA_PYTHON` → `.venv` da pasta gerada; aceita o 1º que importar `nekkora_rpa`, ruff e mypy, **de qualquer versão** |
| Catálogo de capacidades | PARCIAL | `nekkora-rpa-0.2.0.json` (commit `23cc6de`) e `0.3.0.json` (commit `2f36a0a`) vão no jar, mas só o **0.3.0** é carregado (`bundled()`): um projeto 0.2.0 é planejado e conferido contra o catálogo 0.3.0 |
| Versão do ambiente × requisito do projeto | PARCIAL | só "Executar teste autorizado" compara `catalog.version()` com o `nekkora_rpa` do Python; V4/V5 rodam com qualquer versão |
| Gestor de dependências (resolver Python, venv por projeto, plano, consentimento, instalação, verificação) | **AUSENTE** | hoje a pessoa monta o Python à mão (clone + wheel local + ruff + mypy) e o indica em Opções avançadas |
| Sonda real (`FrameworkCapabilityCatalog.compare(Probe)`) | EXISTE | usada em teste (`sondaReal`); não é usada na interface |
| Python no Windows de homologação | EXISTE | 3.12.10 (`py -3.12`), **sem** `nekkora_rpa` — serve à homologação "sem pacote pré-instalado" |

## Matriz modo × versão × extras × capacidades × ambiente

| Modo do processo | Contrato do gerador | Requisito | Extras | Capacidades do catálogo | Commit do catálogo | Ferramentas de verificação |
|---|---|---|---|---|---|---|
| Lote tabular (E2E-001, planilha de entrada declarada) | `codegen-config 1.0.0` | `nekkora-rpa==0.2.0` | `excel` | 26 (0.2.0) | `23cc6de` | ruff 0.17.1, mypy 2.4.0 |
| Execução única (STUDIO-014) | `codegen-config 1.1.0` | `nekkora-rpa==0.2.0` | — | 26 (0.2.0) | `23cc6de` | idem |
| Execução única moderna / lista → planilha (E2E-002) | `codegen-config 1.2.0` | `nekkora-rpa==0.3.0` | `excel` | 36 (0.3.0) | `2f36a0a` | idem |

Ambiente: Windows (desktop: Studio, Recorder, teste autorizado com Chrome visível); Linux só ferramentas de
validação e testes. Navegador do teste autorizado: Chrome instalado (`channel=chrome`), sem `playwright install`.

## Riscos

1. Publicar sem decisão de licença expõe código "todos os direitos reservados" sem conceder uso — **bloqueado por P1**.
2. Nome no índice não confirmado; publicar com nome de terceiro ou sujeito a confusão de dependência — **P2**.
3. Instalar dependências transitivas de índice público sem hash → troca de pacote; mitigação: lock com hashes.
4. Catálogo 0.3.0 validando projeto 0.2.0 (e vice-versa) — corrigir no G4.
5. venv dentro de pasta sincronizada (OneDrive) ou do checkout — a venv fica na área de dados do Studio.

## Decisões da titular (não inferidas)

- **P1** distribuição/licença; **P2** conta e nome no PyPI/TestPyPI; **P3** aprovação de staging e de produção.
  Nenhuma delas é necessária para IMPLEMENTED_LOCAL; publicação e mudança de licença/classifier ficam para depois.

## Estratégia de versão

0.3.0 é inédita em índice e continua candidata; nenhuma API muda por causa do pipeline. 0.2.0 continua servida aos
projetos legados (não migra em silêncio).

## Plano de diffs

- **Framework**: CI por PR (Windows + Linux); verificação de distribuição (wheel + sdist em venvs limpas fora do
  checkout, conteúdo, metadados, hashes); lock com hashes das dependências; workflow de release isolado
  (TestPyPI/PyPI, ambientes com aprovação, OIDC só no upload, guarda que recusa enquanto P1/P2/P3 abertos);
  documentação. **Sem** mudar LICENSE, classifier ou versão.
- **Studio**: Dependency Manager (resolvedor de Python, plano e política, instalador sem shell, verificação de
  integridade e sonda, apresentação com consentimento), catálogo pela versão INSTALADA, pré-voo em Desenvolver/Validar,
  bloqueio de V6 com ambiente divergente, `requirements.txt`/README gerados sem wheel local como único caminho.
