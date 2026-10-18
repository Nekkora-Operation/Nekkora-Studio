# NEKKORA-E2E-001 — Segurança e privacidade

## 1. Origem: benchmark ≠ LAB

- `https://rpachallenge.com` é tratado como **ambiente público de treinamento autorizado pela usuária** (texto da
  missão), nunca como LAB. Nenhuma política LAB foi relaxada: `lab` continua aceitando só loopback.
- Framework: ambiente `benchmark` — lista FECHADA de origens `https` exatas (sem curinga, sem caminho, sem loopback);
  as mesmas guardas de rede do LAB (rota + proxy sumidouro, redirecionamento externo bloqueado, POST de tentativa
  única). Recursos de terceiros do site (fontes Google, métrica Cloudflare) ficam **bloqueados**: no G1, 3 requisições
  bloqueadas, 0 navegações bloqueadas.
- Extensão: a aba do desafio é `AUTHORIZED` pela permissão do próprio Chrome para a origem exata. Na construção de
  TESTE da homologação a origem exata é declarada no manifesto (como `TEST_GRANTED_ORIGINS` das ITs); a extensão
  publicada não muda de permissões.
- Captura visual do PDD: perfil `BENCHMARK` explícito (`visual-capture-profile.js`, `BENCHMARK_ORIGINS =
  ['https://rpachallenge.com']`), aditivo e só para a origem exata; a política `nekkora-visual/1` e a recusa fora do
  perfil (`VISUAL_CAPTURE_NOT_ALLOWED`) continuam valendo.
- Chrome da homologação: `--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE rpachallenge.com, EXCLUDE localhost,
  EXCLUDE 127.0.0.1` — nenhum outro host resolve.

## 2. Vocabulário de interface (revisão versionada)

`contracts/privacy/ui-vocabulary.json` ganhou **"excel"** e **"cloud"** (1062 → 1064 termos), com aprovação explícita
da usuária e registro no histórico do contrato. Motivo: o nome acessível do link de download é
"Download Excel cloud_download" (o ícone Material Icons é texto); sem os dois termos o nome era retido e o link só
tinha localizador POSICIONAL, que o gerador recusa. A regra não mudou (todo termo precisa estar na lista; nenhuma
supressão parcial; nenhuma exceção por origem). A cópia do Studio (`nekkora-studio-domain`) foi sincronizada. Vetores
novos: passa "Download Excel cloud_download"; recusa "Excel Quixadá". O `href` continua saindo só como presença.

## 3. Dados da planilha

- A planilha baixada (34 124 bytes, sha256 `cf1a8c04…1c04d37`, 1 aba, 10 registros) fica fora dos repositórios.
- Para DIGITAR na gravação (G2) a homologação lê o 1º registro de um arquivo transitório fora dos repositórios
  (`transiente.json`), apagado ao final da missão. Os valores digitados nunca entram no modelo: as etapas FILL ficam
  ligadas a variáveis de entrada.
- Varreduras (palavra inteira, valores com ≥ 3 caracteres, os 65 valores distintos da planilha):
  evidências versionadas, código gerado, `.nekkora`, log da homologação, eventos JSONL, checkpoint, resumo,
  observações — **0 ocorrências**. Nos arquivos alterados das três branches, a única ocorrência é um cargo genérico
  nos dados SINTÉTICOS da réplica do laboratório (preexistente, não é dado pessoal).
- O PDF e as duas imagens do PDD foram inspecionados: só rótulos de interface e campos vazios.

## 4. O que NÃO entra em evidência

Nenhuma captura bruta (PNG) do Studio ou do Chrome é versionada: só as duas imagens JÁ sanitizadas pelo PDD do Studio.
Caminhos locais foram trocados por `<trabalho>`, `<temp>` e `<repos>`. Nenhuma credencial é usada (o processo não
declara segredos).

## 5. Execução real

- O código gerado roda com `environment = "benchmark"` e `allowed_origins = ["https://rpachallenge.com"]` declarados na
  configuração gerada; não há chave para desligar as guardas.
- O Start é acionado uma vez por tentativa (navegador novo = tentativa nova). Submit só conclui com prova de avanço
  (o nó marcado sai do documento); desfecho desconhecido nunca é repetido (política do framework, M05/M06/M13).
- O texto do resultado é lido por padrão ANCORADO declarado na configuração de geração e gravado só em
  `observacoes.json`; nunca vai para eventos.
