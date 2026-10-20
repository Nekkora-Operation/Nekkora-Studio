# NEKKORA-PACKAGING-001 — decisões

## Tomadas pelo agente (técnicas, reversíveis)

| # | Decisão | Motivo |
|---|---|---|
| D1 | O Studio instala o framework a partir de **wheels homologados embutidos** (0.2.0 do commit `23cc6de`, 0.3.0 do `2f36a0a`), não de índice | Não há publicação aprovada; o wheel é reprodutível (dois builds iguais, `git archive` + `build.lock`) e conferido por SHA-256 antes do uso |
| D2 | Dependências e ferramentas de verificação vêm do **PyPI oficial com hash obrigatório** (`dependencias.lock`, `verificacao.lock`) | Evita troca de pacote e confusão de dependência; o lock vem do framework (`tools/travar_hashes.py`) |
| D3 | Uma venv **por automação e por versão-extras**, na área de dados do Studio | Legado 0.2.0 e moderno 0.3.0 convivem sem migração; nada no Python global, no projeto (OneDrive) ou em checkout |
| D4 | O requisito vem do `manifest.json` gerado (campo `framework`), não de heurística | É o contrato que o gerador escreveu; projetos antigos mantêm a versão deles |
| D5 | O catálogo de capacidades é o **da versão declarada pelo código gerado** (`FrameworkCapabilityCatalog.forVersion`; ver D13) | 0.3.0 não valida runtime 0.2.0 nem o contrário |
| D6 | Python indicado em Opções avançadas com versão divergente vira **diagnóstico** (VERSION_MISMATCH), nunca troca silenciosa | A pessoa decide; V4/V5 ficam "não verificado" |
| D7 | `ambiente.json` só é gravado **depois** da verificação completa | Instalação cancelada ou falha nunca parece pronta |
| D8 | Instalação sem shell, `cwd` no diretório do ambiente com argumentos relativos ASCII, ambiente do processo limpo (`PIP_*`, `PYTHON*`, `VIRTUAL_ENV` removidos), saída sanitizada (sem credenciais de URL nem a pasta do usuário) | Caminhos com espaço/acento; nenhuma configuração externa altera a instalação |
| D9 | Diálogo próprio do plano de instalação (não o de ação destrutiva), grande e com rolagem | Achado da homologação: o diálogo pequeno de perigo cortava o título e o texto cobria "Cancelar" |
| D10 | Arquivos gerados (`requirements.txt`, README) sem wheel local nem caminho do checkout; a linha de requisito continua a exata do contrato | O caminho principal é "Preparar ambiente"; fora do Studio, `pip install "<req>"` depois da publicação |
| D11 | Consentimento do pip separado do consentimento da IA | A IA não instala nem escolhe URL; nenhum dado do projeto vai ao índice |
| D12 | Não perguntar P1/P2/P3 durante a implementação | Elas bloqueiam só a publicação; ficam como ações da titular |
| D13 | Versão declarada no `manifest.json` **sem catálogo** neste Studio (ou requisito não reconhecido) = **incompatibilidade**: V2 bloqueado (`FRAMEWORK_CATALOG_MISSING`), V4/V5/V6 "não validado", sem `CODE_VALIDATED` nem teste autorizado. O catálogo padrão serve só ao planejamento. Sem código gerado (ou manifesto sem o campo `framework`), o catálogo padrão, como antes (`CatalogResolution`) | Revisão da titular no PR #23: o padrão não pode conferir em silêncio um código que exige outra versão |

## Da titular (não inferidas, abertas)

- **P1** licença de distribuição e retirada do classifier `Private :: Do Not Upload`.
- **P2** conta e nome no TestPyPI/PyPI.
- **P3** aprovação do staging (TestPyPI) e da produção (tag + PyPI).

Versão: 0.3.0 continua candidata (inédita em índice); nenhuma versão foi alterada.
