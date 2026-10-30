# STUDIO-LEAN-001: remoção da venv local da evidência G5 (decisão 4 da titular, 24/10/2026)

A pasta `docs/e2e-001/evidence/g5-codigo-final/venv/` (12 022 534 bytes, 861 arquivos) **nunca foi versionada**. Era um
ambiente Python criado localmente em 18/10/2026 (`pyvenv.cfg`:
`python -m venv …\g5-codigo-final\venv`, Python 3.12.10). A titular autorizou a remoção **só depois** de confirmar
que a pasta é regenerável e não contém evidência nem arquivo necessário. A confirmação, feita antes de apagar:

| Verificação | Resultado |
|---|---|
| Rastreada pelo Git? | não: 0 arquivos em `git ls-files` |
| Pacotes instalados | **nenhum** além do `pip` 25.0.1 que o `venv` instala. O `nekkora-rpa` do `requirements.txt` nunca foi instalado nela |
| Arquivos do `pip` iguais ao original? | **442/442** conferem com os hashes do `RECORD` do próprio `pip`; nenhum divergente |
| Arquivos fora do `RECORD` (7) | `python.exe` e `pythonw.exe` idênticos byte a byte aos modelos de `Lib/venv/scripts/nt` do Python 3.12. `Activate.ps1` e `deactivate.bat` idênticos aos modelos. `activate`, `activate.bat` e `pyvenv.cfg` diferem só nos marcadores que o `venv` preenche (pasta e prompt) |
| Demais arquivos | 411 `.pyc` (cache de bytecode) |
| Referenciada por script, teste ou documento? | não. Só pelos documentos desta missão (`inventario.md`, `decisoes.md`), que a citam como sobra |
| Como recriar | `README.md` da própria evidência: `python -m venv .venv` + instalação do wheel com as restrições travadas |

**Feito:**

- a pasta foi removida (só localmente; nada mais da evidência G5 foi tocado);
- entrou no `.gitignore` uma regra **específica**, `/docs/e2e-001/evidence/g5-codigo-final/venv/`. Nenhum padrão
  genérico (`venv/`, `.venv/`) foi acrescentado, para não esconder por acidente outros ambientes ou artefatos;
- os demais arquivos da evidência G5 continuam visíveis para o Git.

O inventário completo (861 caminhos), o `pyvenv.cfg` e a conferência do `RECORD` ficam no pacote de revisão
(adendo).
