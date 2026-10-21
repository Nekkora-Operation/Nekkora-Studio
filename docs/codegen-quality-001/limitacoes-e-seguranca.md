# NEKKORA-CODEGEN-QUALITY-001 — Limitações conhecidas e segurança

## Limitações (declaradas, não escondidas)

1. **Nomes dependem do PDD revisado.** Sem título REVISADO de seção, a Page fica `Pagina<N>` (provisória) e a camada VQ
   aponta `NOME_SEM_EVIDENCIA`; sem título de passo, o método é nomeado pelo que faz (`ler_dados`, `clicar_sair`,
   `preencher_e_ler_…`). A heurística de português (1ª palavra de conteúdo; verbo no infinitivo) é simples e testada,
   não é análise linguística.
2. **Variáveis antigas mantêm o nome.** Projetos gravados antes desta missão podem ter variáveis como `campo_s`
   (derivadas antes da regra do marcador de plural): o código usa o nome da variável do modelo; a VQ não renomeia nada.
   Renomeie em Variáveis e dependências e desenvolva de novo.
3. **Âncora de região para linhas de tabela.** O localizador de linha (`//tr[th[…]]//td`) não é restrito a uma tabela
   específica: com dois quadros com o mesmo rótulo, o framework recusa (`TARGET_AMBIGUOUS`, testado) em vez de escolher.
   Restringir à região exigiria a extensão gravar a região da tabela (fora do escopo; nenhuma mudança na extensão).
4. **Gerador tabular 1.0.0** (configuração declarada, Automação → Avançado) não recebeu nomes/traceId (fora do escopo;
   byte a byte igual); a camada VQ o marca como `NOT_CHECKED`.
5. **Registro de rastreio fica nos dados do Studio** (como a especificação): em outra máquina, o `trace` do manifesto
   anterior da pasta de saída é a semente. Sem nenhum dos dois, o traceId é derivado de novo (pode mudar se o PDD mudou).
6. **Execução real** (V6) só pelo teste autorizado pela interface, na origem autorizada; nada é dado por executado por
   inspeção estática.

## Segurança e privacidade

- Nenhum valor de negócio no código: entradas por parâmetro/lista, segredos pelo provedor (`NK_SECRET_*`), leituras só
  em variáveis do processo. Docstrings usam títulos REVISADOS do PDD (já filtrados pela política de texto) e rótulos de
  interface que já estavam no registro de alvos — nunca texto de página.
- O UUID das etapas sai do corpo do código mas fica no manifesto e no `selectors.json` (`provenance.studioStepId`): a
  correlação com gravação, debug, PDD e execução continua exata (mapa bijetivo testado).
- Nenhum canal Studio ↔ Platform; nenhuma mudança no Agent, no framework ou na extensão; nenhuma publicação.
- D13 (`FRAMEWORK_CATALOG_MISSING`) continua soberano: a VQ fica `NOT_CHECKED` e nada é dado por validado.
- A análise AST roda no mesmo intérprete isolado de V4/V5 (`-I -B`, sem rede, sem executar o projeto) e só devolve
  códigos e identificadores gerados.
