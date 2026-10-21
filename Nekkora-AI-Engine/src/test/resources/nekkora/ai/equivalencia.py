"""Equivalência de comportamento entre dois projetos gerados (NEKKORA-CODEGEN-QUALITY-001, teste).

Uso: python -I -B equivalencia.py <pasta-do-projeto> <cenário.json>
Importa paginas.py/processo.py do projeto com um ``Actions`` FALSO (nada de navegador, nada de rede) e roda UMA
transação. Cada chamada ao framework vira ``[ação, índice da etapa no modelo]`` — o índice sai do PRÓPRIO manifesto
(``step_id`` = StepId no gerador antigo; traceId → StepId pelo ``trace`` no novo), então dois geradores diferentes
podem ser comparados chamada a chamada. O cenário diz o que cada leitura devolve (pelo índice da etapa) e quais alvos
não existem. Saída: JSON com as chamadas e o desfecho (linha devolvida ou recusa).
"""

from __future__ import annotations

import importlib
import json
import sys
import types
from pathlib import Path
from typing import Any


def main() -> int:
    root = Path(sys.argv[1]).resolve()
    scenario = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    steps = manifest["steps"]
    index_of_step = {s["stepId"]: i for i, s in enumerate(steps)}
    trace = manifest.get("trace") or {}
    step_of_trace = {m["traceId"]: m["studioStepId"] for m in trace.get("mappings", [])}
    index_of_key: dict[str, int] = {}
    for i, s in enumerate(steps):
        if s.get("key") and s["key"] not in index_of_key:
            index_of_key[s["key"]] = i
    targets = json.loads((root / "selectors.json").read_text(encoding="utf-8"))["targets"]
    values = {int(k): v for k, v in scenario["values"].items()}
    absent = set(scenario.get("absent", []))
    calls: list[list[Any]] = []

    def idx(step_id: str | None) -> Any:
        if step_id is None:
            return None
        sid = step_of_trace.get(step_id, step_id)
        return index_of_step.get(sid, step_id)

    def key_index(key: str) -> int:
        return index_of_key[key]

    def locator(key: str) -> str:
        """O ELEMENTO usado (localizador preferido do registro): compara chaves diferentes para o mesmo alvo."""
        preferred = targets[key]["preferred"]
        return str(preferred.get("expression") or preferred)

    class Fake:
        def fill(self, key: str, value: str, *, step_id: str | None = None, **_: Any) -> None:
            calls.append(["fill", idx(step_id), locator(key)])

        def press(self, key: str, name: str, *, step_id: str | None = None, postcondition: Any = None, **_: Any) -> None:
            calls.append(["press", idx(step_id), locator(key)])
            if postcondition is not None:
                scenario["navigated"] = True
                assert postcondition()

        def click(self, key: str, *, step_id: str | None = None, postcondition: Any = None, **_: Any) -> None:
            calls.append(["click", idx(step_id), locator(key)])
            if postcondition is not None:
                assert postcondition()

        def current_address(self, *, step_id: str | None = None) -> str:
            if step_id is not None:
                calls.append(["current_address", idx(step_id)])
            return str(scenario["address"] if scenario.get("navigated", True) else "about:blank")

        def wait_visible(self, key: str, *, step_id: str | None = None, **_: Any) -> None:
            calls.append(["wait_visible", idx(step_id), locator(key)])

        def exists(self, key: str, *, step_id: str | None = None) -> bool:
            calls.append(["exists", idx(step_id), locator(key)])
            return key_index(key) not in absent

        def read_items(self, key: str, *, step_id: str | None = None, **_: Any) -> tuple[str, ...]:
            calls.append(["read_items", idx(step_id), locator(key)])
            if key_index(key) in absent:
                raise RuntimeError("TARGET_NOT_FOUND")
            return tuple(values.get(key_index(key), []))

        def read_text(self, key: str, *, step_id: str | None = None, **_: Any) -> str:
            calls.append(["read_text", idx(step_id), locator(key)])
            return "; ".join(values.get(key_index(key), []))

        def navigate(self, url: str, *, step_id: str | None = None, **_: Any) -> None:
            calls.append(["navigate", idx(step_id) if step_id != "INICIO" else "INICIO"])

    sys.path.insert(0, str(root))
    processo = importlib.import_module("processo")
    from nekkora_rpa import RpaProcess, TransactionItem
    from nekkora_rpa.errors import BusinessRuleRejected

    cls = next(c for c in vars(processo).values() if isinstance(c, type) and issubclass(c, RpaProcess) and c is not RpaProcess
               and c.__module__ == "processo")
    rt = types.SimpleNamespace(act=Fake(), secrets=None)
    outcome: dict[str, Any]
    try:
        result = cls().process(rt, TransactionItem("x", scenario["input"]))  # type: ignore[arg-type]
        outcome = {"status": "SUCCEEDED", "row": dict(result.output)}
    except BusinessRuleRejected:
        outcome = {"status": "BUSINESS_REJECTED"}
    except RuntimeError as err:
        outcome = {"status": "TECHNICAL", "detail": str(err)}
    print(json.dumps({"calls": calls, "outcome": outcome}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
