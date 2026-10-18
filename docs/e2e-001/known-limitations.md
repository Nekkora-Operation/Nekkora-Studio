# NEKKORA-E2E-001 — Limitações conhecidas

1. **Identidade do debug não sobrevive à recriação de campos sem nome.** O debug resolve pela identidade REGISTRADA
   (`targetId`/descritor), não pelo localizador. Depois de recarregar a página, os 7 campos do desafio aparecem como
   `AMBIGUOUS` (`INDISTINGUISHABLE_AT_REGISTRATION`, `LOOKALIKES_PRESENT`): na homologação final, 8 de 10 etapas na
   1ª passagem e 7 de 10 na 2ª exigiram correção guiada ("Selecionar no navegador"). O Start apareceu como
   `NOT_FOUND` (`DESCRIPTOR_ONLY`) e também foi corrigido.
2. **Depois da correção, a reverificação volta em "Requer revisão"** ("Já havia uma decisão para este alvo, mas ela
   precisa de revisão"), com Continuar desabilitado. A decisão CONFIRMED (TEST_AGENT, `SELECTED_IN_BROWSER`) já fica
   registrada; o agente avança com "Próxima". O estado final do registro: 10 confirmadas, 0 rejeitadas, 0 indecisas.
3. **Marcadores do PDD.** Nas capturas, só 1 de 2 e 1 de 8 marcadores puderam ser posicionados (alvo não comprovado
   depois da recarga); o PDF diz "sem marcador na imagem" nessas instruções. A etapa de Conclusão não tem imagem.
4. **Heurística de localizador preferido** pode aceitar um `id` aleatório como preferido (visto em gravações
   anteriores; 0 casos na homologação final). A homologação verifica e corrige no MODELO (`choosePreferredLocator` →
   LABEL) quando acontece.
5. **Resultado por padrão de texto.** O placar não tem identidade exportável; é lido pelo único elemento cujo texto
   casa um padrão ancorado declarado. Mudança de redação no site exige mudar a configuração de geração.
6. **Origem declarada no manifesto só na construção de teste.** Em uso real a origem é concedida pelo controle
   "Acesso ao site" do Chrome.
7. **Diálogo do gerador sem teste headless.** Os testes do app rodam headless; o diálogo só é exercitado pela
   homologação com Robot (foi ali que o defeito 0×0 apareceu).
8. **Regeneração final sem janela.** O código executado na última tentativa real foi regenerado pelo mesmo gerador
   (`CodegenPresenter.generate`) a partir do `.nekkora` salvo, depois da correção de estilo do gerador; a geração pela
   GUI foi comprovada antes, com o gerador anterior (diff anexado: imports e quebra de linha).
9. **Laboratório depois do site real** (desvio de ordem do G6, ver `decisions.md`).
10. **Navegadores diferentes por papel.** Gravação, PDD e debug usam Chrome for Testing com a extensão; a execução do
    código gerado usa o Chrome instalado via Playwright, sem a extensão (ela não participa da execução).
11. **Vocabulário é global.** "excel" e "cloud" passam a ser texto de interface em qualquer página.
12. **Uma aba, sem iframe/shadow DOM** — o desafio não exige; não foi exercitado aqui.
13. **Framework:** 3 arquivos já estavam fora do `ruff format --check` na `main` (blocos de código em `README.md`,
    `docs/contracts.md`, `docs/extending.md`); não foram reformatados para não misturar mudanças.
