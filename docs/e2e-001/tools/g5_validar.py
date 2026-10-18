"""NEKKORA-E2E-001 · G5 — validação do projeto GERADO PELO STUDIO, sem alterá-lo.

1. venv LIMPA (só ``nekkora-rpa[excel]`` do wheel): compila todos os ``.py``, importa ``paginas``/``processo``, carrega
   ``selectors.json`` pelo ``SelectorRegistry`` do framework no ambiente da configuração e a configuração por
   ``load_config``;
2. ferramentas de desenvolvimento (este interpretador): ``selectors.json`` contra o esquema do contrato empacotado no
   framework; manifesto (hash de cada arquivo, todo StepId presente no código); ``ruff check`` e ``mypy --strict``;
3. contratos das SAÍDAS de uma execução real (``--execucao``): eventos JSONL, checkpoint e resumo contra os esquemas.

Uso: ``python g5_validar.py --gerado <pasta> --python-limpo <venv/python> [--execucao <evidências do G6>] --saida <json>``
"""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
from importlib import resources
from pathlib import Path

import jsonschema

LIMPO = r"""
import compileall, json, sys, tomllib
from pathlib import Path
g = Path(sys.argv[1]); sys.path.insert(0, str(g))
ok = compileall.compile_dir(str(g), quiet=1, force=True)
import paginas, processo  # noqa: F401
from nekkora_rpa import load_config
from nekkora_rpa.selectors.registry import SelectorRegistry
cfg = tomllib.loads((g / "config" / "execucao.toml").read_text(encoding="utf-8"))
reg = SelectorRegistry.from_text((g / "selectors.json").read_text(encoding="utf-8"), environment=cfg["runtime"]["environment"])
load_config(g / "config" / "execucao.toml", {})
print(json.dumps({"compila": bool(ok), "importa": True, "registro_carrega": True, "chaves": len(reg.keys()) if hasattr(reg, "keys") else None}))
"""


def schema(name: str) -> dict[str, object]:
    return json.loads(resources.files("nekkora_rpa").joinpath("contracts", name).read_text(encoding="utf-8"))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--gerado", required=True, type=Path)
    ap.add_argument("--python-limpo", required=True, type=Path)
    ap.add_argument("--execucao", type=Path)
    ap.add_argument("--saida", required=True, type=Path)
    a = ap.parse_args()
    g = a.gerado
    out: dict[str, object] = {}
    r = subprocess.run([str(a.python_limpo), "-c", LIMPO, str(g)], capture_output=True, text=True, check=False)
    out["venv_limpa"] = json.loads(r.stdout.strip().splitlines()[-1]) if r.returncode == 0 else {"erro": r.stderr[-1500:]}
    sel = json.loads((g / "selectors.json").read_text(encoding="utf-8"))
    jsonschema.validate(sel, schema(f"selectors-{sel['version']}.schema.json"))
    out["selectors_contrato"] = f"selectors-{sel['version']} válido"
    m = json.loads((g / "manifest.json").read_text(encoding="utf-8"))
    hashes = {f: "sha256:" + hashlib.sha256((g / f).read_bytes()).hexdigest() == h for f, h in m["files"].items()}
    codigo = (g / "paginas.py").read_text(encoding="utf-8")
    out["manifesto"] = {"hashes_conferem": all(hashes.values()), "arquivos": len(hashes),
                        "stepids_no_codigo": sum(s["stepId"] in codigo for s in m["steps"]), "stepids": len(m["steps"]),
                        "planRevision": m["planRevision"]}
    py = sorted(p.name for p in g.glob("*.py"))
    # perfil do framework (pyproject do nekkora_rpa), independente de configuração global da máquina; roda DENTRO da
    # pasta gerada para que paginas/processo sejam reconhecidos como módulos do próprio projeto (isort)
    perfil = ["--isolated", "--line-length", "120", "--select", "E,F,W,I,B,UP,N,S,BLE,RUF"]
    rf = subprocess.run([sys.executable, "-m", "ruff", "check", "--no-cache", *perfil, *py], capture_output=True, text=True,
                        check=False, cwd=str(g))
    out["ruff"] = "OK" if rf.returncode == 0 else rf.stdout[-1500:]
    mp = subprocess.run([sys.executable, "-m", "mypy", "--strict", "--no-incremental", "--cache-dir=nul" if sys.platform == "win32" else "--cache-dir=/dev/null", *py],
                        capture_output=True, text=True, check=False, cwd=str(g))
    out["mypy_strict"] = "OK" if mp.returncode == 0 else mp.stdout[-2500:]
    if a.execucao:
        ev = [json.loads(x) for x in (a.execucao / "events.jsonl").read_text(encoding="utf-8").splitlines() if x.strip()]
        es = schema("events-1.1.0.schema.json")
        for e in ev:
            jsonschema.validate(e, es)
        jsonschema.validate(json.loads((a.execucao / "checkpoint.json").read_text(encoding="utf-8")), schema("checkpoint-1.1.0.schema.json"))
        jsonschema.validate(json.loads((a.execucao / "summary.json").read_text(encoding="utf-8")), schema("run-summary-1.1.0.schema.json"))
        steps = {s["stepId"] for s in m["steps"]}
        vistos = {e.get("step_id") for e in ev if e.get("step_id")}
        out["saidas_execucao"] = {"eventos": len(ev), "eventos_validos": True, "checkpoint_valido": True, "resumo_valido": True,
                                  "stepids_do_modelo_nos_eventos": len(steps & vistos), "stepids_do_modelo": len(steps)}
    a.saida.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(out, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
