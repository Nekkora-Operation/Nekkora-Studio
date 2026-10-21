# Revisão humana — rodada 4 (parecer independente da rodada 3: R4-01…R4-05 e Q12)

**Estado: AWAITING_HUMAN_READABILITY.** Só a titular encerra esta revisão, lendo estes arquivos e declarando
explicitamente "está entendivel". Testes verdes, a camada VQ ou a opinião do agente NÃO substituem esse aceite.

- Arquivos ORIGINAIS gerados pela interface real do Studio, copiados byte a byte pelos roteiros de homologação (nada
  reformatado nem editado à mão). SHA-256 em `*/SHA256SUMS.txt` e no `manifest.json` de cada projeto.
- Studio: branch `feat/codegen-quality-001`, commit `886850c` (só local; nada enviado a repositório remoto, nada
  mergeado, nada publicado no PyPI/TestPyPI). Framework, extensão e Agent sem mudança.
- Glossário de nomes `nekkora-studio/naming-pt-en/1.1.0` · manifesto 1.4.0 nas quatro amostras.

## Onde ler

| Pasta | O quê | Como foi gerada |
|---|---|---|
| `final/` | Wikipédia (lista → planilha), nekkora-studio-codegen/1.2.0, `nekkora-rpa[excel]==0.3.0` | gravação real → PDD → "Desenvolver automação" (IA real) → código → teste autorizado (87/87 verificações) |
| `desenvolver/` | a mesma automação, primeira geração da sessão | idem; difere de `final/` só no manifesto (revisão da automação) |
| `the-internet/` | login/área segura (execução única), nekkora-studio-codegen/1.1.0, `nekkora-rpa==0.2.0` | roteiro dos sites de treinamento pela interface (12/12) |
| `rpa-challenge/` | **Q12** — planilha → formulário, uma transação por linha, nekkora-studio-codegen/1.3.0, `nekkora-rpa[excel]==0.2.0` | modelo gravado no site real na E2E-001, ABERTO pela interface (Ctrl+O) e gerado pelo menu **Automação → Avançado → Gerador avançado (configuração manual)…** (20/20) |
| `evidencias/` | logs das homologações, conferidor, execuções no site real e na réplica, suítes | — |

Para ler o código: `automation/selectors.py` (chaves e localizadores) → `automation/pages.py` (telas) →
`automation/process.py` (ordem e regras) → `main.py` (linha de comando). `README.md` de cada projeto explica o resto.

## Resposta ao parecer

| Item | O que mudou (no gerador) | Onde ver |
|---|---|---|
| R4-01 | Coluna "Reading" e descrições de localizador em inglês ("stable container + attribute", "row anchored by its heading …"); menus do Studio citados ficam como estão e marcados como menu | `final/README.md:30`, `the-internet/README.md:32` |
| R4-01 | `sem_provedor_de_segredos` → `secret_provider_missing`: não era contrato (nenhum consumidor), e é o MESMO código que o Engine do nekkora-rpa usa (D-CQ-36) | `the-internet/automation/process.py:19` |
| R4-02 | Glossário 1.1.0 com contexto de DATA: `birth_date`, `death_date` (campo, chave de alvo, `step_id`, coluna da planilha); cabeçalhos "Nascimento"/"Morte" iguais | `final/automation/pages.py:66`, `final/automation/process.py:59`, `final/main.py:34` |
| R4-02 | `article_address` **mantido** — `article_url` aguarda a sua aprovação (é uma linha do glossário) | `final/automation/process.py:33` |
| R4-03 / Q12 | Emissor tabular no formato da rodada 3: `SelectorKey`, telas pelo nome da sessão em inglês, `InputRow` com os cabeçalhos como contrato, `submit_record(*, …)`, `step_id` legível, `--output` (com `--saida` aceito) | `rpa-challenge/automation/pages.py:78`, `rpa-challenge/automation/process.py:26`, `rpa-challenge/main.py:48` |
| R4-04 | Testes NEGATIVOS da espera do Enter com o código gerado de verdade: sem troca de endereço, endereço inesperado, chegada depois da espera → erro classificado, nenhuma extração, planilha não gravada | `evidencias/suites/negativos-enter-windows.txt` |
| R4-04 | Suítes completas no commit `886850c` | Windows: 763 testes, 0 falhas, 0 erros, 0 pulados, 763 aprovados (116 classes) · Linux: 763 testes, 0 falhas, 0 erros, 22 pulados, 741 aprovados (pulados com motivo em `evidencias/suites/linux.md`) |
| R4-05 | "Morte" continua obrigatória; nada relaxado — NEKKORA-SPEC-OPTIONAL-COLUMNS-001 | `final/main.py:35` |

## Evidências (resultados desta rodada)

| Verificação | Resultado |
|---|---|
| Wikipédia pela interface real (1ª tentativa) | 87/87 |
| Conferidor independente da planilha (reabre o .xlsx e compara com a página) | 24/24 células, cabeçalhos ok: True, fórmulas: 0 |
| The Internet pela interface real | 12/12, execução COMPLETED |
| RPA Challenge pela interface real (abrir, gerar, determinismo, proteção, regerar sobre o projeto anterior, V4/V5/VQ) | 20/20 |
| RPA Challenge — código gerado no **site real**, `oficial-1` | 10/10 linhas SUCCEEDED · placar da página: "Your success rate is 100% ( 70 out of 70 fields) in 2791 milliseconds" · valores da planilha nas evidências: 0 |
| RPA Challenge — código gerado no **site real**, `oficial-2` | 10/10 linhas SUCCEEDED · placar da página: "Your success rate is 100% ( 70 out of 70 fields) in 2714 milliseconds" · valores da planilha nas evidências: 0 |
| Réplica do laboratório (amostra de teste do gerador, oráculo do servidor) | 10 envios, rodadas em ordem: True, todos corretos: True |
| Testes negativos do Enter (Windows) | 3/3 |

Regerar o RPA Challenge sobre a pasta gerada ANTES (E2E-001, `paginas.py`/`processo.py`): os arquivos antigos foram
movidos intactos para `previous-generation/` (RETIRED), com o manifesto anterior, e o resultado ficou byte a byte
igual ao gerado do zero. Projetos anteriores continuam executáveis como estão (sem migração automática).

## Decisões pendentes da titular

1. `article_url` no lugar de `article_address` (R4-02) — recomendado pelo parecer; não aplicado sem a sua aprovação.
2. Colunas opcionais ("Morte" para pessoas vivas) — proposta NEKKORA-SPEC-OPTIONAL-COLUMNS-001.
3. Publicação: Studio continua só local (repositório público); nenhum push sem autorização específica.

Decisões técnicas desta rodada: `docs/codegen-quality-001/decisoes.md`, D-CQ-35…D-CQ-44.
