package com.juliano.stagebook.player;

// Ponte JNI para o motor nativo (engine.c).
final class Engine {
    static { System.loadLibrary("stagebook_audio"); }

    static native boolean start(int fd, int ep, int maxPacket, int channels, int subslot, int intf, int alt,
                                int inEp, int inMaxPacket, int inFrameBytes);
    static native void stop();
    static native long load(String path);
    static native void play();
    static native void pause();
    static native void seek(long ms);
    static native long position();
    // equalizador do backing: 5 bandas (80, 250, 1k, 3,5k, 10k Hz), ganhos em dB
    static native void setEq(boolean on, float[] gainsDb);
    // {lufs do backing (1/2), pico do backing, pico do click (3/4)} ou null
    static native float[] analyze(String path);
    // a mistura (só por canal): gains/norms por canal da fonte (8), eqMask = canais com EQ (cada um separado),
    // route[saída * 8 + canal]; sem chamar, cada canal vai para a saída de mesmo número com ganho 1
    static native void setMix(float[] gains, float[] norms, int eqMask, float[] route);
    // canais da fonte da faixa carregada (até 8)
    static native int lanes();
    // {lufs, pico} dos canais em mask (cada canal ou par do mixer)
    static native float[] analyzeMask(String path, int mask);
    static native boolean isRunning();
    static native String status();
    // relógio da interface pela entrada: 1 travado, 0 esperando, -1 sem entrada
    static native int feedback();
    // medidores ao vivo (só leem o sinal): desligado, o motor faz o mesmo de antes
    static native void setMeter(boolean on);
    // picos lineares desde a leitura anterior: {canal 1, 2, 3, 4, faixas 80, 250, 1k, 3,5k, 10k do backing}
    static native float[] peaks();

    private Engine() {}
}
