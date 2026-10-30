"""Reaplica os commits locais do Studio sobre a main, saneados (decisão da titular de 29/10/2026).

Para cada commit de <base>..<ponta>, na ordem: a MESMA árvore do commit original, trocando só os blobs saneados:
  - os logs .txt com caminho pessoal (texto: pasta do usuário -> %USERPROFILE%, pasta de projeto do agente -> <projeto>);
  - as capturas PNG com caminho/dado pessoal (blob original -> PNG tarjado, por SHA do blob).
Autor, datas e mensagem são os do original; a mensagem ganha a linha "Origem-local: <sha>". Nada é reescrito: a branch
nova é criada só no fim, e nenhuma ref existente é movida.

Uso: python -I reproduzir.py <repo> <base> <ponta> <branch-nova> <png-map.txt> <png-red-dir>
"""
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

repo, base, tip, branch, png_map, png_dir = sys.argv[1:7]
BS = chr(92)
USER = os.environ["USERNAME"]  # pasta do usuário que gerou as evidências (nunca escrita no arquivo)


def git(*args, inp=None, env=None):
    r = subprocess.run(["git", "-C", repo, *args], input=inp, capture_output=True, env=env)
    if r.returncode != 0:
        raise SystemExit("git " + " ".join(args) + ": " + r.stderr.decode("utf-8", "replace"))
    return r.stdout


def sanitize(data: bytes) -> bytes:
    t = data.decode("utf-8")
    t = t.replace("C--Users-" + USER + "-nekkora", "<projeto>")
    t = re.sub(re.escape("C:" + BS + "Users" + BS + USER), lambda m: "%USERPROFILE%", t, flags=re.IGNORECASE)
    t = re.sub(re.escape("C:/Users/" + USER), lambda m: "%USERPROFILE%", t, flags=re.IGNORECASE)
    t = re.sub(re.escape("/c/Users/" + USER), lambda m: "~", t, flags=re.IGNORECASE)
    if re.search(USER, t, re.IGNORECASE):
        raise SystemExit("sobrou o nome do usuário após o saneamento")
    return t.encode("utf-8")


LOGS = re.compile(r"^docs/(codegen-quality-001|spec-optional-columns-001)/.*\.txt$")
png_blob = {}
for line in Path(png_map).read_text(encoding="utf-8").splitlines():
    n, sha, path = line.split(" ", 2)
    red = next(Path(png_dir).glob(n + "-*.png"))
    png_blob[(sha, path)] = git("hash-object", "-w", str(red)).decode().strip()

text_cache = {}
commits = git("rev-list", "--reverse", base + ".." + tip).decode().split()
parent = git("rev-parse", base).decode().strip()
mapping = []
for c in commits:
    idx = tempfile.NamedTemporaryFile(delete=False)
    idx.close()
    env = dict(os.environ, GIT_INDEX_FILE=idx.name)
    git("read-tree", c, env=env)
    changed = 0
    for entry in git("ls-tree", "-r", "-z", c).decode("utf-8").split("\0"):
        if not entry:
            continue
        meta, path = entry.split("\t", 1)
        mode, kind, sha = meta.split()
        new = None
        if (sha, path) in png_blob:
            new = png_blob[(sha, path)]
        elif LOGS.match(path):
            if sha not in text_cache:
                data = git("cat-file", "blob", sha)
                if re.search(USER.encode(), data, re.IGNORECASE):
                    text_cache[sha] = git("hash-object", "-w", "--stdin", inp=sanitize(data)).decode().strip()
                else:
                    text_cache[sha] = sha
            new = text_cache[sha] if text_cache[sha] != sha else None
        if new:
            git("update-index", "--cacheinfo", mode + "," + new + "," + path, env=env)
            changed += 1
    tree = git("write-tree", env=env).decode().strip()
    os.unlink(idx.name)
    fmt = git("show", "-s", "--format=%an%x00%ae%x00%ad%x00%cn%x00%ce%x00%cd", "--date=raw", c).decode("utf-8").split("\0")
    msg = git("show", "-s", "--format=%B", c).decode("utf-8").rstrip("\n")
    last = msg.split("\n\n")[-1].splitlines()
    trailer = all(re.match(r"^[A-Za-z-]+: ", x) for x in last) and len(msg.split("\n\n")) > 1
    msg += ("\n" if trailer else "\n\n") + "Origem-local: " + c + "\n"
    cenv = dict(os.environ, GIT_AUTHOR_NAME=fmt[0], GIT_AUTHOR_EMAIL=fmt[1], GIT_AUTHOR_DATE=fmt[2],
                GIT_COMMITTER_NAME=fmt[3], GIT_COMMITTER_EMAIL=fmt[4], GIT_COMMITTER_DATE=fmt[5])
    new_commit = git("commit-tree", tree, "-p", parent, inp=msg.encode("utf-8"), env=cenv).decode().strip()
    mapping.append((c, new_commit, changed))
    parent = new_commit
git("branch", branch, parent)
for c, n, k in mapping:
    print(c[:7], "->", n[:7], "blobs saneados:", k)
print("branch", branch, parent)
