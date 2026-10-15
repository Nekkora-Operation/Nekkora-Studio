package com.nekkora.studio.ui.recorder;

import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.recorder.RecorderSnapshot;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingProgress;
import com.nekkora.studio.recorder.RecordingSummary;
import com.nekkora.ui.core.Status;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Textos do Recorder para o usuário (sem identificadores técnicos; estados sempre também em texto). */
public final class RecorderText {

    /** Aviso permanente desta versão: a gravação técnica não cria etapas. */
    public static final String ACTION_CAPTURE_NOTICE = "Captura de ações: próxima versão. Esta gravação acompanha a página, mas ainda não cria etapas.";
    /** Os modos ainda não mudam o comportamento da captura. */
    public static final String MODE_NOTICE = "Configuração para a próxima etapa: os sensores de interação chegam na STUDIO-003.";
    public static final String WAITING_LINK = "Aguardando conexão com a extensão";

    private RecorderText() {
    }

    public static Status status(RecorderState s) {
        return switch (s) {
            case IDLE -> Status.DISABLED;
            case RECORDING -> Status.RUNNING;
            case PAUSED -> Status.PAUSED;
            case DEGRADED -> Status.WARNING;
            case COMPLETED -> Status.SUCCESS;
            case CANCELLED -> Status.DISABLED;
            case ERROR -> Status.FAILED;
            default -> Status.PENDING;
        };
    }

    /** Estado da ligação com o navegador (barra de status e diálogo de gravação). */
    public static String link(LinkStatus l) {
        if (l == null) {
            return "Navegador: " + WAITING_LINK.toLowerCase(java.util.Locale.ROOT);
        }
        return switch (l.state()) {
            case STARTING -> "Navegador: preparando a conexão local…";
            case WAITING -> "Navegador: " + WAITING_LINK.toLowerCase(java.util.Locale.ROOT);
            case CONNECTED -> "Navegador: conectado" + (l.extension() != null && l.extension().version() != null
                    ? " (Nekkora Extension " + l.extension().version() + ")" : "");
            case UNAVAILABLE -> "Navegador: indisponível" + switch (String.valueOf(l.detail())) {
                case "OTHER_INSTANCE" -> " (outra janela do Studio controla a gravação)";
                case "INSECURE_DIRECTORY" -> " (não foi possível proteger a conexão local)";
                case "CLOSED" -> "";
                default -> " (conexão local não publicada)";
            };
        };
    }

    /** Orientação quando o navegador não está conectado. */
    public static String connectHint(LinkStatus l) {
        if (l != null && l.state() == LinkStatus.State.UNAVAILABLE) {
            return "OTHER_INSTANCE".equals(l.detail())
                    ? "Outra janela do Nekkora Studio já está conectada ao navegador. Use aquela janela para gravar."
                    : "A conexão local do Studio não pôde ser publicada com segurança. As automações continuam disponíveis; a gravação fica indisponível.";
        }
        return "Abra o Chrome e ligue “Conectar ao Nekkora Studio” no painel da Nekkora Extension. A conexão é local e autenticada.";
    }

    /** O que está acontecendo com a observação da página. */
    public static String capture(RecorderSnapshot s) {
        RecordingProgress p = s.progress();
        return switch (s.state()) {
            case RECORDING -> p.recorderAttached() ? "Recorder acompanhando a página" + (p.otherConsumers() > 0 ? " (junto com a inspeção manual)" : "")
                    : "Aguardando a captura da página";
            case PAUSED, PAUSING -> p.otherConsumers() > 0
                    ? "Recorder pausado. A inspeção manual continua observando a página."
                    : "Recorder pausado. A página não está sendo observada.";
            case DEGRADED -> s.linkLost() ? "Sem conexão com o navegador: estado da página desconhecido." : "A página não está sendo acompanhada.";
            case STARTING -> "Iniciando a captura técnica…";
            case COMPLETED, CANCELLED -> "Recorder liberado da captura.";
            default -> "—";
        };
    }

    public static String coverage(RecordingProgress p) {
        return switch (String.valueOf(p.coverage())) {
            case "COMPLETE" -> "Página acompanhada por completo";
            case "PARTIAL" -> "Parcial: " + p.framesUnavailable() + (p.framesUnavailable() == 1 ? " quadro (iframe) sem acesso" : " quadros (iframes) sem acesso");
            default -> "—";
        };
    }

    public static String reason(String reason) {
        return switch (String.valueOf(reason)) {
            case "TAB_CLOSED" -> "A aba foi fechada.";
            case "PERMISSION_REVOKED" -> "A autorização do site foi revogada.";
            case "ORIGIN_NOT_ALLOWED" -> "A aba saiu para uma página não autorizada.";
            case "LINK_LOST" -> "A conexão com o navegador foi perdida.";
            case "LEASE_EXPIRED" -> "A concessão de controle expirou.";
            default -> "";
        };
    }

    /** Linhas do resumo técnico (nunca afirma que etapas foram gravadas). */
    public static List<String> summary(RecordingSummary sum) {
        List<String> out = new ArrayList<>();
        RecordingProgress p = sum.progress();
        out.add("Duração: " + duration(sum.duration()));
        out.add("Capturas técnicas: " + p.technicalCaptures() + " · Sincronizações: " + p.synchronizations() + " · Recuperações: " + p.recoveries());
        out.add("Cobertura: " + coverage(p));
        if (sum.limitations().contains("INTERRUPTED")) {
            out.add("A captura foi interrompida durante a gravação.");
        }
        out.add("Nenhuma etapa foi criada: a captura de ações chega na próxima versão.");
        out.add(sum.pageMapExported() ? "PageMap exportado." : "O PageMap não foi exportado (finalizar não é exportar).");
        return out;
    }

    static String duration(Duration d) {
        long s = Math.max(0, d.toSeconds());
        return s < 60 ? s + " s" : (s / 60) + " min " + (s % 60) + " s";
    }
}
