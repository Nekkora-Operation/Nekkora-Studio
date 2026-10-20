# NEKKORA-PACKAGING-001 — limitações conhecidas

1. **Sem publicação**: `nekkora-rpa` não está no TestPyPI nem no PyPI. `pip install "nekkora-rpa==…"` fora do Studio
   só funcionará depois das ações da titular (licença, nome, OIDC, aprovações). Até lá, o caminho é o "Preparar
   ambiente" do Studio (wheel embutido).
2. **Primeira preparação exige rede** para as dependências (≈150 MB do PyPI oficial: playwright, openpyxl, ruff,
   mypy). Sem rede, diagnóstico NETWORK_UNAVAILABLE/PROXY_OR_SSL; não há modo offline com cache de wheels.
3. **Navegadores** não são instalados (`playwright install` não roda): o teste autorizado usa o Chrome instalado.
4. **Windows com caminhos longos desativados** (`LongPathsEnabled=0`): pasta de dados muito profunda dá
   PATH_TOO_LONG antes de instalar; a recuperação é usar uma pasta de dados mais curta ou ativar caminhos longos.
5. **sdist não é byte a byte reprodutível** (datas no tar); o wheel é. Wheels construídos no Windows e no Linux
   diferem (setuptools grava METADATA com CRLF no Windows) — por isso o wheel embutido é construído em Linux.
6. Disco mínimo de 600 MB livres para preparar um ambiente.
7. Um ambiente por automação e versão: duas automações com o mesmo requisito têm venvs separadas (mais disco, sem
   interferência).
8. O teste autorizado continua exigindo autorização explícita e não foi executado nesta missão.
9. Um único `dependencias.lock` serve a todas as versões: a venv de um projeto `nekkora-rpa==0.2.0` (sem o extra
   excel) recebe também o openpyxl. O plano mostrado antes da confirmação já lista o openpyxl; a verificação do extra
   excel só é exigida quando o código o pede. Separar locks por extra fica para uma próxima rodada.
10. Na aba Resultado, a linha do diagnóstico de versão divergente é cortada na largura da janela ("o projeto exige
    0.…"). Cosmético; o diagnóstico em si está correto.
