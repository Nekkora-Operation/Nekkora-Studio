# NEKKORA-PACKAGING-001 — distribuição do framework e ambiente Python por automação

Missão em dois repositórios, PRs separados e **sem merge** (a titular decide):

| Repositório | Branch | PR |
|---|---|---|
| Nekkora-RPA-Framework | `feat/nekkora-packaging-001` | #5 — CI, verificação de distribuição, locks por hash, release bloqueado por guardas |
| Nekkora-Studio | `feat/nekkora-packaging-001` | #22 — Dependency Manager, ambiente por automação, arquivos gerados sem wheel local |

A extensão Chrome não muda (nenhuma dependência objetiva).

## Estado (vocabulário da missão)

| Item | Estado |
|---|---|
| Framework: wheel + sdist verificados, CI Windows + Linux por PR, locks com SHA-256 | **IMPLEMENTED_LOCAL** (CI verde no PR #5) |
| Framework: workflow de release por Trusted Publishing (TestPyPI → PyPI) | **BLOCKED_AWAITING_OWNER** — pronto, recusa por guarda enquanto licença/nome/OIDC/aprovações estiverem abertos |
| Studio: Dependency Manager + "Preparar ambiente" com consentimento + V4/V5 na versão exata | **IMPLEMENTED_LOCAL** — homologado pela interface (ver [homologation.md](homologation.md)) |
| Publicação no TestPyPI / PyPI | **não realizada** — nenhuma URL de índice existe; nada foi enviado |

## Como funciona (Studio)

1. **Desenvolver automação** gera o código e lê do `manifest.json` gerado o requisito EXATO do contrato:
   `nekkora-rpa==0.2.0` (execução única 1.1.0), `nekkora-rpa[excel]==0.2.0` (lote tabular 1.0.0) ou
   `nekkora-rpa[excel]==0.3.0` (lista → planilha 1.2.0). Nada migra de versão em silêncio.
2. O ambiente da automação é procurado sem instalar e sem rede: Python indicado em Opções avançadas (sempre conferido
   contra o requisito) → venv da automação → `NEKKORA_PYTHON`/`.venv` só se a versão conferir. Faltando, a pendência
   **Ambiente Python da automação** (aviso) explica o diagnóstico e oferece **Preparar ambiente…**.
3. **Preparar ambiente…** mostra o plano (pacote, versão, extras, origem homologada com commit e SHA-256, índice das
   dependências, Python base, destino, o que NÃO é enviado) e só instala com a confirmação da pessoa.
4. A instalação roda fora da interface (progresso por etapa, Cancelar), sem shell, com
   `--isolated --require-hashes --only-binary=:all:`: o wheel do framework vem embutido no Studio (reprodutível,
   SHA-256 conferido antes de usar); dependências e ferramentas (ruff, mypy) vêm do PyPI oficial travadas por hash.
5. Verificação completa (versão, import a partir da própria venv, 12 esquemas, extra excel, ruff, mypy, `pip check`,
   sonda contra o catálogo **da mesma versão**). Só então `ambiente.json` é gravado e o Studio desenvolve de novo:
   V4/V5 passam a valer para aquela versão.

Venvs ficam em `<dados do Studio>/ambientes/<automação>/<versão-extras>/venv` — nunca no Python global, na pasta do
projeto (que pode estar no OneDrive) nem no checkout de um repositório.

## Documentos

- [g0-auditoria.md](g0-auditoria.md) — baseline, inventário, matriz modo × versão, riscos.
- [decisions.md](decisions.md) — decisões técnicas e o que ficou para a titular.
- [security.md](security.md) — cadeia de suprimentos, consentimento, segredos.
- [homologation.md](homologation.md) — homologação real pela interface + Linux.
- [known-limitations.md](known-limitations.md) — limites conhecidos.
- Framework: `docs/distribuicao.md` (CI, locks, reprodutibilidade, release, ações da titular).

## Ações da titular (nenhuma foi feita pelo agente)

| # | Ação | Onde | Desbloqueia |
|---|---|---|---|
| a | Decidir a licença de distribuição (hoje "todos os direitos reservados") e, se aprovada, retirar `Private :: Do Not Upload` e definir `NEKKORA_LICENCA_APROVADA=<versão>` no repositório | LICENSE, `pyproject.toml`, variáveis do GitHub | qualquer publicação |
| b | Confirmar/criar o projeto `nekkora-rpa` com a conta titular no TestPyPI e no PyPI (404 público não prova que o nome está livre) | test.pypi.org / pypi.org | publicação |
| c | Registrar o Trusted Publisher: repositório `Nekkora-Operation/Nekkora-RPA-Framework`, workflow `release.yml`, environments `testpypi` e `pypi` (pending publisher não reserva o nome) | PyPI/TestPyPI → Publishing | upload por OIDC |
| d | Criar os GitHub Environments `testpypi` e `pypi` com revisores obrigatórios; aprovar o staging | GitHub → Settings → Environments | TestPyPI |
| e | Criar a tag `v<versão>` e aprovar a produção depois do staging verificado | GitHub | PyPI |

Depois da publicação aprovada, o Studio pode passar a usar o índice (`Source.INDEX`) em vez do wheel embutido — mesma
verificação por hash; nenhuma mudança de contrato.
