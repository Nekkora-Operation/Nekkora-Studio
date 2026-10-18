"""NEKKORA-E2E-001 · G6 — executa o código GERADO PELO STUDIO no Chrome real contra o desafio (operador: TEST_AGENT).

1. constrói o wheel do framework e instala numa venv NOVA fora dos repositórios (``[excel]`` + ``constraints/lab.txt``);
2. copia o projeto gerado (sem alterar nada) para uma pasta de trabalho fora dos repositórios;
3. roda ``main.py`` com o Chrome instalado (janela visível) — cada execução é um navegador novo, logo uma tentativa nova
   do desafio; a tentativa é rotulada (``depuracao-N`` ou ``oficial``) e nunca reaproveitada;
4. guarda evidências SEM valores: resumo, eventos JSONL, checkpoint, observações (texto do resultado exibido pela página)
   e o hash da planilha baixada; verifica que nenhum valor da planilha aparece nelas.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


def sh(cmd: list[str], **kw: object) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, capture_output=True, text=True, check=False, **kw)  # type: ignore[call-overload]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--framework", required=True, type=Path)
    ap.add_argument("--gerado", required=True, type=Path)
    ap.add_argument("--evidencias", required=True, type=Path)
    ap.add_argument("--rotulo", required=True)
    ap.add_argument("--valores", type=Path, help="planilha baixada no G1 (só para varrer as evidências; nunca copiada)")
    ap.add_argument("--venv", type=Path, help="reusar uma venv já preparada por este script")
    a = ap.parse_args()
    base = Path(tempfile.mkdtemp(prefix="nk-e2e001-g6-"))
    if a.venv is None:
        dist = base / "dist"
        r = sh([sys.executable, "-m", "build", "--wheel", "--outdir", str(dist), str(a.framework)])
        if r.returncode:
            print(r.stderr[-2000:])
            return 2
        wheel = next(dist.glob("nekkora_rpa-*.whl"))
        venv = base / "venv"
        sh([sys.executable, "-m", "venv", str(venv)])
        py = venv / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
        r = sh([str(py), "-m", "pip", "install", "-q", f"{wheel}[excel]", "-c", str(a.framework / "constraints" / "lab.txt")])
        if r.returncode:
            print(r.stderr[-2000:])
            return 2
        print(json.dumps({"venv": str(venv), "wheel": wheel.name}))
    else:
        venv = a.venv
        py = venv / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    projeto = base / "projeto"
    shutil.copytree(a.gerado, projeto, ignore=shutil.ignore_patterns("*.novo", "*.diff"))
    saida = base / "execucao"
    env = {k: v for k, v in os.environ.items() if not k.startswith(("PYTHONPATH", "NK_SECRET_"))}
    r = sh([str(py), str(projeto / "main.py"), "--saida", str(saida), "--channel", "chrome", "--console"], cwd=str(base), env=env, timeout=900)
    destino = a.evidencias / a.rotulo
    destino.mkdir(parents=True, exist_ok=True)
    (destino / "stdout.txt").write_text(r.stdout, encoding="utf-8")
    (destino / "stderr.txt").write_text(r.stderr, encoding="utf-8")
    for nome in ("events.jsonl", "checkpoint.json", "summary.json"):
        f = saida / "execucao" / nome
        if f.exists():
            shutil.copyfile(f, destino / nome)
    if (saida / "observacoes.json").exists():
        shutil.copyfile(saida / "observacoes.json", destino / "observacoes.json")
    estados = {}
    for f in (saida / "resultados").glob("*.json"):
        d = json.loads(f.read_text(encoding="utf-8"))
        estados[d["status"]] = estados.get(d["status"], 0) + 1
    (destino / "resultados-contagem.json").write_text(json.dumps(estados) + "\n", encoding="utf-8")
    # varredura: nenhum valor da planilha nas evidências copiadas
    vazou = []
    if a.valores and a.valores.exists():
        import openpyxl

        wb = openpyxl.load_workbook(a.valores, read_only=True)
        valores = {str(v).strip() for row in list(wb[wb.sheetnames[0]].iter_rows(values_only=True))[1:] for v in row
                   if v is not None and len(str(v).strip()) >= 4}
        wb.close()
        for f in destino.iterdir():
            texto = f.read_text(encoding="utf-8")
            vazou += [f.name for v in valores if v in texto]
    print(json.dumps({"rotulo": a.rotulo, "exit": r.returncode, "resumo": r.stdout.strip().splitlines()[-1] if r.stdout.strip() else None,
                      "resultados": estados, "valores_vazados_em": sorted(set(vazou)), "venv": str(venv)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
