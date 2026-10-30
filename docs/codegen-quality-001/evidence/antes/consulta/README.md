<!-- GERADO pelo Nekkora Studio (STUDIO-014). -->
# Processo gerado: consulta_de_registro_sintetica

Gerado por "Desenvolver automação" a partir do modelo validado e do PDD pronto (ver `manifest.json`: automação, revisões do PDD, StepIds e hashes). Correções vão para o MODELO ou o PDD e o código é gerado de novo; arquivo editado à mão não é sobrescrito.

Execução única (sem planilha). Entradas (arquivo JSON em `--entrada`, um objeto com estas chaves): `codigo_do_registro`. Segredos (variáveis de ambiente, nunca no projeto): `NK_SECRET_CREDENCIAL_SENHA`, `NK_SECRET_CREDENCIAL_USUARIO`. 

O Nekkora Studio prepara o ambiente desta automação: **Desenvolver automação → Pendências → Preparar ambiente** (venv própria, `nekkora-rpa==0.2.0`, dependências conferidas por hash, confirmação antes de instalar).

Fora do Studio (Python 3.12+, depois da publicação no índice aprovado):

```sh
python -m venv .venv
.venv/Scripts/python -m pip install "nekkora-rpa==0.2.0"
.venv/Scripts/python main.py --saida <pasta-da-execucao> --entrada <entrada.json> --console
```

Saída: `resultados/` (o desfecho da execução, com as leituras) e `execucao/` (eventos JSONL sem valores, checkpoint, resumo).
Gerar código não é executar: rode somente em ambiente autorizado (lab).
