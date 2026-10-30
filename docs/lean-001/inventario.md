# STUDIO-LEAN-001: inventário e linha de base

Medido em 24/10/2026 sobre `feat/spec-optional-columns-001` @ `91040a5` (branch da missão: `feat/studio-lean-001`).
Windows 11, JDK 21.0.12 (Microsoft), 31 GB de RAM. Nada foi apagado antes deste inventário.

## 1. Estado de partida

- Branch de origem `91040a5`: encerramento local da SPEC-OPTIONAL-COLUMNS-001.
- Árvore limpa, exceto `docs/e2e-001/evidence/g5-codigo-final/venv/` (**não rastreada**, 14 MB, sobra conhecida da
  PACKAGING-001). Não foi tocada: arquivo não rastreado da titular; a remoção pede consentimento.
- Linha de base de testes (`./mvnw -o -B clean verify`, Framework irmão em `main`/0.3.0): **780 testes, 0 falhas,
  0 pulados**, 6 min 21 s. Inclui golden e escala 150% (`Scale150Test`, `Variables150Test`). A lista nominal dos 780
  casos está guardada para a comparação final (`matriz-regressao.md`).

## 2. Tamanhos medidos

| O quê | Medida | Observação |
|---|---|---|
| (a) repositório rastreado | 54,8 MB em 2 001 arquivos; `.git` 55 MB | ver a divisão abaixo |
| (b) distribuição (`nekkora-studio.jar`, shade) | **8 395 978 bytes**, 2 761 entradas, 19,0 MB descomprimidos | é o que roda (`java -jar`) |
| (c) pacote de automação gerado (SPEC-OPTIONAL, Wikipédia) | 49 317 bytes em 11 arquivos (manifesto 21,6 KB) | cada arquivo tem papel próprio (CQ-001): nada a cortar |
| (d) abertura (cópia do jar, dados descartáveis) | janela visível em **1 328 ms** (mediana de 5; 1 321–1 495) | `java -jar`, sem opções |
| (e) memória ociosa (20 s com a janela aberta) | conjunto de trabalho **152,5 MB** (147–153); privada 248 MB | |

### Repositório rastreado, por categoria

| Categoria | Bytes | Arquivos | Vai para o jar? |
|---|---|---|---|
| `docs/` (missões, decisões, evidências, revisões humanas) | 29 138 162 | 894 | não |
| `reports/` (evidências das STUDIO-001…007) | 15 511 102 | 175 | não |
| fontes de teste | 3 585 105 | 175 | não |
| fontes main | 3 148 463 | 430 | sim (compiladas) |
| recursos main (fontes Inter/JetBrains Mono/Sora, ícones, vocabulário) | 2 172 661 | 23 | sim |
| recursos de teste (golden, fixtures) | 800 372 | 261 | não |
| AI-Engine: wheels 0.2.0/0.3.0, locks, catálogos | 262 551 | 7 | sim (Dependency Manager) |
| poms, ferramentas, raiz | 146 586 | 36 | não |

**82% do repositório é evidência e documentação de auditoria, e nada disso vai para o artefato distribuído.** Tirar
isso do Git não muda nada no tempo de execução do programa.

### Jar distribuído, por origem (bytes comprimidos)

| Origem | Comprimido | Descomprimido | Entradas |
|---|---|---|---|
| PDFBox (`org/apache/pdfbox`) | 1 895 589 | 4 712 292 | 874 |
| Código do Studio (`com/nekkora/studio`) | 1 723 852 | 4 251 941 | 774 |
| FontBox (`org/apache/fontbox`) | 1 580 843 | 4 209 307 | 301 |
| Design system (`com/nekkora/ui`, fontes inclusive) | 1 359 839 | 2 828 037 | 266 |
| FlatLaf (inclui nativos de 7 plataformas) | 926 277 | 2 204 986 | 387 |
| AI-Engine: framework, prompts, catálogo, Python | 216 570 | 325 839 | 21 |
| commons-logging, `META-INF/maven`, licenças | ~120 000 | | |

## 3. Candidatos

| Candidato | Tamanho | Motivo | Consumidores reais | Risco | Proposta | Evidência |
|---|---|---|---|---|---|---|
| `.ruff_cache/` dentro de `Nekkora-AI-Engine/src/main/resources` **indo para o jar** | 327 bytes (3 entradas) | cache local do desenvolvedor no release: o conteúdo do jar passa a depender da máquina de quem gera | nenhum | baixo | **excluir do artefato** (recursos) + teste de guarda | `unzip -l` do jar (`nekkora/ai/python/.ruff_cache/…`) |
| dependência declarada e não usada: AI-Engine → `nekkora-studio-guided` | 0 no jar (o app usa o módulo diretamente) | grafo de módulos mais simples; o AI-Engine compila sem esperar o Guided | nenhum no AI-Engine (`dependency:analyze` + busca no código) | baixo | **remover a declaração** | `dependency:analyze` |
| minimizar o shade (`minimizeJar`) | jar 8,40 → 7,69 MB (−8,4%) | | FlatLaf carrega delegados de interface POR NOME; o PDFBox carrega o commons-logging por reflexão — o minimizador os remove | **alto** | **não fazer**: ao repor o que é carregado por reflexão, o ganho cai para ~3% | experimento em árvore separada (removida) |
| nativos do FlatLaf para outras plataformas | ~360 KB comprimidos | o Studio também roda em Linux (golden Linux) e macOS | | médio | **manter** | |
| `META-INF/maven/**` no shade | ~30 KB | metadados de bibliotecas | algumas leem a própria versão | baixo, ganho 0,4% | **manter** (ganho irrelevante) | |
| wheel `nekkora_rpa-0.2.0` no AI-Engine | 80 KB | pacotes legados (`paginas.py`, `--entrada`) ainda validam e rodam no Studio | Dependency Manager | alto | **manter** | CQ-001 rodada 3 |
| 72 grupos de arquivos idênticos byte a byte no Git | 815 KB redundantes | evidências repetidas entre missões (ex.: capturas da E2E-002 citadas pela CQ-001) | documentos de cada missão + manifestos SHA-256 | alto: quebra a rastreabilidade | **manter** | `studio-hashes.txt` |
| `reports/` e `docs/**/evidence` | 44,6 MB | evidência de auditoria | revisões e pareceres | alto | **manter** (não vai para o jar) | |
| `docs/e2e-001/evidence/g5-codigo-final/venv/` (não rastreada) | 14 MB | sobra local | nenhum | — | **decisão da titular** (não tocada) | `git status` |
| opções de JVM no `studio.cmd` (`-Xms32m`) | — | medido: privada 248 → 222 MB, conjunto de trabalho igual | | | **não fazer**: ganho pequeno, e o README manda `java -jar` | medição em `antes-depois.md` |
