"""Qualidade da GERAÇÃO (NEKKORA-CODEGEN-QUALITY-001, camada VQ) — análise pela AST, sem executar o projeto.

Uso pelo Studio: ``python -I -B qualidade_codigo.py <pasta-do-projeto>``. Responde um JSON com os achados objetivos
(código, arquivo, símbolo, linha); nunca trechos de código, valores ou caminhos do usuário. Verifica nas Pages e no
processo: UUID ou comentário por etapa no código, nome opaco/genérico, variável de uma letra, docstring ausente em
classe/método público, ``step_id`` sem mapeamento no manifesto, seletor escrito no código e falha silenciada
(``or ""``, ``except: pass``). No código em inglês (pacote ``automation/``, rodada 3) também: identificador com palavra
portuguesa (classe, função, parâmetro, variável, atributo, membro do ``SelectorKey``) e chave de alvo escrita como texto
nas telas; na rodada 5, ``@dataclass`` sem ``repr=False`` (o repr automático mostraria os valores). Passar aqui NÃO é aceite de legibilidade: esse é da titular.
"""

from __future__ import annotations

import ast
import io
import json
import re
import sys
import tokenize
from pathlib import Path
from typing import Any

UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
OPAQUE = re.compile(
    r"^((tela|pagina|page)\d+|etapas_sessao_\d+|fill|press_key|click|observe|wait_for|navigate|campo_s|alvo(_.*)?|tmp|aux|var\d*|"
    r"dados\d+|data\d+|metodo\d*|method\d*|processo\d*)$",
    re.IGNORECASE,
)
SELECTOR = re.compile(r"(^//|^\(//|\[@|\[name=|\[data-|>>|:nth-|^#[A-Za-z]|^[a-z]+#[A-Za-z])")
LEGACY_FILES = ("paginas.py", "processo.py")
PACKAGE_FILES = ("automation/pages.py", "automation/process.py", "automation/selectors.py", "main.py")
# Palavras portuguesas comuns em nomes de código (sem as que também são inglesas: area, data, total, status, link, menu...).
# Achar uma delas num identificador do código em inglês é regressão do gerador.
PORTUGUESE = frozenset(
    "pagina paginas dados dado ficha fichas acoes acao elemento elementos entrada entradas saida saidas planilha linha linhas "
    "coluna colunas pesquisa pesquisar busca buscar ler preencher clicar conferir verificar endereco nome nomes completo "
    "nascimento morte termo campo campos texto titulo processo segredos segredo resultado resultados valor valores segura "
    "seguro acesso entrar sair executar tela telas abrir inicial esperado esperada artigo biografias biografia pessoa "
    "usuario senha credencial codigo registro consulta extracao ocupacao nacionalidade antes aqui pasta ajustes destino "
    "lista erro publicados resumo chave chaves alvo etapa etapas sessao aguardar pressionar enviar".split()
)
WORD = re.compile(r"[A-Z]?[a-z]+|[A-Z]+(?![a-z])|\d+")


def finding(out: list[dict[str, Any]], code: str, file: str, symbol: str, line: int) -> None:
    out.append({"code": code, "file": file, "symbol": symbol, "line": line})


def symbol_of(stack: list[str]) -> str:
    return ".".join(stack) if stack else "<módulo>"


def portuguese(name: str) -> bool:
    return any(w.lower() in PORTUGUESE for w in WORD.findall(name))


def exposes_values(decorator: ast.expr) -> bool:
    """``@dataclass`` sem ``repr=False``: o repr automático mostra os valores (entrada ou leitura, às vezes pessoais)."""
    call = decorator if isinstance(decorator, ast.Call) else None
    func = call.func if call else decorator
    name = func.id if isinstance(func, ast.Name) else func.attr if isinstance(func, ast.Attribute) else ""
    if name != "dataclass":
        return False
    return not (call and any(k.arg == "repr" and isinstance(k.value, ast.Constant) and k.value.value is False for k in call.keywords))


class Analyzer(ast.NodeVisitor):
    def __init__(
        self, file: str, out: list[dict[str, Any]], traces: set[str], synthetic: set[str], english: bool, system: str
    ) -> None:
        self.file = file
        self.out = out
        self.traces = traces
        self.synthetic = synthetic
        self.english = english
        self.system = system
        self.stack: list[str] = []
        self.pages = file in ("paginas.py", "automation/pages.py")

    def add(self, code: str, node: ast.AST) -> None:
        finding(self.out, code, self.file, symbol_of(self.stack), getattr(node, "lineno", 0))

    def name(self, name: str, node: ast.AST) -> None:
        if self.english and portuguese(name):
            self.add("IDENTIFICADOR_EM_PORTUGUES", node)

    def visit_ClassDef(self, node: ast.ClassDef) -> None:
        self.stack.append(node.name)
        self.name(node.name, node)
        if OPAQUE.match(node.name):
            self.add("NOME_GENERICO", node)
        if ast.get_docstring(node) is None:
            self.add("DOCSTRING_AUSENTE", node)
        if self.english and any(exposes_values(d) for d in node.decorator_list):
            self.add("DATACLASS_REPR_COM_VALORES", node)
        self.generic_visit(node)
        self.stack.pop()

    def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
        self.stack.append(node.name)
        self.name(node.name, node)
        public = not node.name.startswith("_")
        if OPAQUE.match(node.name):
            self.add("NOME_GENERICO", node)
        if public and ast.get_docstring(node) is None:
            self.add("DOCSTRING_AUSENTE", node)
        for a in node.args.args + node.args.kwonlyargs:
            self.name(a.arg, a)
            if len(a.arg) == 1:
                self.add("VARIAVEL_OPACA", a)
        self.generic_visit(node)
        self.stack.pop()

    def visit_Assign(self, node: ast.Assign) -> None:
        for t in node.targets:
            for n in ast.walk(t):
                if isinstance(n, ast.Name):
                    self.name(n.id, node)
            if isinstance(t, ast.Name) and len(t.id) == 1:
                self.add("VARIAVEL_OPACA", node)
        self.generic_visit(node)

    def visit_AnnAssign(self, node: ast.AnnAssign) -> None:
        if isinstance(node.target, ast.Name):
            self.name(node.target.id, node)
        self.generic_visit(node)

    def visit_Constant(self, node: ast.Constant) -> None:
        if isinstance(node.value, str):
            if UUID.search(node.value):
                self.add("UUID_EXPOSTO_NO_CODIGO", node)
            if self.pages and SELECTOR.search(node.value.strip()):
                self.add("SELETOR_NO_CODIGO", node)
            if self.pages and self.english and self.system and node.value.startswith(self.system + "."):
                self.add("CHAVE_DE_ALVO_LITERAL", node)

    def visit_keyword(self, node: ast.keyword) -> None:
        if node.arg == "step_id" and isinstance(node.value, ast.Constant) and isinstance(node.value.value, str):
            value = node.value.value
            if value not in self.traces and value not in self.synthetic:
                self.add("TRACE_SEM_MAPEAMENTO", node.value)
        self.generic_visit(node)

    def visit_BoolOp(self, node: ast.BoolOp) -> None:
        last = node.values[-1]
        if isinstance(node.op, ast.Or) and isinstance(last, ast.Constant) and last.value == "":
            self.add("SILENCIA_FALHA", node)
        self.generic_visit(node)

    def visit_ExceptHandler(self, node: ast.ExceptHandler) -> None:
        if node.type is None or all(isinstance(b, ast.Pass) for b in node.body):
            self.add("SILENCIA_FALHA", node)
        self.generic_visit(node)


def run(root: Path) -> dict[str, Any]:
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    trace = manifest.get("trace") if isinstance(manifest.get("trace"), dict) else {}
    mappings = trace.get("mappings", [])
    traces = {m.get("traceId") for m in mappings}
    synthetic = {x.get("traceId") for x in trace.get("synthetic", [])}
    package = (root / "automation" / "__init__.py").is_file()
    english = package and manifest.get("language") == "en"
    system = ""
    if package:
        system = str(json.loads((root / "selectors.json").read_text(encoding="utf-8")).get("system", ""))
    out: list[dict[str, Any]] = []
    if not mappings:
        finding(out, "TRACE_SEM_MAPEAMENTO", "manifest.json", "trace", 0)
    if len({m.get("studioStepId") for m in mappings}) != len(mappings) or len(traces) != len(mappings):
        finding(out, "TRACE_ALIAS_CONFLITO", "manifest.json", "trace", 0)
    for name in PACKAGE_FILES if package else LEGACY_FILES:
        text = (root / name).read_text(encoding="utf-8")
        Analyzer(name, out, traces, synthetic, english, system).visit(ast.parse(text, name))
        for tok in tokenize.generate_tokens(io.StringIO(text).readline):
            if tok.type == tokenize.COMMENT and (UUID.search(tok.string) or re.match(r"#\s*(etapa|step)\b", tok.string)):
                finding(out, "COMENTARIO_POR_ETAPA", name, "<comentário>", tok.start[0])
    return {"findings": out, "total": len(out)}


def main() -> int:
    if len(sys.argv) != 2 or not Path(sys.argv[1]).is_dir():
        print(json.dumps({"error": "ARGS"}))
        return 2
    try:
        result = run(Path(sys.argv[1]))
    except Exception as exc:  # noqa: BLE001
        print(json.dumps({"error": "QUALITY_RUN_FAILED", "type": type(exc).__name__}))
        return 3
    print(json.dumps(result, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
