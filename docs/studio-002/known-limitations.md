# STUDIO-002 — Limitações conhecidas

## Por escopo

- **Captura de ações** (cliques, digitação, seleção) e conversão em etapas: STUDIO-003. A gravação técnica observa a
  página, mas não cria etapas, e a interface diz isso.
- **Modos de captura**: "Capturar" e "Capturar e interagir" são **configuração confirmada**. Os sensores que
  diferenciam o comportamento chegam na STUDIO-003.
- **Exportar PageMap** a partir do Studio: não há; finalizar não é exportar.
- Persistência de automações (STUDIO-006), login, licenciamento, IA, execução, PDD, SDD e PDF: fora do escopo.

## Técnicas

- **Mesmo usuário**: o segredo efêmero fica num arquivo restrito ao usuário. Um processo malicioso desse mesmo usuário
  consegue lê-lo. É o limite declarado do D-STUDIO-002-01.
- **Opt-in obrigatório na extensão**: o host é iniciado pelo Chrome. Para o Studio conversar com o navegador, o usuário
  liga "Conectar ao Nekkora Studio" no painel (uma vez; o opt-in fica gravado). Com ele ligado, a extensão mantém o host
  em execução enquanto o Chrome está aberto.
- **Um navegador por vez**: um Studio aceita uma conexão autenticada. Um segundo perfil do Chrome com o canal ligado
  recebe `BRIDGE_BUSY` e tenta de novo quando o primeiro sai.
- **Uma janela do Studio com gravação**: a segunda instância fica sem Bridge (OTHER_INSTANCE) e continua editando
  automações.
- **Retomar com o motor ocupado**: retomar sem outro consumidor exige nova captura-base, que depende do motor Java. Se
  houver uma exportação longa em andamento, a retomada espera ("Retomando…"). Pausar, finalizar, cancelar, renovar e
  consultar não esperam.
- **Encerramento da sessão de captura no motor**: ao pausar, finalizar ou cancelar com o Recorder como único
  consumidor, o encerramento local é imediato e o `capture.session.stop` no motor segue a fila dele. Por alguns
  instantes, com uma exportação longa em curso, o motor ainda guarda essa sessão.
- **Diálogos do Chrome**: o Floating Recorder fica acima das outras janelas por padrão. Se ele cobrir um diálogo do
  Chrome (ex.: pedido de permissão), desmarque "Manter acima das outras janelas" ou use "Recolher".
- **Homologação de autorização**: a concessão e a revogação foram feitas pelo controle "Acesso ao site" do Chrome,
  sobre uma cópia de homologação da extensão que declara o site de teste. O diálogo de permissão do Chrome não foi
  automatizado (regra da missão); ele foi homologado manualmente na REAL-001.
- **Sandbox do Chrome for Testing**: na homologação, o perfil temporário do CfT usou
  `--disable-features=NetworkServiceSandbox`, porque o sandbox do serviço de rede não acessava o executável em
  `.tools` e o serviço reiniciava, deixando a primeira navegação pendente. A configuração não afeta o produto.
- **Linux**: o Bridge usa o mesmo código, e os testes de servidor e cliente rodam no contêiner Linux. A homologação com
  janela e Chrome reais foi feita só no Windows. O `tools/golden-linux.sh verify` compila o Native Host da extensão
  ao lado. Por isso os testes cruzados com o host real (`RealNativeHostBridgeTest`) também rodam no Linux, pelo Unix
  Domain Socket. Sem a extensão ao lado, eles são ignorados, como já acontecia com o contrato do PageMap.
- **Leitores de tela**: nomes e descrições acessíveis foram verificados por teste; não houve sessão com NVDA ou JAWS.
