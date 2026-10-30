# S0 — Preservação e inventário (NEKKORA-STUDIO-INTEGRATION-CONSOLIDATION-001)

Levantado em 29/10/2026, antes de qualquer alteração. Repositório local: `Nekkora-Studio`, um único worktree e nenhum submódulo.

## 1. Estado encontrado

| Item | Valor |
|---|---|
| `main` remota (`origin/main`, após `git fetch`) | `1cbf669cb729b7a4790b66be93b1b5a0fa71ffdb` (merge do PR #23), árvore `8abe57d4…` |
| `main` local | `1cbf669…`, igual à remota |
| HEAD | `da7bb94d9ff5dc42558dba35eee103a1740cd421`, branch `feat/studio-lean-001`, árvore `19973371…` |
| Working tree | limpo: 0 modificados e 0 não rastreados fora do `.gitignore` |
| Stashes | nenhum |
| Submódulos | nenhum |
| Worktrees | só o principal |

O HEAD coincide com a referência `da7bb94` do relatório da PLATFORM-AUTOMATION-LIFECYCLE-001.

## 2. Os 22 commits locais formam uma única cadeia linear

As três missões não estão em branches paralelas. Cada uma parte do fim da anterior, e todas partem da `main` atual, sem nenhum commit atrás dela:

```
1cbf669 (origin/main)
  └─ +15  feat/codegen-quality-001        425be58  (CODEGEN-QUALITY-001)      merge-base 1cbf669
       └─ +5   feat/spec-optional-columns-001  91040a5  (SPEC-OPTIONAL-COLUMNS-001)  merge-base 1cbf669
            └─ +2   feat/studio-lean-001       da7bb94  (STUDIO-LEAN-001)            merge-base 1cbf669
```

Ancestralidade: `425be58 ⊂ 91040a5 ⊂ da7bb94` e `1cbf669 ⊂ 425be58`. Por isso `feat/studio-lean-001` contém as outras duas. Os 22 commits estão a seguir.

| # | Commit | Data | Missão | Assunto |
|---|---|---|---|---|
| 1 | edee926 | 20/10 | CQ | feat(codegen): código legível e rastreável (nomes do PDD, step_id, IR, camada VQ) |
| 2 | 4d27da2 | 21/10 | CQ | test: homologação G8 pela interface + rodada 1 |
| 3 | 5d09790 | 21/10 | CQ | chore: revisão humana sem conversão de fim de linha |
| 4 | 65caaad | 21/10 | CQ | test(pycheck): proveniência ignora ids de transação |
| 5 | b2ed26b | 21/10 | CQ | feat(codegen): rodada 2 (CQ-01…09) |
| 6 | 8c5de7b | 21/10 | CQ | test: rodada 2 pela interface |
| 7 | a3e1a97 | 21/10 | CQ | feat(codegen): rodada 3, código em inglês e chaves tipadas (Q10/Q11) |
| 8 | 56ed279 | 21/10 | CQ | fix(codegen): Enter que troca de documento confirma pela troca de endereço |
| 9 | 81bc9c8 | 21/10 | CQ | test: rodada 3 pela interface |
| 10 | ad0e3d3 | 21/10 | CQ | feat(codegen): rodada 4, tabular em inglês (Q12) e R4-01/02 |
| 11 | 886850c | 21/10 | CQ | fix(codegen): nome já em inglês não vira pendência |
| 12 | ddd933a | 21/10 | CQ | test: rodada 4 pela interface |
| 13 | 838e86b | 21/10 | CQ | feat(codegen): rodada 5, repr sem valores, chave pelo efeito e article_url |
| 14 | 9a842b9 | 21/10 | CQ | test: rodada 5 pela interface |
| 15 | 425be58 | 21/10 | CQ | docs: fechamento e aceite da titular |
| 16 | 4359c07 | 21/10 | SOC | feat(spec): colunas de saída opcionais |
| 17 | 6828edc | 21/10 | SOC | fix(spec): obrigatoriedade chega à janela Desenvolver |
| 18 | a390664 | 21/10 | SOC | test: homologação pela interface e rodada 1 |
| 19 | 29fdeea | 21/10 | SOC | chore: revisão humana sem conversão de fim de linha |
| 20 | 91040a5 | 21/10 | SOC | docs: encerramento e aceite da titular |
| 21 | 811fedf | 24/10 | LEAN | caches Python fora do jar (PackagedResourcesTest), dependência AI-Engine→guided removida |
| 22 | da7bb94 | 24/10 | LEAN | venv local não versionada da evidência G5 removida |

Diferença entre `1cbf669` e `da7bb94`: 424 arquivos, +44 416 e −1 206 linhas. A maior parte é documentação.

| Área | Arquivos | Linhas adicionadas |
|---|---|---|
| `docs/codegen-quality-001` | 306 | 28 874 |
| `docs/spec-optional-columns-001` | 44 | 3 836 |
| `docs/lean-001` | 5 | 169 |
| `nekkora-studio-codegen/src` | 24 | 4 723 |
| `nekkora-studio-app/src` | 7 | 3 479 (dos quais 3 192 são harnesses de homologação em `src/test`) |
| `Nekkora-AI-Engine/src` | 33 | 3 284 |
| `nekkora-studio-ui/src` | 1 | 9 |
| `Nekkora-AI-Engine/pom.xml`, `.gitignore`, `.gitattributes`, `reports/` | 4 | 42 |

Os 22 commits têm o mesmo e-mail de autor, que já aparece em 166 commits da `main`; não é exposição nova.

## 3. Outras referências fora da `main`

| Referência | Situação | Tratamento |
|---|---|---|
| `archive/pdd01-v3-print-real-local` (`e00d093`, +1) | arquivo local "não publicar" da STUDIO-013 (v3 com print real mascarado, não adotada) | fica fora da integração; preservada no backup |
| `feat/e2e-002-wikipedia-excel` e `origin/…` (`d5f141d`, +1) | mesma mensagem de `e3ebb56`, já na `main` pelo PR #21 e depois editada em `1dd305f` | superada; fica fora |
| `origin/claude/compassionate-knuth-7isa9u` | ancestral da `main` | nada a integrar |
| demais `feat/*`, `chore/*`, `docs/*` | 0 commits fora da `main` | já integradas |

## 4. Arquivos locais fora do controle de versão

`git status --ignored`, sem contar `target/`, `__pycache__/` e `.ruff_cache/`, que são regeneráveis:

- `.env`: configuração local com segredo. Fica onde está, sem ser copiada para o backup nem versionada. Nenhuma operação desta missão a toca.
- `docs/studio-009/evidence/g9/*.log` (9 logs de execução da STUDIO-009, ignorados pelo `.gitignore`): copiados para o backup.

## 5. Preservação

Backup fora do repositório, em `C:\Users\<titular>\nekkora\backup-studio-integration-001\`, que não é versionado:

| Arquivo | SHA-256 |
|---|---|
| `nekkora-studio-todas-refs.bundle` (`git bundle --all`: 52 referências, locais e remotas) | `d871922899a99a82a4df7127e141d7e283215c0bc45cd96c67104725545d0867` |
| `refs.txt` (`git for-each-ref` no momento do backup) | `d8f169ec9c5179efc9cf71f8445ca7a7e327c2020c59c6fa914668e4862409f4` |
| `ignorados-locais/…` (os 9 logs da §4) | listados em `SHA256SUMS.txt` |

Conferências feitas:
- `git bundle verify` OK;
- `sha256sum -c SHA256SUMS.txt` OK;
- um clone a partir do bundle resolve `da7bb94` com a mesma árvore `19973371…`.

**Recuperação:**
```
git clone backup-studio-integration-001/nekkora-studio-todas-refs.bundle studio-recuperado
cd studio-recuperado && git checkout feat/studio-lean-001   # ou qualquer ref de refs.txt
# ou, no repositório existente:
git fetch <caminho>/nekkora-studio-todas-refs.bundle 'refs/heads/*:refs/remotes/backup/*'
```

Nenhuma branch local é apagada ou reescrita. Os comandos `reset --hard` e `clean`, o force-push e a reescrita de histórico não foram usados.

## 6. Dados pessoais no conteúdo a publicar (repositório público)

Onze logs `.txt` de homologação, adicionados por estes commits, contêm caminhos pessoais absolutos: a pasta do usuário, `AppData` e pastas temporárias do agente.

- `docs/codegen-quality-001/evidence/g8/{the-internet,wikipedia}/SITES-homologacao-log.txt`
- `docs/codegen-quality-001/human-review/rodada-{4,5}/evidencias/{the-internet,wikipedia}/SITES-homologacao-log.txt`
- `docs/codegen-quality-001/human-review/rodada-{4,5}/evidencias/rpa-challenge/{CQ4-tabular-homologacao-log,G-resultados-da-geracao}.txt`
- `docs/spec-optional-columns-001/human-review/rodada-1/evidencias/interface/SITES-homologacao-log.txt`

A conferência abrangeu todas as versões intermediárias dos 22 commits, e o conjunto é o mesmo: nenhum outro arquivo teve caminho pessoal em commit algum.

**Decisão da titular (29/10):** a branch do PR reaplica os 22 commits saneados sobre a `main`, na mesma ordem e com o mesmo código. Nos logs, o caminho pessoal é trocado por `%USERPROFILE%` e os `SHA256SUMS` afetados são regenerados. Cada commit leva a linha `Origem-local: <sha>`. As branches locais e o backup não mudam. A conferência das capturas PNG e das planilhas está na S1 (`s1-revisao.md`).
