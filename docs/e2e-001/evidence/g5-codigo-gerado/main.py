# GERADO pelo Nekkora Studio (STUDIO-011) a partir do modelo validado. NÃO EDITE: correções vão para o
# modelo (debug guiado), e o código é gerado de novo. Arquivo editado à mão não é sobrescrito.
"""Execução: python main.py --saida <pasta> [--channel chrome|chromium] [--headless] [--console]"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from processo import Processo

from nekkora_rpa import Engine, EnvSecretProvider, load_config
from nekkora_rpa.data import JsonDirectoryResultSink
from nekkora_rpa.tabular import Column, DeferredTabularInputProvider, TabularInputProvider, TabularSchema

AQUI = Path(__file__).resolve().parent
ESQUEMA = TabularSchema(
    (
        Column("First Name"),
        Column("Last Name"),
        Column("Company Name"),
        Column("Role in Company"),
        Column("Address"),
        Column("Email"),
        Column("Phone Number", integer_as_text=True),
    ),
    sheet="Sheet1",
)
SAIDA = {"COMPLETED": 0, "COMPLETED_WITH_FAILURES": 1, "STOPPED": 2, "INIT_FAILED": 3, "CANCELLED": 4}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Processo gerado: rpa_challenge_input_forms")
    ap.add_argument("--saida", required=True, type=Path, help="pasta da execução (resultados, eventos, checkpoint)")
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
    processo = Processo(saida)
    provider = DeferredTabularInputProvider(lambda: processo.entrada, ESQUEMA)  # planilha baixada no INIT
    engine = Engine(
        processo,
        provider,
        JsonDirectoryResultSink(saida / "resultados"),
        config,
        secrets=EnvSecretProvider(processo.required_secrets),
        run_dir=saida / "execucao",
        console=a.console,
    )
    resumo = engine.run()
    processo.gravar_observacoes()
    d = resumo.to_dict()
    print(json.dumps({k: d[k] for k in ("execution_id", "status", "stop_code", "stop_detail", "counts")}))
    return SAIDA[resumo.status.value]


if __name__ == "__main__":
    sys.exit(main())
