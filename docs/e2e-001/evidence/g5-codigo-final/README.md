<!-- GERADO pelo Nekkora Studio (STUDIO-011). -->
# Processo gerado: rpa_challenge_input_forms

Gerado a partir do modelo validado do Nekkora Studio (ver `manifest.json`: automação, revisão do plano, StepIds e hashes).
Correções vão para o MODELO (debug guiado) e o código é gerado de novo; arquivo editado à mão não é sobrescrito.

```sh
python -m venv .venv
.venv/Scripts/python -m pip install <nekkora_rpa-0.2.0-py3-none-any.whl>[excel] -c <constraints/lab.txt>
.venv/Scripts/python main.py --saida <pasta-da-execucao> --console
```

Saída: `resultados/` (um arquivo por linha), `execucao/` (eventos JSONL, checkpoint, resumo) e `observacoes.json`.
