# GERADO pelo Nekkora Studio (STUDIO-014, "Desenvolver automação") do modelo validado e do PDD pronto.
# NÃO EDITE: correções vão para o modelo ou o PDD e o código é gerado de novo.
# Arquivo editado à mão não é sobrescrito.
"""Execução do processo em lista.

    python main.py --entrada <lista.csv|lista.xlsx> --planilha <saida.xlsx> --saida <pasta>
        [--channel chrome|chromium] [--headless]

A lista tem uma coluna por entrada:
- termo_de_busca
- endereco_esperado
A planilha só é gravada se TODOS os itens derem certo.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from nekkora_rpa import Engine, EnvSecretProvider, load_config
from nekkora_rpa.errors import NekkoraRpaError
from nekkora_rpa.tabular import Column, TabularInputProvider, TabularSchema, read_tabular
from nekkora_rpa.xlsx import ColumnKind, OutputColumn, OverwritePolicy, XlsxResultSink, XlsxSchema

from processo import ENTRADAS, Processo

AQUI = Path(__file__).resolve().parent
SAIDA = {"COMPLETED": 0, "COMPLETED_WITH_FAILURES": 1, "STOPPED": 2, "INIT_FAILED": 3, "CANCELLED": 4}
LISTA = TabularSchema(tuple(Column(nome) for nome in ENTRADAS), reject_extra_columns=True)
PLANILHA = XlsxSchema(
    (
        OutputColumn("URL", "url", ColumnKind.URL),
        OutputColumn("Nome completo", "nome_completo", ColumnKind.TEXT),
        OutputColumn("Nascimento", "nascimento", ColumnKind.ISO_DATE),
        OutputColumn("Morte", "morte", ColumnKind.ISO_DATE),
        OutputColumn("Nacionalidade", "nacionalidade", ColumnKind.TEXT),
        OutputColumn("Ocupação", "ocupacao", ColumnKind.TEXT),
    ),
    key_field="url",
    sheet="Dados",
)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Processo gerado: fichas_do_acervo_sintetica")
    ap.add_argument("--entrada", required=True, type=Path, help="lista CSV ou XLSX (uma linha por item)")
    ap.add_argument("--planilha", required=True, type=Path, help="planilha .xlsx de saída")
    ap.add_argument("--saida", required=True, type=Path, help="pasta da execução (eventos, checkpoint)")
    ap.add_argument("--channel", choices=["chromium", "chrome"])
    ap.add_argument("--headless", action="store_true")
    ap.add_argument("--console", action="store_true")
    a = ap.parse_args(argv)
    saida = a.saida.resolve()
    saida.mkdir(parents=True, exist_ok=True)
    overrides: dict[str, object] = {}
    if a.channel:
        overrides["browser.channel"] = a.channel
    if a.headless:
        overrides["browser.headless"] = True
    config = load_config(AQUI / "config" / "execucao.toml", overrides)
    try:
        lista = read_tabular(a.entrada, LISTA)
        destino = XlsxResultSink(
            a.planilha.resolve(),
            PLANILHA,
            state_dir=saida / "execucao",
            overwrite=OverwritePolicy.REPLACE,
            expected_rows=len(lista.rows),
        )
    except NekkoraRpaError as err:
        print(json.dumps({"status": "INPUT_INVALID", "code": err.code.value, "detail": err.detail}))
        return 5
    processo = Processo()
    engine = Engine(
        processo,
        TabularInputProvider(lista),
        destino,
        config,
        secrets=EnvSecretProvider(processo.required_secrets),
        run_dir=saida / "execucao",
        console=a.console,
    )
    resumo = engine.run()
    d = resumo.to_dict()
    campos = ("execution_id", "status", "stop_code", "stop_detail", "counts", "output")
    print(json.dumps({k: d[k] for k in campos}))
    return SAIDA[resumo.status.value]


if __name__ == "__main__":
    sys.exit(main())
