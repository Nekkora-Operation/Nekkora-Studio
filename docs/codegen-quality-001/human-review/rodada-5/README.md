# Revisão humana — rodada 5 (parecer independente da rodada 4: CQ-R5-01…CQ-R5-03 e `article_url`)

**Estado: AWAITING_HUMAN_READABILITY.** Só a titular encerra esta revisão, lendo estes arquivos e declarando
explicitamente "está entendivel". Testes verdes, a camada VQ ou a opinião do agente NÃO substituem esse aceite.

- Arquivos ORIGINAIS gerados pela interface real do Studio, copiados byte a byte (nada reformatado nem editado à mão).
  SHA-256 em `*/SHA256SUMS.txt` e no `manifest.json` de cada projeto.
- Studio: branch `feat/codegen-quality-001`, commit `838e86b` (só local; nada enviado a repositório remoto, nada
  mergeado, nada publicado no PyPI/TestPyPI). Framework, extensão e Agent sem mudança.
- Glossário de nomes `nekkora-studio/naming-pt-en/1.2.0` · manifesto 1.4.0 nas quatro amostras.
- Princípio desta rodada (da titular): *"Nekkora owns the code conventions. Customers own their data contracts."* —
  nomes do código mudam pelo glossário governado; colunas, cabeçalhos, rótulos da página, segredos e códigos de recusa não.

## Onde ler

| Pasta | O quê | Como foi gerada |
|---|---|---|
| `final/` | Wikipédia (lista → planilha), nekkora-studio-codegen/1.2.0, `nekkora-rpa[excel]==0.3.0` | gravação real → PDD → "Desenvolver automação" (IA real) → código → teste autorizado (89/89 verificações) |
| `desenvolver/` | a mesma automação, primeira geração da sessão | idem; difere de `final/` só no manifesto (revisão da automação) |
| `the-internet/` | login/área segura (execução única), nekkora-studio-codegen/1.1.0, `nekkora-rpa==0.2.0` | roteiro dos sites de treinamento pela interface (12/12) |
| `rpa-challenge/` | planilha → formulário, uma transação por linha, nekkora-studio-codegen/1.3.0, `nekkora-rpa[excel]==0.2.0` | modelo gravado no site real na E2E-001, aberto pela interface (Ctrl+O) e gerado pelo menu **Automação → Avançado → Gerador avançado (configuração manual)…** (27/27) |
| `evidencias/` | logs das homologações, conferidor, execuções no site real e na réplica, suítes, sondas | — |

Para ler o código: `automation/selectors.py` (chaves e localizadores) → `automation/pages.py` (telas) →
`automation/process.py` (ordem e regras) → `main.py` (linha de comando). `README.md` de cada projeto explica o resto.

## Resposta ao parecer

| Item | O que mudou (no gerador) | Onde ver |
|---|---|---|
| CQ-R5-01 | Toda dataclass com valores de entrada ou de leitura sai com `@dataclass(frozen=True, repr=False)` (nomes, tipos e interface iguais). A VQ bloqueia `@dataclass` sem `repr=False` (`DATACLASS_REPR_COM_VALORES`); testes com valores-sentinela no Python real nos dois emissores, e a mesma sonda nas amostras desta pasta | `rpa-challenge/automation/process.py:25`, `final/automation/pages.py:57`, `evidencias/repr-sentinelas.txt` |
| CQ-R5-02 | Chave do clique que baixa a planilha pelo EFEITO: `PREPARATION_DOWNLOAD_SPREADSHEET` (igual ao método `download_spreadsheet` e ao `step_id`). Regra geral: token de ícone de fonte (`cloud_download`, `arrow_forward`) sai do NOME quando há outra palavra. O texto real da página continua no XPath, role/name e identidade | `rpa-challenge/automation/selectors.py:18`, `rpa-challenge/automation/selectors.py:34` |
| CQ-R5-02 | Chave renomeada nunca some em silêncio: gerando sobre um projeto anterior, o manifesto declara a chave antiga em `previousTargetKeys`. Gerado sobre a amostra da rodada 4: só a chave do download ganhou o vínculo (`← process.preparation.download_excel_cloud_download`); sobre o projeto da E2E-001 (gerador 1.0.0): as 10 chaves em português. Projeto gerado do zero não tem o campo; o `step_id` atribuído não muda | `evidencias/rpa-challenge/manifest-regerado-sobre-rodada-4.json`, `…-sobre-e2e-001.json` |
| `article_url` | Glossário 1.2.0: `endereco do artigo` → `article url`. `InputRow.article_url`; a coluna da lista continua "endereco_do_artigo". O endereço da página atual segue o vocabulário do framework (`current_address`/`same_address`) e fica `page_address` | `final/automation/process.py:33`, `final/automation/process.py:40` |
| CQ-R5-03 | A rodada 4 registrou `linhas: 999` (oficial-1) e `linhas_com_dados: 10` (oficial-2) para o MESMO arquivo: a 1ª versão do roteiro contava todas as linhas depois do cabeçalho, vazias incluídas; o roteiro foi corrigido entre as duas execuções e a diferença não foi explicada. Agora cada resumo registra as três contagens com o significado escrito no próprio arquivo: o arquivo declara 1000 linhas (1000 lidas com o cabeçalho, ou seja 999 depois dele), 10 têm dados = 10 transações esperadas. Planilha e código não mudaram por causa do resumo | `evidencias/rpa-challenge/site-real/oficial-1/resumo.json` |

## Evidências (resultados desta rodada)

| Verificação | Resultado |
|---|---|
| Suítes completas no commit `838e86b` | Windows: 767 testes, 0 falhas, 0 erros, 0 pulados, 767 aprovados (117 classes) · Linux: 767 testes, 0 falhas, 0 erros, 24 pulados, 743 aprovados (117 classes) (pulados com motivo em `evidencias/suites/linux.md`) |
| Wikipédia pela interface real (1ª tentativa) | 89/89 |
| Conferidor independente da planilha (reabre o .xlsx e compara com a página) | 24/24 células, cabeçalhos ok: True, fórmulas: 0 |
| The Internet pela interface real | 12/12, execução COMPLETED |
| RPA Challenge pela interface real (abrir, gerar, determinismo, proteção, regerar sobre E2E-001 e sobre a rodada 4, V4/V5/VQ) | 27/27 |
| RPA Challenge — código gerado no **site real**, `oficial-1` | 10/10 transações SUCCEEDED · placar da página: "Your success rate is 100% ( 70 out of 70 fields) in 2694 milliseconds" · valores da planilha nas evidências: 0 |
| RPA Challenge — código gerado no **site real**, `oficial-2` | 10/10 transações SUCCEEDED · placar da página: "Your success rate is 100% ( 70 out of 70 fields) in 2519 milliseconds" · valores da planilha nas evidências: 0 |
| Réplica do laboratório (amostra de teste do gerador, oráculo do servidor) | 10 envios, rodadas em ordem: True, todos corretos: True |
| Conferência estática desta pasta (hashes, AST, SelectorKey/TARGETS ↔ selectors.json, final ↔ desenvolver, repr) | `evidencias/conferencia-estatica.txt` (23/23) |
| Sonda de sentinelas no `repr` das amostras (Python real) | nenhum valor mostrado; campos guardam o valor (`evidencias/repr-sentinelas.txt`) |

## Decisões pendentes da titular

1. Colunas opcionais ("Morte" para pessoas vivas) — proposta NEKKORA-SPEC-OPTIONAL-COLUMNS-001 (fora desta missão).
2. Publicação: Studio continua só local (repositório público); nenhum push sem autorização específica.
3. Se `article_url` não era o que a frase de princípio autorizava: desfazer é tirar uma linha do glossário (1.2.0 → nova versão).

Decisões técnicas desta rodada: `docs/codegen-quality-001/decisoes.md`, D-CQ-45…D-CQ-49.
