# GERADO pelo Nekkora Studio (STUDIO-014, "Desenvolver automação") do modelo validado e do PDD pronto.
# NÃO EDITE: correções vão para o modelo ou o PDD e o código é gerado de novo.
# Arquivo editado à mão não é sobrescrito.
"""Execução: python main.py --saida <pasta> --entrada <entrada.json> [--channel chrome|chromium] [--headless]"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from nekkora_rpa import Engine, EnvSecretProvider, SingleInputProvider, TransactionItem, load_config
from nekkora_rpa.data import JsonDirectoryResultSink

from processo import Processo

AQUI = Path(__file__).resolve().parent
SAIDA = {"COMPLETED": 0, "COMPLETED_WITH_FAILURES": 1, "STOPPED": 2, "INIT_FAILED": 3, "CANCELLED": 4}


def ler_entrada(caminho: Path) -> dict[str, object]:
    """Objeto JSON com as entradas da execução; os valores nunca são impressos nem registrados."""
    dados: object = json.loads(caminho.read_text(encoding="utf-8"))
    if not isinstance(dados, dict):
        raise SystemExit("entrada: o arquivo precisa ser um objeto JSON")
    return {str(k): v for k, v in dados.items()}


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Processo gerado: consulta_de_registro_sintetica")
    ap.add_argument("--saida", required=True, type=Path, help="pasta da execução (resultados, eventos, checkpoint)")
    ap.add_argument("--entrada", required=True, type=Path, help="objeto JSON com as entradas")
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
    processo = Processo()
    item = TransactionItem("execucao-unica", ler_entrada(a.entrada))
    engine = Engine(
        processo,
        SingleInputProvider(item),
        JsonDirectoryResultSink(saida / "resultados"),
        config,
        secrets=EnvSecretProvider(processo.required_secrets),
        run_dir=saida / "execucao",
        console=a.console,
    )
    resumo = engine.run()
    d = resumo.to_dict()
    print(json.dumps({k: d[k] for k in ("execution_id", "status", "stop_code", "stop_detail", "counts")}))
    return SAIDA[resumo.status.value]


if __name__ == "__main__":
    sys.exit(main())
