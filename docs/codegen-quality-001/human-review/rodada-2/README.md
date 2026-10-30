# Revisão humana — rodada-2

**Estado: AWAITING_HUMAN_READABILITY.** Só a titular encerra esta revisão, lendo estes arquivos e declarando
explicitamente "está entendivel". Testes verdes, a camada VQ ou a opinião do agente NÃO substituem esse aceite.

- Arquivos ORIGINAIS gerados pelo Studio (botão "Desenvolver automação"), copiados byte a byte pelo roteiro de
  homologação `CodegenQuality001Homologation` — nada foi reformatado nem editado depois do Studio. SHA-256 em `final/SHA256SUMS.txt`.
- Studio: branch `feat/codegen-quality-001`, commit `b2ed26b` · gerador nekkora-studio-codegen/1.2.0 · manifesto 1.3.0 ·
  requisito `nekkora-rpa[excel]==0.3.0` · automação cd042a37-a293-4441-945e-8e31eae94c2c revisão 54.

## Por onde começar

1. `paginas.py` — uma classe por tela (Page). Cada método diz, na docstring, o passo do PDD e o que faz.
2. `processo.py` — a ordem das telas e a linha da planilha (regra das fontes alternativas).
3. `README.md` — o que a automação faz, como cada elemento é localizado, como executar.
4. `manifest.json` → `trace` — cada `step_id` do código ligado à etapa do Studio.

## Mapa

- **`PaginaPesquisa`** — Tela da seção “Pesquisa do artigo” do PDD: pesquisa o artigo pelo nome. (nome: PDD_SECTION_TITLE)
  - `pesquisar_artigo(termo_de_busca) -> None` — Preenche “termo de busca” com ``termo_de_busca`` e envia com Enter.
- **`PaginaFicha`** — Tela da seção “Extração dos dados” do PDD: lê o endereço e os dados da ficha. (nome: PDD_DATA_OWNER)
  - `conferir_endereco_da_pagina(endereco_do_artigo) -> str` — Lê o endereço da página e o compara com ``endereco_do_artigo``.
  - `ler_dados_da_ficha() -> DadosDaFicha` — Lê o título, “Nome completo”, “Nascimento”, “Morte”, “Nacionalidade”, “Ocupação” e “Campo(s)”.
- **`DadosDaFicha`** (lido por `PaginaFicha.ler_dados_da_ficha`): titulo?, nome_completo?, nascimento, morte, nacionalidade, ocupacao?, campos? (`?` = pode faltar na página)
- **`BiografiasDaWikipedia`** (processo)

## Perguntas para a titular (checklist de dúvidas)

- Os nomes das classes e dos métodos dizem o que cada parte faz, sem precisar abrir o Studio?
- A docstring de cada método é suficiente (nem curta demais, nem repetindo o código)?
- A linha da planilha (`linha_da_planilha`) deixa clara a regra das fontes alternativas?
- Os `step_id` (ex.: `ficha.ler_nome_completo`) ajudam a ler os eventos?
- Algum trecho ficou difícil de entender? Aponte o arquivo e a linha: a correção vai para o gerador e o código é
  gerado de novo pelo Studio (nunca editado à mão).

## Segunda amostra: `the-internet/` (execução única, com login)

Gerada pelo Studio no roteiro dos sites públicos de treinamento (`Studio014Sites`, the-internet.herokuapp.com/login),
mesmo commit e mesmo gerador; arquivos originais e SHA-256 em `the-internet/SHA256SUMS.txt`. Executada uma vez:
`COMPLETED`, 1 transação, eventos sem senha.

- **`PaginaAcesso`** — `entrar(segredos)`: usuário e senha vêm do provedor de segredos, nunca do projeto.
- **`PaginaAreaSegura`** — `ler_texto_do_link_logout()`, `clicar_logout()`.
- **`AreaSeguraTheInternet`** (processo).

## Resposta ao parecer da rodada 1 (CQ-01…CQ-09)

Tudo foi corrigido no GERADOR (Studio), e o Python abaixo saiu da interface real; nada foi editado à mão. Decisões
completas em `docs/codegen-quality-001/decisoes.md` (D-CQ-14…D-CQ-21).

| Achado | O que mudou | Onde ver |
|---|---|---|
| CQ-01 nomes da tarefa | A tela onde os dados são lidos leva o nome do DONO dos dados dito no PDD ("os dados da ficha"): `PaginaFicha`, `DadosDaFicha`, `ler_dados_da_ficha`. Sem título com o dono, vale a frase nominal da seção (`PaginaAreaSegura`, antes `PaginaArea`). Nada fora do texto do PDD: "biográfica" não aparece porque o PDD não diz "ficha biográfica" (pendência para a titular, se quiser esse nome: basta o PDD dizer) | `final/paginas.py:35,74,90` |
| CQ-02 `self.act`/`self.k` | `PaginaBase` gerada com `self.acoes` e `self.elemento("nome")`; `act`/`k` aparecem só ali. O framework NÃO mudou: o requisito `nekkora-rpa[excel]==0.3.0` está fixado e homologado por SHA-256 na PACKAGING-001, e uma API nova exigiria nova versão publicada | `final/paginas.py:21-32` |
| CQ-03 docstrings | Tela: "Tela da seção “X” do PDD: papel". Método: o que faz com as entradas e um parágrafo com o que devolve, quando termina, o que recusa e se repete. O comentário da espera virou docstring de `ler_dados_da_ficha` | `final/paginas.py:50-51,90-97` |
| CQ-04 idempotência | `Actions.click(idempotent=True)` (0.2.0/0.3.0) permite REPETIR o clique depois de falha pós-despacho. Agora só clique/Enter sem efeito, fora de autenticação e de fim de sessão, recebe `idempotent=True`. Login e Logout são enviados uma vez (resultado incerto → `OUTCOME_UNKNOWN`). Decisão por etapa em `manifest.steps[].retry` (`SAFE`/`AUTHENTICATION`/`SESSION_END`/`EXTERNAL_EFFECT`); testes negativos no `SingleFlowTest` e no roteiro dos sites | `the-internet/paginas.py:37-53,69-74`; `the-internet/manifest.json:64,96` |
| CQ-05 `ler_resultado_logout` | O PDD manda ler o texto do link, não confirmar login: `ler_texto_do_link_logout`, saída `texto_do_link_logout` | `the-internet/paginas.py:62`, `the-internet/processo.py` |
| CQ-06 "Morte" obrigatória | **Não resolvido nesta missão — limitação real registrada.** O contrato do PDD atual foi preservado. O framework 0.3.0 já aceita coluna opcional; falta a especificação permitir declarar "pode ficar vazia". Proposta: missão **NEKKORA-SPEC-OPTIONAL-COLUMNS-001** | `limitacoes-e-seguranca.md` item 6 |
| CQ-07 campo no diagnóstico | `DADO_OBRIGATORIO_VAZIO:<campo>` (nome do campo, nunca o conteúdo) | `final/processo.py:55` |
| CQ-08 `INICIO` | `processo.abrir_pagina_inicial`; o manifesto declara `previousTraceIds: ["INICIO"]`. Checkpoints não guardam `step_id`; projetos antigos mantêm `INICIO` até serem gerados de novo (nada migrado em silêncio) | `final/processo.py:69`; `final/manifest.json:336-342` |
| CQ-09 teste de privacidade | Novo `ProvenancePrivacyTest`: sentinelas exclusivos injetados nos eventos e no estado da planilha, comparação ESTRUTURAL por campo (`ProvenanceShape`: cada folha num conjunto permitido; id de transação validado pelo formato), controle negativo que prova que o teste acusa um sentinela vazado, diagnóstico que cita caminho e regra, nunca o conteúdo. Roda em Java (também no Linux) | `Nekkora-AI-Engine/src/test/.../pycheck/ProvenancePrivacyTest.java` |

## `final/` × `desenvolver/`

O Python das duas pastas é idêntico byte a byte. Os manifestos diferem só em `automationRevision` (52 → 54): entre as
duas gerações, o roteiro faz o teste G3 (acrescenta uma etapa, confirma que o PDD desatualizado bloqueia e desfaz com
Ctrl+Z). Cada uma dessas ações conta como revisão do modelo; o conteúdo voltou ao mesmo e o código gerado não mudou.

## Evidências desta rodada (commit `b2ed26b`)

- Suíte do Studio no Windows: 742 testes, 0 falhas, 0 erros, 0 pulados.
- Suíte no Linux (contêiner `eclipse-temurin:21.0.12.1_1-jdk-resolute`): 742 testes, 0 falhas, 0 erros, **15 pulados**
  (14 sem Python no contêiner, 1 por rodar como root). Os pulados NÃO contam como aprovados: 727 aprovados de fato.
- Homologação pela interface real, Wikipédia (`CodegenQuality001Homologation`): 81 verificações, 0 falhas, na 2ª
  tentativa. A 1ª parou com tempo esgotado esperando a janela do debug no 3º artigo (antes da geração de código; o
  Chrome cobria metade do Studio). Mesma instabilidade de tela da rodada 1; a evidência foi guardada.
- Homologação pela interface real, the-internet (`Studio014Sites`): 10 verificações, 0 falhas; execução do código
  gerado `COMPLETED` com 1 transação.
- Oráculo independente no laboratório sintético (amostra de fichas do gerador atual): 3 linhas e 18 células `PASS`;
  endereço errado recusado (`ENDERECO_DIFERENTE_DO_ESPERADO`) e planilha retida (tudo ou nada).

## Observações do agente (para a titular julgar — não são aceite)

- `the-internet/paginas.py:31-32`: com um único método, a docstring de `PaginaAcesso` fica só "Tela da seção “Acesso”
  do PDD." (o papel está na docstring de `entrar`). É uma escolha para não repetir texto; pode parecer curta.
- `the-internet/paginas.py:52`: a pós-condição do login aponta o elemento da tela seguinte pela chave completa
  (`"processo.area_segura.logout"`), não por `self.elemento(...)`, porque o elemento é de outra tela. Funciona, mas é
  o único lugar com chave crua.
- `the-internet/paginas.py:62-67`: `ler_texto_do_link_logout` tem docstring mínima ("Lê “Logout”. Devolve o texto
  lido.").
- `final/paginas.py`: o Enter da pesquisa continua `idempotent=True` (pesquisa sem efeito declarado: repetir é seguro).
