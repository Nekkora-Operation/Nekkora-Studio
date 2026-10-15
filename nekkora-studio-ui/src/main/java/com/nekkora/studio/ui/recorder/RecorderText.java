package com.nekkora.studio.ui.recorder;

import com.nekkora.studio.bridge.LinkStatus;
import com.nekkora.studio.recorder.RecorderSnapshot;
import com.nekkora.studio.recorder.RecorderState;
import com.nekkora.studio.recorder.RecordingProgress;
import com.nekkora.studio.recorder.RecordingSummary;
import com.nekkora.studio.recorder.StepProgress;
import com.nekkora.ui.core.Status;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Textos do Recorder para o usuário (sem identificadores técnicos; estados sempre também em texto). */
public final class RecorderText {

    /** Gravação apenas técnica (extensão sem a gravação de ações): a página é acompanhada, nenhuma etapa é criada. */
    public static final String ACTION_CAPTURE_NOTICE = "Captura de ações indisponível nesta gravação: a extensão conectada não registra ações. A página é acompanhada, mas nenhuma etapa é criada.";
    /** O que cada modo faz (STUDIO-003). */
    public static final String MODE_NOTICE = "Capturar: registra o elemento sem executar a ação. Capturar e interagir: registra e deixa a página funcionar normalmente.";
    /** Antes do início: o que a gravação faz. */
    public static final String SETUP_NOTICE = "Cada interação no Chrome vira uma etapa na sessão escolhida, depois que o Studio a registra. Valores digitados não são guardados.";
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

    /** Rótulo do estado no Recorder: "Gravando" somente com o estado RECORDING confirmado e a captura de ações ativa. */
    public static String stateLabel(RecorderSnapshot s) {
        return s.state() == RecorderState.RECORDING && s.steps().enabled() ? CONFIRMED_RECORDING_LABEL : s.state().label();
    }

    private static final String CONFIRMED_RECORDING_LABEL = "Gravando";

    /** Etapas registradas (aceitas pelo domínio do Studio). */
    public static String steps(StepProgress p) {
        if (!p.enabled()) {
            return "Nenhuma (gravação técnica)";
        }
        String out = p.accepted() + (p.accepted() == 1 ? " registrada" : " registradas");
        if (p.pending() > 0) {
            out += " · " + p.pending() + " aguardando o Studio";
        }
        return out;
    }

    /** Aviso sobre a entrega das etapas (falhas nunca ficam em silêncio). */
    public static String stepsNotice(StepProgress p) {
        if (!p.enabled()) {
            return ACTION_CAPTURE_NOTICE;
        }
        List<String> parts = new ArrayList<>();
        if (p.rejected() > 0) {
            parts.add(p.rejected() + (p.rejected() == 1 ? " etapa não pôde ser registrada pelo Studio." : " etapas não puderam ser registradas pelo Studio."));
        }
        if (p.lost() > 0) {
            parts.add(p.lost() + (p.lost() == 1 ? " interação foi perdida" : " interações foram perdidas") + " porque a página mudou antes de serem concluídas.");
        }
        if (p.waiting()) {
            parts.add("Há etapas emitidas pelo navegador que ainda não chegaram: reenvio solicitado.");
        }
        return parts.isEmpty() ? "Cada interação vira uma etapa depois que o Studio a registra. Valores digitados não são guardados." : String.join(" ", parts);
    }

    /** Linhas do resumo (as etapas só são citadas quando foram de fato registradas pelo Studio). */
    public static List<String> summary(RecordingSummary sum) {
        List<String> out = new ArrayList<>();
        RecordingProgress p = sum.progress();
        out.add("Duração: " + duration(sum.duration()));
        out.add("Capturas técnicas: " + p.technicalCaptures() + " · Sincronizações: " + p.synchronizations() + " · Recuperações: " + p.recoveries());
        out.add("Cobertura: " + coverage(p));
        if (sum.limitations().contains("INTERRUPTED")) {
            out.add("A captura foi interrompida durante a gravação.");
        }
        if (sum.actionsCaptured()) {
            out.add("Etapas registradas: " + sum.stepsAccepted() + (sum.stepsRejected() > 0 ? " · não registradas: " + sum.stepsRejected() : "")
                    + (sum.stepsLost() > 0 ? " · interações perdidas: " + sum.stepsLost() : ""));
            out.add("PARTIAL".equals(sum.actionCapture()) ? "Captura de ações parcial: veja as limitações acima." : "Captura de ações completa.");
            if (sum.limitations().contains("STEPS_UNCONFIRMED")) {
                out.add("Algumas etapas não foram confirmadas a tempo.");
            }
        } else {
            out.add("Nenhuma etapa foi criada: a extensão conectada não registra ações.");
        }
        out.add(sum.pageMapExported() ? "PageMap exportado." : "O PageMap não foi exportado (finalizar não é exportar).");
        return out;
    }

    static String duration(Duration d) {
        long s = Math.max(0, d.toSeconds());
        return s < 60 ? s + " s" : (s / 60) + " min " + (s % 60) + " s";
    }
}
