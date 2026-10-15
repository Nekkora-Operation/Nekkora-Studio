# STUDIO-004 — Comentários nas etapas

## Funcionalidades

Na aba **Comentários** do editor:

- **Adicionar:** escreva e clique em "Adicionar comentário". Ele entra no final da lista.
- **Editar:** "Editar" carrega o texto no campo; "Salvar comentário" atualiza.
- **Excluir:** "Excluir".
- A lista mostra a ordem e as datas: "Criado em 15/10/2026 12:00", com "· editado em …" quando foi editado.

Tudo vale ao clicar em **Salvar alterações** do editor, que aplica uma operação com uma revisão. O painel de
propriedades mostra os comentários no grupo **Comentários**.

Também há operações diretas no domínio e no caso de uso (`addComment`, `editComment`, `removeComment`), usadas pelos
testes e disponíveis para futuras telas.

## Regras (`StepComment.requireText`)

- texto obrigatório, até 1000 caracteres;
- quebras de linha permitidas; outros caracteres de controle recusados;
- nada com aparência de dado sensível (e-mail, CPF, CNPJ, cartão, JWT, Bearer): "Comentários não guardam segredos";
- a data de criação é preservada nas edições, e `editedAt` registra a última edição;
- **sem autor**: o Studio não tem login e não presume identidade.

Os comentários pertencem à etapa (`StepId`) e a acompanham em movimentações entre sessões. Duplicar uma etapa não copia
os comentários.

## Evidência

- Domínio: `AutomationEditingTest.comentarios` e `moverEntreSessoes`.
- Aplicação: `EditorStudioTest.comentarios`, inclusive a recusa de um CPF no próprio campo.
- Homologação real, cenário 6: comentário criado, editado (data preservada) e mantido depois de mover a etapa com
  Alt+↑.
