package com.nekkora.studio.ui.form;

import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.tokens.Spacing;
import com.nekkora.ui.container.NekkoraStack;
import com.nekkora.ui.input.NekkoraFormField;
import javax.swing.JComponent;

/**
 * Base dos formulários do Studio: coleta valores e posiciona no campo certo as mensagens de validação
 * produzidas pelo <b>domínio</b> (o formulário não decide regras). Pode ser montado sem janela (testes).
 *
 * @param <V> valores coletados
 */
public abstract class StudioForm<V> extends NekkoraStack {

    protected StudioForm() {
        super(Axis.VERTICAL, Spacing.SPACE_6);
    }

    /** Valores atuais dos campos. */
    public abstract V values();

    /** Campo que recebe a mensagem de um problema (ou {@code null} para o campo principal). */
    protected abstract NekkoraFormField fieldFor(ValidationProblem.Field field);

    /** Campo principal (nome), usado quando o problema não tem campo próprio. */
    protected abstract NekkoraFormField mainField();

    /** Controle que recebe o foco ao abrir. */
    public abstract JComponent initialFocus();

    /** Campos que exibem mensagem de erro. */
    protected abstract NekkoraFormField[] fields();

    /** Mostra a mensagem do problema no campo correspondente. */
    public void showProblem(ValidationProblem problem) {
        NekkoraFormField f = fieldFor(problem.field());
        (f == null ? mainField() : f).setError(problem.message());
    }

    public void clearErrors() {
        for (NekkoraFormField f : fields()) if (f != null) f.setError(null);
    }

    /** Mensagem de erro exibida atualmente no campo principal (para testes e acessibilidade). */
    public String visibleErrors() {
        StringBuilder b = new StringBuilder();
        for (NekkoraFormField f : fields()) {
            if (f != null && f.hasError()) b.append(f.getControl().getAccessibleContext().getAccessibleDescription()).append('\n');
        }
        return b.toString().strip();
    }
}
