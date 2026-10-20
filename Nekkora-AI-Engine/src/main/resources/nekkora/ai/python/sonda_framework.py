"""Sonda do nekkora_rpa INSTALADO (STUDIO-014) — só leitura, sem rede, sem navegador.

Uso pelo Studio: ``python -I -B sonda_framework.py`` com um JSON ``{"id": "modulo:Objeto.atributo", ...}`` na entrada
padrão. Responde, na saída padrão, um JSON com a versão instalada, a assinatura de cada objeto (``inspect.signature``),
os ausentes e a impressão digital do conjunto. Nada do sistema-alvo, nenhum valor e nenhum caminho do usuário além da
pasta do pacote. O mesmo código gera o catálogo versionado (``tools/gerar_catalogo.py``), para que catálogo e
verificação nunca divirjam no método.
"""

from __future__ import annotations

import hashlib
import importlib
import inspect
import json
import sys
from typing import Any


def resolve(path: str) -> Any:
    module, _, attr = path.partition(":")
    obj: Any = importlib.import_module(module)
    for part in attr.split(".") if attr else []:
        obj = getattr(obj, part)
    return obj


def signature(obj: Any) -> str:
    target = obj.__init__ if inspect.isclass(obj) else obj
    try:
        sig = str(inspect.signature(target))
    except (TypeError, ValueError):
        return "<sem assinatura>"
    return sig.replace("(self, ", "(").replace("(self)", "()")


def fingerprint(signatures: dict[str, str]) -> str:
    text = "".join(f"{k}|{signatures[k]}\n" for k in sorted(signatures))
    return "sha256:" + hashlib.sha256(text.encode("utf-8")).hexdigest()


def describe(objects: dict[str, str]) -> dict[str, Any]:
    import nekkora_rpa

    found: dict[str, str] = {}
    missing: list[str] = []
    for cid, path in sorted(objects.items()):
        try:
            found[cid] = signature(resolve(path))
        except (ImportError, AttributeError):
            missing.append(cid)
    return {
        "framework": "nekkora-rpa",
        "version": nekkora_rpa.__version__,
        "python": "%d.%d.%d" % sys.version_info[:3],
        "signatures": found,
        "missing": missing,
        "fingerprint": fingerprint(found),
    }


def main() -> int:
    try:
        objects = json.loads(sys.stdin.read())
        if not isinstance(objects, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in objects.items()):
            raise ValueError
    except ValueError:
        print(json.dumps({"error": "INPUT_INVALID"}))
        return 2
    try:
        out = describe(objects)
    except ImportError:
        print(json.dumps({"error": "FRAMEWORK_NOT_INSTALLED"}))
        return 3
    print(json.dumps(out, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
