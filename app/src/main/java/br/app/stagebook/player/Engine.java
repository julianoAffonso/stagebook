package br.app.stagebook.player;

// JNI bridge to the native engine (engine.c).
final class Engine {
    static { System.loadLibrary("stagebook_audio"); }

    static native boolean start(int fd, int ep, int maxPacket, int channels, int subslot, int intf, int alt,
                                int inEp, int inMaxPacket, int inFrameBytes);
    static native void stop();
    // no USB interface: plays on the device's output (AAudio, 48 kHz stereo); false if Android didn't open it
    static native boolean startDevice();
    static native long load(String path);
    static native void play();
    static native void pause();
    static native void seek(long ms);
    static native long position();
    // backing equalizer: 5 bands (80, 250, 1k, 3.5k, 10k Hz), gains in dB
    static native void setEq(boolean on, float[] gainsDb);
    // {backing lufs (1/2), backing peak, click peak (3/4)} or null
    static native float[] analyze(String path);
    // the mix (per channel only): gains/norms per source channel (8), eqMask = channels with EQ (each separate),
    // route[output * 8 + channel]; without calling, each channel goes to the output with the same number at gain 1
    static native void setMix(float[] gains, float[] norms, int eqMask, float[] route);
    // source channels of the loaded track (up to 8)
    static native int lanes();
    // metronome click into USB outputs: normal/accent 48 kHz, samples per beat (0 = off), up to two outputs (-1 = none)
    static native void setClick(float[] normal, float[] accent, long samplesPerBeat, int out1, int out2);
    // {lufs, peak} of the channels in mask (each mixer channel or pair)
    static native float[] analyzeMask(String path, int mask);
    static native boolean isRunning();
    static native String status();
    // interface clock through the input: 1 locked, 0 waiting, -1 no input
    static native int feedback();
    // live meters (they only read the signal): when off, the engine does the same as before
    static native void setMeter(boolean on);
    // linear peaks since the previous read: {channel 1, 2, 3, 4, backing bands 80, 250, 1k, 3.5k, 10k}
    static native float[] peaks();
    // peaks of each file channel (after fader, normalization and EQ) since the last read: mixer meters
    static native float[] lanePeaks();

    private Engine() {}
}
