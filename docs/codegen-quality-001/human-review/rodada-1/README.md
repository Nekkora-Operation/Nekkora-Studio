# Revisão humana — rodada-1

**Estado: AWAITING_HUMAN_READABILITY.** Só a titular encerra esta revisão, lendo estes arquivos e declarando
explicitamente "está entendivel". Testes verdes, a camada VQ ou a opinião do agente NÃO substituem esse aceite.

- Arquivos ORIGINAIS gerados pelo Studio (botão "Desenvolver automação"), copiados byte a byte pelo roteiro de
  homologação `CodegenQuality001Homologation` — nada foi reformatado nem editado depois do Studio. SHA-256 em `final/SHA256SUMS.txt`.
- Studio: branch `feat/codegen-quality-001`, commit `edee926` · gerador nekkora-studio-codegen/1.2.0 · manifesto 1.3.0 ·
  requisito `nekkora-rpa[excel]==0.3.0` · automação 5eac01c1-e29f-4a4b-9d51-c1d039dc35b5 revisão 54.

## Por onde começar

1. `paginas.py` — uma classe por tela (Page). Cada método diz, na docstring, o passo do PDD e o que faz.
2. `processo.py` — a ordem das telas e a linha da planilha (regra das fontes alternativas).
3. `README.md` — o que a automação faz, como cada elemento é localizado, como executar.
4. `manifest.json` → `trace` — cada `step_id` do código ligado à etapa do Studio.

## Mapa

- **`PaginaPesquisa`** — Pesquisa do artigo. (nome: PDD_SECTION_TITLE)
  - `pesquisar_artigo(termo_de_busca) -> None` — Pesquisar o artigo pelo nome.
- **`PaginaExtracao`** — Extração dos dados. (nome: PDD_SECTION_TITLE)
  - `conferir_endereco_da_pagina(endereco_do_artigo) -> str` — Lê endereço da página e o confere com ``endereco_do_artigo`` (diferente recusa o item).
  - `ler_dados() -> DadosExtracao` — Lê título, “Nome completo”, “Nascimento”, “Morte”, “Nacionalidade”, “Ocupação” e “Campo(s)”. Título, “Nome completo”, “Ocupação” e “Campo(s)” podem faltar nesta página (fonte alternativa declarada no PDD).
- **`DadosExtracao`** (lido por `PaginaExtracao.ler_dados`): titulo?, nome_completo?, nascimento, morte, nacionalidade, ocupacao?, campos? (`?` = pode faltar na página)
- **`BiografiasDaWikipedia`** (processo)

## Perguntas para a titular (checklist de dúvidas)

- Os nomes das classes e dos métodos dizem o que cada parte faz, sem precisar abrir o Studio?
- A docstring de cada método é suficiente (nem curta demais, nem repetindo o código)?
- A linha da planilha (`linha_da_planilha`) deixa clara a regra das fontes alternativas?
- Os `step_id` (ex.: `extracao.ler_nome_completo`) ajudam a ler os eventos?
- Algum trecho ficou difícil de entender? Aponte o arquivo e a linha: a correção vai para o gerador e o código é
  gerado de novo pelo Studio (nunca editado à mão).

## Segunda amostra: `the-internet/` (execução única, com login)

Gerada pelo Studio no roteiro dos sites públicos de treinamento (`Studio014Sites`, the-internet.herokuapp.com/login), mesmo
commit e mesmo gerador; arquivos originais e SHA-256 em `the-internet/SHA256SUMS.txt`. Executada uma vez: `COMPLETED`, 1
transação, eventos sem senha.

- **`PaginaAcesso`** — `entrar(segredos)`: usuário e senha vêm do provedor de segredos (variáveis de ambiente), nunca do projeto.
- **`PaginaArea`** — `ler_resultado_logout()`, `clicar_logout()`.
- **`AreaSeguraTheInternet`** (processo).

## Observações do agente (para a titular julgar — não são aceite)

- `the-internet/paginas.py`: a docstring da classe repete o título (`Acesso.` / `Etapas: Acesso.`); `PaginaArea` perdeu o
  "segura" de "Área segura"; `ler_resultado_logout` vem de uma etapa gravada que lê o texto do botão Logout.
- `final/paginas.py`: "Lê endereço da página" (falta o artigo "o"); `step_id="INICIO"` em maiúsculas destoa dos demais.
