# STUDIO-014 — o que NÃO é garantido

"Garantia" aqui é um processo verificável de qualidade, não ausência de defeitos.

1. **Execução real (V6) nunca é verificada pelo Studio.** O Studio não executa automações. A execução do Caso A no
   laboratório, com o oráculo do servidor, foi feita pela homologação, fora do Studio, e está registrada como evidência.
2. **Integração real com a Anthropic não homologada.** A única chamada real respondeu HTTP 400, porque a chave não está
   ligada a um workspace. O cabeçalho `anthropic-workspace-id` (via `NEKKORA_ANTHROPIC_WORKSPACE_ID`) já é enviado
   quando configurado, mas falta o id. O provedor segue coberto por testes de contrato com um servidor local que imita o
   provedor e por um mock rotulado.
3. **Framework 0.2.0:** execução única gerada só para o laboratório (loopback) ou para sites públicos de treinamento
   `https` que a pessoa autorizou no projeto (ambiente `benchmark`, D-S014-19). Qualquer outro site fica
   `SECURITY_BLOCKED`, pois não há ambiente de produção. Alvos em iframe ou shadow DOM não são gerados
   (pedido de ajuda). Ações suportadas pelo gerador: Navegar, Preencher, Clicar, Aguardar e Observar; as demais
   (selecionar opção, marcar, tecla…) aparecem como `UNSUPPORTED_CAPABILITY`, com o caminho de extensão.
4. **Leitura de resultado:** o código lê o alvo assim que ele existe. Se o elemento já estiver na tela antes de o
   resultado chegar, a leitura pode pegar o texto anterior — aviso no plano; o 0.2.0 não tem a espera "texto mudou".
5. **Endereço inicial:** a gravação guarda só o modelo do endereço; sem uma etapa Navegar, a pessoa informa o endereço uma
   vez (conferido contra o modelo).
6. **"Ler o resultado" na gravação:** o Recorder descarta cliques em elementos não interativos. Uma área de resultado só é
   apontada se for focável ou interativa; senão, a etapa Observar precisa do alvo pela correção do debug guiado.
7. **Efeito de clique:** derivado só de um vocabulário fechado de rótulos; qualquer outro rótulo vira pergunta. A
   classificação derivada é procedência "derivado do rótulo", não prova.
8. **Lote tabular:** continua exigindo a configuração declarada do projeto (`<projeto>.codegen.json`) para colunas e
   planilha; o Studio não deduz cabeçalhos.
9. **Ferramentas Python:** V4/V5 só rodam com um interpretador que tenha `nekkora_rpa`, ruff e mypy (Opções avançadas,
   `NEKKORA_PYTHON` ou `.venv` na pasta gerada). Sem ele, as camadas ficam "não verificado".
10. **Alvo que só se distingue por dado de negócio** (ex.: o "Add to cart" de um produto, pelo nome do produto): a
    privacidade retém o dado, a gravação marca o alvo como não resolvido e o Studio não gera localizador posicional.
    Ver [sites-publicos.md](sites-publicos.md).
11. **Revisão humana:** o gate registra os revisores dos textos do PDD (`DEVELOPER` ou `TEST_AGENT`); revisão do agente
    não é aprovação de negócio. Nesta homologação o operador foi o agente de teste.
