<!-- GERADO pelo Nekkora Studio (NEKKORA-E2E-002). -->
# Processo gerado: fichas_do_acervo_sintetica

Gerado por "Desenvolver automação" a partir do modelo validado e do PDD pronto (ver `manifest.json`: automação, revisões do PDD, StepIds e hashes). Correções vão para o MODELO ou o PDD e o código é gerado de novo; arquivo editado à mão não é sobrescrito.

Uma transação por linha da lista de entrada (CSV ou XLSX com as colunas `termo_de_busca`, `endereco_esperado`). Saída: planilha `.xlsx` com as colunas `URL`, `Nome completo`, `Nascimento`, `Morte`, `Nacionalidade`, `Ocupação`, gravada pelo `XlsxResultSink` do nekkora_rpa só se TODOS os itens derem certo (nada parcial). 

O Nekkora Studio prepara o ambiente desta automação: **Desenvolver automação → Pendências → Preparar ambiente** (venv própria, `nekkora-rpa[excel]==0.3.0`, dependências conferidas por hash, confirmação antes de instalar).

Fora do Studio (Python 3.12+, depois da publicação no índice aprovado):

```sh
python -m venv .venv
.venv/Scripts/python -m pip install "nekkora-rpa[excel]==0.3.0"
.venv/Scripts/python main.py --entrada <lista.csv|lista.xlsx> --planilha <saida.xlsx> --saida <pasta> --console
```

Eventos JSONL sem valores, checkpoint e resumo em `<pasta>/execucao/`.
Gerar código não é executar: rode somente em ambiente autorizado (lab).
