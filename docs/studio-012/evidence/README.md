# Evidências da NEKKORA-STUDIO-012

| Arquivo | O que é |
|---|---|
| `homologacao-log.txt` | Log da homologação real (`Studio012Homologation`): Studio, Chrome for Testing com a extensão da branch, Native Host oficial, mouse e teclado do sistema, laboratório sintético. Resultado: 25 verificações, 0 falhas. |
| `projeto-sintetico.nekkora` | Projeto gravado e salvo pela homologação no laboratório sintético (dados fictícios). |
| `auditoria-copia-metadados.txt` | Saída de `Studio012Audit` sobre uma CÓPIA do projeto da usuária: só ação, modo, estratégia, escopo, códigos de obstáculo, limitação e apontamento, origem e sensibilidade das variáveis, e o hash antes e depois (inalterado). |

**Saneamento declarado.** No log, o nome do arquivo do projeto da usuária foi trocado por "projeto de reprodução" e os
caminhos locais por `<home>`. Nenhuma outra alteração.

**Não versionados**, pela regra da missão: as capturas de tela (PNG) da homologação, inclusive as sintéticas; o projeto
real e suas cópias; o perfil do Chrome e os dados isolados do Studio e do host.

Os resultados de cada cenário no Chrome real (headless) estão em `Nekkora-Extension-Chrome/reports/evidence/S012-*.json`.
