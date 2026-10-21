# Revisão humana — rodada 3 (CODEGEN-Q10 + CODEGEN-Q11)

**Estado: AWAITING_HUMAN_READABILITY.** Só a titular encerra esta revisão, lendo estes arquivos e declarando
explicitamente "está entendivel". Testes verdes, a camada VQ ou a opinião do agente NÃO substituem esse aceite.

- Arquivos ORIGINAIS gerados pelo Studio (botão "Desenvolver automação", pela interface real), copiados byte a byte
  pelos roteiros de homologação — nada foi reformatado nem editado depois do Studio. SHA-256 em `*/SHA256SUMS.txt`.
- Studio: branch `feat/codegen-quality-001`, commit `56ed279` (só local) · gerador nekkora-studio-codegen/1.2.0 · manifesto
  1.4.0 · glossário `nekkora-studio/naming-pt-en/1.0.0` · requisito `nekkora-rpa[excel]==0.3.0` (framework sem mudança).
- `final/` e `desenvolver/`: Wikipédia (lista → planilha). `the-internet/`: login → área segura → logout (execução única).
- `antes-depois.md`: cada mudança pedida no adendo, com arquivo e linha, antes (rodada 2) e depois (rodada 3).

## Por onde começar

1. `automation/selectors.py` — `SelectorKey`: um nome por elemento da tela, e `TARGETS`: como cada um é localizado.
2. `automation/pages.py` — uma classe por tela; toda chave é `SelectorKey.<NOME>.value`.
3. `automation/process.py` — a ordem das telas, a entrada (coluna da lista → campo) e a linha da planilha.
4. `main.py` — linha de comando (`--input/--spreadsheet/--output`; os nomes de antes continuam aceitos).
5. `manifest.json` → `naming` (de onde veio cada nome), `selectors` (chave ↔ etapa), `trace` (step_id ↔ etapa).

## Árvore — `final/` (Wikipédia)

```
    automation/__init__.py
    automation/pages.py
    automation/process.py
    automation/selectors.py
    config/execution.toml
    main.py
    manifest.json
    README.md
    requirements.txt
    selectors.json
```

## Árvore — `the-internet/`

```
    automation/__init__.py
    automation/pages.py
    automation/process.py
    automation/selectors.py
    config/execution.toml
    main.py
    manifest.json
    README.md
    requirements.txt
    selectors.json
```

## Mapa (Wikipédia)

- **`SearchPage`** — Screen of the PDD section “Pesquisa do artigo”. (nome: PDD_SECTION_TITLE)
  - `search_article(search_term) -> None` — Fills the field with ``search_term`` and submits with Enter.
- **`ProfilePage`** — Screen of the PDD section “Extração dos dados”: checks the page address and reads the profile data. (nome: PDD_DATA_OWNER)
  - `check_page_address(article_address) -> str` — Reads the page address and compares it with ``article_address``.
  - `read_profile_data() -> ProfileData` — Reads the title, “Nome completo”, “Nascimento”, “Morte”, “Nacionalidade”, “Ocupação” and “Campo(s)”.
- **`ProfileData`** (lido por `ProfilePage.read_profile_data`): title?, full_name?, birth, death, nationality, occupation?, fields? (`?` = pode faltar na página)
- **`WikipediaBiographiesProcess`** (processo; nas execuções continua `biografias_da_wikipedia`)

### Nomes: origem → código (manifest.json → naming)

- processo: `biografias_da_wikipedia` → `WikipediaBiographiesProcess`
- tela: “pesquisa” → `SearchPage`
- tela: “ficha” → `ProfilePage`
- entrada (coluna da lista): `termo_de_busca` → `search_term`
- entrada (coluna da lista): `endereco_do_artigo` → `article_address`
- dado lido: `endereco_da_pagina` → `page_address`
- dado lido: `titulo` → `title`
- dado lido: `nome_completo` → `full_name`
- dado lido: `nascimento` → `birth`
- dado lido: `morte` → `death`
- dado lido: `nacionalidade` → `nationality`
- dado lido: `ocupacao` → `occupation`
- dado lido: `campos` → `fields`
- coluna da planilha: “URL” (cabeçalho, não muda) → campo `url`
- coluna da planilha: “Nome completo” (cabeçalho, não muda) → campo `full_name`
- coluna da planilha: “Nascimento” (cabeçalho, não muda) → campo `birth`
- coluna da planilha: “Morte” (cabeçalho, não muda) → campo `death`
- coluna da planilha: “Nacionalidade” (cabeçalho, não muda) → campo `nationality`
- coluna da planilha: “Ocupação” (cabeçalho, não muda) → campo `occupation`
- palavras sem tradução no glossário: 0 (Wikipédia), 0 (the-internet)

### Chaves tipadas (manifest.json → selectors)

Equivalência `automation/selectors.py` ↔ `selectors.json`: `sha256:9a55903edba85d677527bbe8c3863dcd5217c9c1de72f2ff9382aafca220a18f` (conferida pelo contrato V5).

- `SelectorKey.SEARCH_TERM_FIELD` = `process.search.search_term_field` ← `search.fill_search_term`, `search.press_enter`
- `SelectorKey.PROFILE_TITLE` = `process.profile.title` ← `profile.read_title`
- `SelectorKey.PROFILE_FULL_NAME` = `process.profile.full_name` ← `profile.read_full_name`
- `SelectorKey.PROFILE_BIRTH` = `process.profile.birth` ← `profile.read_birth`
- `SelectorKey.PROFILE_DEATH` = `process.profile.death` ← `profile.read_death`
- `SelectorKey.PROFILE_NATIONALITY` = `process.profile.nationality` ← `profile.read_nationality`
- `SelectorKey.PROFILE_OCCUPATION` = `process.profile.occupation` ← `profile.read_occupation`
- `SelectorKey.PROFILE_FIELDS` = `process.profile.fields` ← `profile.read_fields`

## Perguntas para a titular (checklist de dúvidas)

- Os nomes em inglês dizem o que cada parte faz? (`SearchPage.search_article`, `ProfilePage.read_profile_data`, `ProfileData`)
- `SelectorKey.<NOME>.value` deixa claro qual elemento cada ação usa, sem abrir outro arquivo?
- O mapeamento explícito `search_term=_required_input(data, "termo_de_busca")` está claro?
- As traduções literais do glossário (`birth`, `death` para "Nascimento", "Morte") servem, ou prefere outras?
- Algum trecho difícil? Aponte arquivo e linha: a correção vai para o gerador e o código é gerado de novo pelo Studio.

## `final/` × `desenvolver/`

O Python é idêntico byte a byte. Os manifestos diferem só em `automationRevision` (52 → 54): entre as duas gerações o
roteiro faz o teste G3 (acrescenta uma etapa, confirma que o PDD desatualizado bloqueia e desfaz com Ctrl+Z).

## Evidências desta rodada (commits locais `a3e1a97` + `56ed279`)

- Suíte do Studio no Windows: 756 testes, 0 falhas, 0 erros, 0 pulados.
- Suíte no Linux (contêiner `eclipse-temurin:21.0.12.1_1-jdk-resolute`): 756 testes, 0 falhas, 0 erros, **19 pulados**
  (testes que precisam do Python com o framework, ausente no contêiner; 1 por rodar como root). Pulados NÃO contam como
  aprovados: 737 aprovados de fato. (A suíte rodou antes da correção D-CQ-34, que mudou uma linha do planejador e foi
  coberta pelos módulos AI-Engine 69/0 e app 182/0 no Windows e pela homologação real abaixo.)
- Homologação pela interface real, Wikipédia (`CodegenQuality001Homologation`): **87 verificações, 0 falhas** na 4ª
  tentativa, com o teste autorizado (execução real) gravando e conferindo a planilha. Tentativas anteriores, com a
  evidência guardada:
  1. 82/86 — a execução real recusou os 4 itens (`ENDERECO_DIFERENTE_DO_ESPERADO`): a leitura do endereço corria contra a
     navegação depois do Enter. **Defeito real**, que existia desde a rodada 1 (a rodada 2 passou por tempo). Corrigido
     no gerador (D-CQ-34): o Enter que troca de documento confirma a chegada pela troca de endereço.
  2. e 3. — a Wikipédia respondeu **HTTP 429** (limite de requisições na busca) depois das execuções seguidas; nada a ver
     com o código. Esperei o limite expirar (sonda espaçada) antes da 4ª.
- Homologação pela interface real, the-internet (`Studio014Sites`): **12 verificações, 0 falhas**; execução do código
  gerado `COMPLETED` com 1 transação.
- Oráculo independente no laboratório sintético (código da rodada 3): 3 linhas e 18 células `PASS`; endereço errado
  recusado e planilha retida (tudo ou nada); a 2ª execução usou `--entrada/--planilha/--saida` (nomes antigos aceitos).
- Compatibilidade (G-Q13), testes automatizados: o projeto da rodada 2 (português, `paginas.py`) continua aprovado em
  V4/V5/VQ; o projeto anterior à missão roda pelo teste autorizado do Studio com `--entrada` e `main.PLANILHA`; nada é
  migrado; a VQ só OFERECE gerar de novo.

## O que o adendo pediu e onde está

| Critério | Onde |
|---|---|
| Nenhum `PaginaPesquisa`, `dados_da_ficha`, `self.acoes`, `self.elemento`, `--entrada` como identificador novo | `antes-depois.md`; VQ `IDENTIFICADOR_EM_PORTUGUES` (bloqueio) e teste com mutante |
| Toda chave de alvo como `SelectorKey.<NOME>.value`, inclusive pós-condição | `final/automation/pages.py`, `the-internet/automation/pages.py`; contrato `SELECTOR_KEYS_TYPED` + VQ `CHAVE_DE_ALVO_LITERAL` |
| `selectors.py` dentro de pacote, sem encobrir a stdlib | `automation/selectors.py`; contrato `STDLIB_NOT_SHADOWED` (+ controle negativo com `selectors.py` na raiz) |
| Runtime 0.3.0 lê o JSON derivado e validado; fonte única | `selectors.json`; `manifest.json → selectors.equivalence`; contrato `SELECTORS_PY_JSON_EQUAL` (+ controle negativo) |
| Manifesto: arquivos, hashes, versões, SelectorKey ↔ chave ↔ traceId/studioStepId | `manifest.json → files`, `selectors.keys`, `trace`, `naming` |
| Dados do cliente, rótulos, CSV, planilha, segredos e códigos de recusa sem mudança | `antes-depois.md` (seção "O que NÃO foi traduzido") |
| Campos só com espaços | `final/automation/process.py` (`_filled`); testes com principal, alternativa e ambos só com espaços |
| Mesma entrada → mesmos bytes | teste `mesmosBytes` (todas as gerações, todos os arquivos) |
| Decisões e limitações | `docs/codegen-quality-001/decisoes.md` (D-CQ-22…34), `limitacoes-e-seguranca.md` (itens 8–10) |

## Observações do agente (para a titular julgar — não são aceite)

- Traduções literais do glossário: "Nascimento" → `birth`, "Morte" → `death`, "ficha" → `profile`, "Campo(s)" →
  `fields`, "endereço do artigo" → `article_address`. Se preferir outras (ex.: `birth_date`, `article_url`), é uma
  mudança no glossário (nova versão), e o código é gerado de novo.
- `SearchPage.search_article`: a docstring diz "Fills the field" porque o campo de busca da Wikipédia não tem rótulo
  visível; o nome do parâmetro (`search_term`) diz o que vai nele.
- `ProfileData.page_address` aparece em `check_page_address` (devolvido) e não na dataclass: são dois métodos porque o
  PDD separa "conferir o endereço" de "ler os dados".
- O membro `ACCESS_PASSWORD` em `the-internet/automation/selectors.py` leva `# noqa: S105 - element key, not a secret`:
  é a chave do campo de senha (o bandit não distingue); a senha em si vem do provedor de segredos.
- O emissor tabular 1.0.0 (RPA Challenge) continua em português (D-CQ-33): decida se ele entra numa próxima rodada.
- `SecureAreaPage` (the-internet): o nome do processo virou `InternetSecureAreaProcess` ("Área segura The Internet");
  "The" é artigo inglês e cai, como os artigos portugueses.
