# GERADO pelo Nekkora Studio ("Desenvolver automação") a partir do modelo validado e do PDD pronto.
# Não edite: correções vão para o modelo ou o PDD e o código é gerado de novo (arquivo editado à mão não é sobrescrito).
"""Execução única.

Uso: python main.py --saida <pasta>
    [--channel chrome|chromium] [--headless] [--console]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from nekkora_rpa import Engine, EnvSecretProvider, SingleInputProvider, TransactionItem, load_config
from nekkora_rpa.data import JsonDirectoryResultSink

from processo import AreaSeguraTheInternet

AQUI = Path(__file__).resolve().parent
CODIGO_DE_SAIDA = {"COMPLETED": 0, "COMPLETED_WITH_FAILURES": 1, "STOPPED": 2, "INIT_FAILED": 3, "CANCELLED": 4}


def main(argv: list[str] | None = None) -> int:
    """Lê os argumentos, executa o processo e imprime o resumo (sem valores); devolve o código de saída."""
    parser = argparse.ArgumentParser(description="Processo gerado: area_segura_the_internet")
    parser.add_argument("--saida", required=True, type=Path, help="pasta da execução (resultados, eventos, checkpoint)")
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
    processo = AreaSeguraTheInternet()
    item = TransactionItem("execucao-unica", {})
    engine = Engine(
        processo,
        SingleInputProvider(item),
        JsonDirectoryResultSink(pasta / "resultados"),
        config,
        secrets=EnvSecretProvider(processo.required_secrets),
        run_dir=pasta / "execucao",
        console=args.console,
    )
    resumo = engine.run()
    campos = resumo.to_dict()
    publicados = ("execution_id", "status", "stop_code", "stop_detail", "counts")
    print(json.dumps({chave: campos[chave] for chave in publicados}))
    return CODIGO_DE_SAIDA[resumo.status.value]


if __name__ == "__main__":
    sys.exit(main())
