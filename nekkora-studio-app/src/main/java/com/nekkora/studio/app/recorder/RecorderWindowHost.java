package com.nekkora.studio.app.recorder;

/** Onde o Floating Recorder é mostrado (janela real em Swing; registro de visibilidade nos testes sem tela). */
public interface RecorderWindowHost {

    void show();

    void hide();

    boolean isShowing();

    void setAlwaysOnTop(boolean onTop);

    boolean isAlwaysOnTop();

    /** O conteúdo mudou de tamanho (reajusta a janela). */
    void contentChanged();

    void dispose();

    /** Sem janela: apenas registra a visibilidade pedida. */
    static RecorderWindowHost headless() {
        return new RecorderWindowHost() {
            private boolean showing;
            private boolean onTop = true;

            @Override
            public void show() {
                showing = true;
            }

            @Override
            public void hide() {
                showing = false;
            }

            @Override
            public boolean isShowing() {
                return showing;
            }

            @Override
            public void setAlwaysOnTop(boolean value) {
                onTop = value;
            }

            @Override
            public boolean isAlwaysOnTop() {
                return onTop;
            }

            @Override
            public void contentChanged() {
            }

            @Override
            public void dispose() {
                showing = false;
            }
        };
    }
}
