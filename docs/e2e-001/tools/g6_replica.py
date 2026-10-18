"""NEKKORA-E2E-001 · G6 (laboratório) — executa a variante GERADA PELO STUDIO contra a réplica local do desafio.

A réplica (``lab/static/desafio`` do Nekkora-RPA-Framework) tem dados SINTÉTICOS e um oráculo no servidor que registra
cada Start e cada submissão. A conferência é do oráculo, nunca do Python gerado: 1 início, 10 submissões, rodadas
1..10 em ordem e valores iguais aos da linha correspondente. A saída não traz valores, só contagens e o texto do placar.

Uso: ``python g6_replica.py --framework <repo> --gerado <variante-lab> --python <venv/python> --evidencias <pasta>``
(o ``python`` desta ferramenta precisa importar ``lab.server`` do framework; a venv indicada roda o código gerado).
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--framework", required=True, type=Path)
    ap.add_argument("--gerado", required=True, type=Path)
    ap.add_argument("--python", required=True, type=Path)
    ap.add_argument("--evidencias", required=True, type=Path)
    ap.add_argument("--porta", type=int, default=18080)
    a = ap.parse_args()
    sys.path.insert(0, str(a.framework))
    from lab.server import DESAFIO, Lab  # réplica e oráculo do framework

    lab = Lab(port=a.porta).start()
    base = Path(tempfile.mkdtemp(prefix="nk-e2e001-replica-"))
    try:
        projeto = base / "projeto"
        shutil.copytree(a.gerado, projeto, ignore=shutil.ignore_patterns("*.novo", "*.diff"))
        saida = base / "execucao"
        r = subprocess.run([str(a.python), str(projeto / "main.py"), "--saida", str(saida), "--channel", "chromium", "--headless", "--console"],
                           capture_output=True, text=True, timeout=600, cwd=str(base), check=False)
        o = lab.oracle()
    finally:
        lab.stop()
    subs = o["desafio_submissoes"]
    observ = json.loads((saida / "observacoes.json").read_text(encoding="utf-8")) if (saida / "observacoes.json").exists() else None
    resumo = {
        "exit": r.returncode,
        "resumo_engine": r.stdout.strip().splitlines()[-1] if r.stdout.strip() else None,
        "oraculo": {
            "inicios": o["desafio_inicios"],
            "submissoes": len(subs),
            "rodadas_em_ordem": [s["rodada"] for s in subs] == list(range(1, 11)),
            "todas_corretas": len(subs) == 10 and all(s["valores"] == DESAFIO[i] for i, s in enumerate(subs)),
        },
        "placar_lido_pelo_codigo_gerado": observ["observacoes"] if observ else None,
    }
    a.evidencias.mkdir(parents=True, exist_ok=True)
    (a.evidencias / "replica-resumo.json").write_text(json.dumps(resumo, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for nome in ("events.jsonl", "summary.json"):
        f = saida / "execucao" / nome
        if f.exists():
            shutil.copyfile(f, a.evidencias / nome)
    print(json.dumps(resumo, ensure_ascii=False))
    return 0 if r.returncode == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
