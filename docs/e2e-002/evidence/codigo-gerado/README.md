<!-- GERADO pelo Nekkora Studio (NEKKORA-E2E-002). -->
# Processo gerado: biografias_da_wikipedia

Gerado por "Desenvolver automação" a partir do modelo validado e do PDD pronto (ver `manifest.json`: automação, revisões do PDD, StepIds e hashes). Correções vão para o MODELO ou o PDD e o código é gerado de novo; arquivo editado à mão não é sobrescrito.

Uma transação por linha da lista de entrada (CSV ou XLSX com as colunas `termo_de_busca`, `endereco_do_artigo`). Saída: planilha `.xlsx` com as colunas `URL`, `Nome completo`, `Nascimento`, `Morte`, `Nacionalidade`, `Ocupação`, gravada pelo `XlsxResultSink` do nekkora_rpa só se TODOS os itens derem certo (nada parcial). 

```sh
python -m venv .venv
.venv/Scripts/python -m pip install "<nekkora_rpa-0.3.0-py3-none-any.whl>[excel]" -c <constraints/lab.txt>
.venv/Scripts/python main.py --entrada <lista.csv|lista.xlsx> --planilha <saida.xlsx> --saida <pasta> --console
```

Eventos JSONL sem valores, checkpoint e resumo em `<pasta>/execucao/`.
Gerar código não é executar: rode somente em ambiente autorizado (benchmark).
