package com.nekkora.studio.app.ui;

import com.nekkora.studio.domain.ValidationProblem;
import com.nekkora.studio.ui.form.FormSpec;
import com.nekkora.studio.ui.form.StudioDialogs;
import com.nekkora.ui.file.NekkoraFileDialogs;
import java.awt.Component;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Diálogos reais (NekkoraDialog) ancorados na janela do Studio. */
public final class SwingStudioPrompts implements StudioPrompts {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy — HH:mm");
    private final Supplier<Component> parent;

    public SwingStudioPrompts(Supplier<Component> parent) {
        this.parent = parent;
    }

    @Override
    public <V> boolean showForm(FormSpec<V> spec, Function<V, Optional<ValidationProblem>> submit) {
        return StudioDialogs.showForm(parent.get(), spec, submit);
    }

    @Override
    public boolean confirm(Confirmation c) {
        return c.danger() ? com.nekkora.ui.dialog.NekkoraConfirmationDialog.confirmDanger(parent.get(), c.title(), c.message(), c.confirmLabel())
                : com.nekkora.ui.dialog.NekkoraConfirmationDialog.confirm(parent.get(), c.title(), c.message(), c.confirmLabel());
    }

    @Override
    public Optional<Path> chooseProjectToOpen(Path dir) {
        return NekkoraFileDialogs.openFile(parent.get(), "Abrir projeto", "Projeto do Nekkora Studio (.nekkora)", "nekkora");
    }

    @Override
    public Optional<Path> chooseSaveLocation(String suggestedName, Path dir) {
        String suggested = dir == null ? suggestedName : dir.resolve(suggestedName).toString();
        return NekkoraFileDialogs.saveFile(parent.get(), "Salvar projeto como", suggested, "nekkora");
    }

    @Override
    public UnsavedChoice confirmUnsaved(List<String> projects, boolean exit) {
        String list = projects.size() == 1 ? projects.get(0) : String.join("\n", projects.stream().map(p -> "• " + p).toList());
        String message = exit ? "Existem projetos com alterações não salvas:\n" + list
                + "\n\nOs projetos são arquivos locais não criptografados; não guarde segredos neles."
                : "O projeto " + list + " tem alterações não salvas.";
        int c = StudioDialogs.choose(parent.get(), exit ? "Sair do Nekkora Studio?" : "Fechar projeto?", message, true, List.of(
                new StudioDialogs.Choice("Descartar alterações", StudioDialogs.Choice.Kind.DANGER),
                new StudioDialogs.Choice(exit ? "Salvar todos" : "Salvar", StudioDialogs.Choice.Kind.PRIMARY)));
        return c == 0 ? UnsavedChoice.DISCARD : c == 1 ? UnsavedChoice.SAVE : UnsavedChoice.CANCEL;
    }

    @Override
    public ConflictChoice resolveConflict(String project) {
        int c = StudioDialogs.choose(parent.get(), "Arquivo alterado fora do Studio", "O arquivo do projeto " + project
                + " foi alterado (ou removido) fora do Nekkora Studio desde a última leitura ou gravação. Para não perder nenhuma das versões, "
                + "ele não foi sobrescrito.", true, List.of(
                new StudioDialogs.Choice("Recarregar do arquivo", StudioDialogs.Choice.Kind.DANGER),
                new StudioDialogs.Choice("Salvar com outro nome", StudioDialogs.Choice.Kind.PRIMARY)));
        return c == 0 ? ConflictChoice.RELOAD : c == 1 ? ConflictChoice.SAVE_AS : ConflictChoice.CANCEL;
    }

    @Override
    public RecoveryChoice offerRecovery(RecoveryOffer o) {
        String message = "Encontramos alterações não salvas.\n\nProjeto:\n" + o.project() + "\n\nÚltima recuperação:\n"
                + WHEN.format(o.at().atZone(ZoneId.systemDefault()))
                + (o.origin() == null ? "\n\nO projeto ainda não tinha sido salvo." : "\n\nArquivo do projeto: " + o.origin()
                        + "\nRecuperar não altera esse arquivo; salve depois para confirmar.");
        int c = StudioDialogs.choose(parent.get(), "Recuperação disponível", message, true, List.of(
                new StudioDialogs.Choice("Ignorar", StudioDialogs.Choice.Kind.SECONDARY),
                new StudioDialogs.Choice("Recuperar alterações", StudioDialogs.Choice.Kind.PRIMARY)));
        return c == 0 ? RecoveryChoice.IGNORE : c == 1 ? RecoveryChoice.RECOVER : RecoveryChoice.CANCEL;
    }

    @Override
    public Optional<RecoveryPreferences> editRecovery(RecoveryPreferences current, List<Integer> intervals, String explanation) {
        return StudioDialogs.recoverySettings(parent.get(), current.enabled(), current.intervalSeconds(), intervals, explanation)
                .map(d -> new RecoveryPreferences(d.enabled(), d.intervalSeconds(), d.clear()));
    }
}
