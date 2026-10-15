# STUDIO-004 — Edição da automação

## Como usar

- Selecione a automação ("Propriedades da automação" no cabeçalho ou a própria aba) e use **F2** ou **Editar
  propriedades** no painel.
- O diálogo **Propriedades da automação** traz o nome e a descrição atuais. **Salvar alterações** aplica as duas
  coisas numa única operação.

## Regras (domínio e caso de uso)

| Regra | Onde |
|---|---|
| Nome obrigatório, até 120 caracteres, sem quebras de linha ou caracteres de controle | `Texts.requireName` |
| Descrição opcional, até 2000 caracteres | `Texts.optionalDescription` |
| Nome único no catálogo (sem diferenciar maiúsculas e espaços nas pontas) | `AutomationCatalog.updateDetails` |
| Nenhuma alteração parcial: se o nome é recusado, a descrição também não muda | validação antes de alterar |
| A identidade (`AutomationId`) não muda; renomear não cria outra automação | `Automation.updateDetails` |
| Sem diferença, sem revisão nem entrada no histórico | `updateDetails` sem efeito |

Ao renomear, a navegação e o título da aba acompanham o nome (`StudioView.refreshTabTitles`), e as sessões, as etapas
e a seleção continuam como estão. Nome duplicado aparece no próprio campo ("Já existe uma automação com este nome."), e
o diálogo continua aberto.

## Evidência

- Domínio: `AutomationEditingTest.automacao` (S004-001/002/003).
- Aplicação: `EditorStudioTest.automacao`, que passa pelo F2, pela aba renomeada e pelo duplicado mostrado no campo.
- Homologação real, cenário 1.
