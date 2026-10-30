# GERADO pelo Nekkora Studio ("Desenvolver automação") a partir do modelo validado e do PDD pronto.
# Não edite: correções vão para o modelo ou o PDD e o código é gerado de novo (arquivo editado à mão não é sobrescrito).
"""Execução do processo em lista.

Uso: python main.py --entrada <lista.csv|lista.xlsx> --planilha <saida.xlsx> --saida <pasta>
    [--channel chrome|chromium] [--headless] [--console]

A lista tem uma coluna por entrada: termo_de_busca, endereco_do_artigo.
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

from processo import ENTRADAS, BiografiasDaWikipedia

AQUI = Path(__file__).resolve().parent
CODIGO_DE_SAIDA = {"COMPLETED": 0, "COMPLETED_WITH_FAILURES": 1, "STOPPED": 2, "INIT_FAILED": 3, "CANCELLED": 4}
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
    """Lê os argumentos, executa o processo e imprime o resumo (sem valores); devolve o código de saída."""
    parser = argparse.ArgumentParser(description="Processo gerado: biografias_da_wikipedia")
    parser.add_argument("--entrada", required=True, type=Path, help="lista CSV ou XLSX (uma linha por item)")
    parser.add_argument("--planilha", required=True, type=Path, help="planilha .xlsx de saída")
    parser.add_argument("--saida", required=True, type=Path, help="pasta da execução (eventos, checkpoint)")
    parser.add_argument("--channel", choices=["chromium", "chrome"])
    parser.add_argument("--headless", action="store_true")
    parser.add_argument("--console", action="store_true")
    args = parser.parse_args(argv)
    pasta = args.saida.resolve()
    pasta.mkdir(parents=True, exist_ok=True)
    ajustes: dict[str, object] = {}
    if args.channel:
        ajustes["browser.channel"] = args.channel
    if args.headless:
        ajustes["browser.headless"] = True
    config = load_config(AQUI / "config" / "execucao.toml", ajustes)
    try:
        lista = read_tabular(args.entrada, LISTA)
        destino = XlsxResultSink(
            args.planilha.resolve(),
            PLANILHA,
            state_dir=pasta / "execucao",
            overwrite=OverwritePolicy.REPLACE,
            expected_rows=len(lista.rows),
        )
    except NekkoraRpaError as erro:
        print(json.dumps({"status": "INPUT_INVALID", "code": erro.code.value, "detail": erro.detail}))
        return 5
    processo = BiografiasDaWikipedia()
    engine = Engine(
        processo,
        TabularInputProvider(lista),
        destino,
        config,
        secrets=EnvSecretProvider(processo.required_secrets),
        run_dir=pasta / "execucao",
        console=args.console,
    )
    resumo = engine.run()
    campos = resumo.to_dict()
    publicados = ("execution_id", "status", "stop_code", "stop_detail", "counts", "output")
    print(json.dumps({chave: campos[chave] for chave in publicados}))
    return CODIGO_DE_SAIDA[resumo.status.value]


if __name__ == "__main__":
    sys.exit(main())
