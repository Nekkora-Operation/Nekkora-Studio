"""Execução da referência manual: ``python main.py --entrada <lista.csv> --planilha <saida.xlsx> --saida <pasta>``.

REFERENCIA_MANUAL_VALIDACAO (NEKKORA-CODEGEN-QUALITY-001): escrita à mão SÓ para comparação; não é saída do Studio.
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

from processo import Processo

AQUI = Path(__file__).resolve().parent
CODIGO_DE_SAIDA = {"COMPLETED": 0, "COMPLETED_WITH_FAILURES": 1, "STOPPED": 2, "INIT_FAILED": 3, "CANCELLED": 4}
LISTA = TabularSchema((Column("termo_de_busca"), Column("endereco_esperado")), reject_extra_columns=True)
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
    parser = argparse.ArgumentParser(description="Referência manual: fichas do acervo")
    parser.add_argument("--entrada", required=True, type=Path)
    parser.add_argument("--planilha", required=True, type=Path)
    parser.add_argument("--saida", required=True, type=Path)
    args = parser.parse_args(argv)
    pasta = args.saida.resolve()
    pasta.mkdir(parents=True, exist_ok=True)
    config = load_config(AQUI / "config" / "execucao.toml", {})
    try:
        lista = read_tabular(args.entrada, LISTA)
        planilha = XlsxResultSink(
            args.planilha.resolve(),
            PLANILHA,
            state_dir=pasta / "execucao",
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
        planilha,
        config,
        secrets=EnvSecretProvider(processo.required_secrets),
        run_dir=pasta / "execucao",
    )
    resumo = engine.run().to_dict()
    campos = ("execution_id", "status", "stop_code", "stop_detail", "counts", "output")
    print(json.dumps({campo: resumo[campo] for campo in campos}))
    return CODIGO_DE_SAIDA[str(resumo["status"])]


if __name__ == "__main__":
    sys.exit(main())
