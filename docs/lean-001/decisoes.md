# STUDIO-LEAN-001: decisões

Regra da missão: enxugar não é destruir a capacidade de provar que o Studio funciona. Cada decisão tem medida ou
evidência; o que não teve ganho comprovado com segurança ficou como estava.

| # | Decisão | Por quê | Destino | Evidência |
|---|---|---|---|---|
| L-01 | **Caches locais de ferramentas Python fora do jar** (`.ruff_cache`, `__pycache__`, `.mypy_cache`, `.pytest_cache`, `*.pyc` excluídos dos recursos do AI Engine) + teste de guarda `PackagedResourcesTest` | o jar distribuído levava `nekkora/ai/python/.ruff_cache/…`: o conteúdo do release dependia da máquina de quem gerava. Ganho em bytes desprezível; o ganho é de **reprodutibilidade e higiene do release** | excluído só do artefato (os arquivos são locais e não rastreados; nada removido do Git) | teste vermelho antes (`.ruff_cache` listado), verde depois; `unzip -l` do jar |
| L-02 | **Removida a dependência declarada e não usada** `Nekkora-AI-Engine → nekkora-studio-guided` | `dependency:analyze` + busca no código: nenhum uso. O grafo fica mais simples e o AI Engine não espera o Guided para compilar | removida do `pom.xml` (o app continua dependendo do Guided diretamente; o jar não muda) | compilação e testes do módulo verdes |
| L-03 | **NÃO minimizar o jar** (`minimizeJar` do shade) | medido: 8,40 → 7,69 MB (−8,4%), mas o minimizador remove classes carregadas por NOME: delegados de interface do FlatLaf e o commons-logging do PDFBox. Repondo o que é carregado por reflexão, o ganho cai para ~3%, com risco de quebrar telas ou a exportação de PDF que os testes (classpath de módulo) não pegariam | sem mudança | experimento em árvore separada (descartada) |
| L-04 | **NÃO mexer no PDFBox/FontBox** (42% do jar) | usados de verdade: exportação E prévia (renderização) do PDD; a renderização puxa quase todo o PDFBox | sem mudança | `PddExporter`, `PdfRenderer`, `PddPresenter` |
| L-05 | **NÃO remover evidências duplicadas** (72 grupos idênticos, 815 KB) | são evidências de missões diferentes, citadas pelos próprios documentos e por manifestos SHA-256; apagar quebraria a rastreabilidade por 1,5% do repositório | sem mudança | `studio-hashes.txt` |
| L-06 | **NÃO mover `reports/` e `docs/**/evidence`** (44,6 MB) | evidência de auditoria e revisões humanas; não vão para o jar; mover para "esconder tamanho" é proibido pela missão | sem mudança | inventário §2 |
| L-07 | **NÃO adicionar opções de JVM ao `studio.cmd`** | `-Xms32m` medido: privada 248 → 222 MB, conjunto de trabalho igual (152 → 154 MB). Ganho pequeno, e o README documenta `java -jar` direto: só quem usa o script teria o efeito | sem mudança | medição em `antes-depois.md` |
| L-08 | `docs/e2e-001/evidence/g5-codigo-final/venv/` (14 MB, NÃO rastreada) | arquivo local da titular: a missão proíbe tocar em não rastreados | **decisão da titular** | `git status` |
| L-09 | Comunicação Studio → Agent / Platform | fora do escopo da missão | não implementada | — |

## Resultado

Distribuição: o jar só perde os 3 arquivos de cache (alguns bytes). **Não há redução segura relevante de peso nem de
memória no Studio** com a pilha atual. As duas mudanças feitas melhoram a reprodutibilidade do release e a
manutenção do grafo de módulos, sem alterar comportamento. Ver `antes-depois.md` e `matriz-regressao.md`.
