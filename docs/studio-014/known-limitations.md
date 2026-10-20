# STUDIO-014 — o que NÃO é garantido

"Garantia" aqui é um processo verificável de qualidade, não ausência de defeitos.

1. **Execução real (V6) nunca é verificada pelo Studio.** O Studio não executa automações. A execução do Caso A no
   laboratório, com o oráculo do servidor, foi feita pela homologação, fora do Studio, e está registrada como evidência.
2. **Integração real com a Anthropic não homologada.** Não havia chave nesta máquina. O provedor está implementado e
   coberto por testes de contrato com um servidor local que imita o provedor e por um mock rotulado; nenhuma chamada real
   foi feita.
3. **Framework 0.2.0:** execução única gerada só para origens de laboratório (loopback); um site real fica
   `SECURITY_BLOCKED` até existir um ambiente autorizado no framework. Alvos em iframe ou shadow DOM não são gerados
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
10. **Revisão humana:** o gate registra os revisores dos textos do PDD (`DEVELOPER` ou `TEST_AGENT`); revisão do agente
    não é aprovação de negócio. Nesta homologação o operador foi o agente de teste.
