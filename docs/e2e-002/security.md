# NEKKORA-E2E-002 — Segurança e privacidade

## O que nunca sai da página nem entra no projeto
- Texto do artigo: "Selecionar dado" envia só o elemento (tipo, localizadores, caminho de acesso) e a ÂNCORA da linha
  quando o rótulo é 100% vocabulário de interface (D-E2E2-01). Verificado no IT `E2e002SelectDataIT` (nenhum texto da
  ficha no descritor) e na homologação (nenhum texto dos artigos no modelo, no PDD, no PDF nem no código gerado).
- Valor digitado na busca: o modelo guarda só a etapa Preencher ligada à variável `termo_de_busca`; o valor vem da lista
  de entrada na execução.

## IA (Anthropic real)
- Chave lida só do ambiente do processo do Studio (`NEKKORA_ANTHROPIC_API_KEY`/`..._WORKSPACE_ID`); nunca impressa,
  gravada ou enviada no relatório. O lançador da homologação mascara a saída e confere que o log não contém a chave.
- Consentimento com prévia do que VAI e do que NÃO VAI; a carga é minimizada (sem URLs, localizadores, valores,
  nomes de variáveis ou credenciais). A prévia foi conferida sem texto de artigo e sem chave.
- Evidência da chamada (`ia-chamadas.jsonl`): provedor, modelo, contrato, Prompt-Mestre, tamanho e hash da carga,
  propostas aceitas/recusadas, tokens e duração — nunca a carga, a resposta ou a chave.
- Proposta da IA é só proposta: "Revisar e aplicar" registra a decisão com o ator; o verificador recusa o que foge ao
  contrato. Conteúdo da página nunca vira instrução (a IA não recebe texto da página).

## Execução supervisionada
- Botão separado ("Executar teste autorizado"), disponível só com PDD_READY, código CODE_VALIDATED da revisão atual,
  manifesto conferido por hash (código editado à mão é recusado), framework na versão do catálogo e origem autorizada.
- Confirmação mostra o site, o arquivo de saída exato (e se será substituído), a lista e a contagem, e que não é produção.
- Subprocesso Python isolado (`-E -s -B -X utf8`, ambiente limpo, fora da EDT), sem shell, sem chave em argumentos,
  com prazo e Cancelar (fecha o navegador; a planilha não é gravada).
- O framework só acessa as origens autorizadas (redirecionamento para fora é bloqueado).
- "Validar automação" continua sem efeitos: V6 só fica ✓ com o registro do teste autorizado da MESMA revisão.

## Dados de saída
- A planilha fica só no destino escolhido (local); não é commitada.
- Pasta da execução: eventos e resumo sem valores; o estado da planilha guarda as linhas preparadas só até o commit
  (depois, hash, contagem e transações — framework 2f36a0a); proveniência separada (coluna → etapa → localizador, com as
  URLs públicas da lista de entrada como chave).
- Evidências commitadas foram varridas contra os valores dos artigos (sentinelas tiradas do oráculo).
