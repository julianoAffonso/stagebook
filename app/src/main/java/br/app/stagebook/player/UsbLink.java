package br.app.stagebook.player;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

// Opens the output of a class 2 USB audio interface (any with an isochronous output) and takes it away from Android's
// control.
final class UsbLink {

    UsbDevice dev;
    UsbDeviceConnection conn;
    UsbInterface streaming;   // alt with the isochronous endpoint
    UsbInterface idle;        // alt 0 (banda zero)
    int ep, maxPacket, interval, channels, subslot, bitRes, clockId = -1;
    int rate = 48000;   // the output's rate: USB Audio 2.0 is set to 48 kHz, 1.0 says what it has
    boolean classOne;   // USB Audio 1.0: its format, rate and control are written differently
    private int outIf = -1, outAlt = -1;
    int outIfNum, outAltNum;
    // the interface's input (recording): its pace is the interface clock (implicit feedback)
    int inEp = -1, inMaxPacket, inChannels, inSubslot, inIfNum = -1, inAltNum;
    UsbInterface inStreaming, inIdle;
    final java.util.List<UsbInterface> others = new java.util.ArrayList<>();

    static UsbDevice find(UsbManager um) {
        for (UsbDevice d : um.getDeviceList().values()) {
            if (isAudioOut(d)) return d;
        }
        return null;
    }

    // USB audio interface, class 1 or 2, with an isochronous output endpoint: the formats the engine speaks.
    static boolean isAudioOut(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface x = d.getInterface(i);
            if (x.getInterfaceClass() != 1 || x.getInterfaceSubclass() != 2) continue;
            for (int e = 0; e < x.getEndpointCount(); e++) {
                android.hardware.usb.UsbEndpoint ep = x.getEndpoint(e);
                if (ep.getType() == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_ISOC
                        && ep.getDirection() == android.hardware.usb.UsbConstants.USB_DIR_OUT) return true;
            }
        }
        return false;
    }

    // Name for the screen: the manufacturer's, or a generic one.
    String name() {
        String n = dev != null ? dev.getProductName() : null;
        return n != null && !n.trim().isEmpty() ? n.trim() : "USB";
    }

    // Returns null on success, or the error message.
    String open(UsbManager um, UsbDevice d) {
        dev = d;
        conn = um.openDevice(d);
        if (conn == null) return "couldn't open the USB interface";
        byte[] raw = conn.getRawDescriptors();
        // some interfaces (or a cable dropping right then) give no descriptors: an error, not a crash (audio audit P1)
        if (raw == null) return "the USB interface gave no descriptors";

        scan(raw);
        if (ep < 0 || channels == 0) return "couldn't find the USB interface audio output";

        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface x = d.getInterface(i);
            if (x.getId() == inIfNum && x.getAlternateSetting() == inAltNum) inStreaming = x;
            if (x.getId() == inIfNum && x.getAlternateSetting() == 0) inIdle = x;
            if (x.getId() != outIf) continue;
            if (x.getAlternateSetting() == outAlt) streaming = x;
            if (x.getAlternateSetting() == 0) idle = x;
        }
        if (streaming == null) return "streaming interface not found";
        // Claims ALL audio interfaces: if Android keeps the USB sound card, its driver touches the same interface when
        // another app plays and drops the stream.
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface x = d.getInterface(i);
            if (x.getInterfaceClass() != 1 || x.getAlternateSetting() != 0 || x.getId() == outIf) continue;
            if (conn.claimInterface(x, true)) others.add(x);
        }
        if (!conn.claimInterface(streaming, true)) return "couldn't claim the USB interface (another app using it?)";
        outIfNum = outIf;
        outAltNum = outAlt;
        if (!conn.setInterface(streaming)) return "a interface USB recusou o modo de streaming";
        // without the input there's no feedback: the engine falls back to the nominal rate (the tablet's 48 kHz)
        if (inStreaming == null || inChannels == 0 || !conn.setInterface(inStreaming)) inEp = -1;
        if (classOne) {
            byte[] hz = {(byte) rate, (byte) (rate >> 8), (byte) (rate >> 16)};   // on the endpoint; a single rate may refuse
            conn.controlTransfer(0x22, 0x01, 0x0100, ep, hz, 3, 1000);
        } else if (clockId >= 0) {
            byte[] hz = {(byte) 0x80, (byte) 0xBB, 0, 0}; // 48000 Hz; fixed-rate interfaces may refuse
            conn.controlTransfer(0x21, 0x01, 0x0100, clockId << 8, hz, 4, 1000);
        }
        return null;
    }

    // Reads the output and input out of the descriptors. Class 1 keeps channels, size and rates in one format block.
    void scan(byte[] raw) {
        int curIf = -1, curAlt = -1, curSub = -1;
        int candCh = 0, candSub = 0, candRes = 0, candRate = 0;
        boolean adaptive = false;
        ep = -1;
        for (int p = 0; p + 1 < raw.length; ) {
            int len = raw[p] & 0xff, type = raw[p + 1] & 0xff;
            if (len < 2) break;
            if (type == 4 && len >= 9) {
                curIf = raw[p + 2] & 0xff; curAlt = raw[p + 3] & 0xff; curSub = raw[p + 6] & 0xff;
                candCh = 0; candSub = 0; candRes = 0; candRate = 0;
            } else if (type == 0x24 && len >= 4) {
                int st = raw[p + 2] & 0xff;
                if (curSub == 1 && st == 0x0A) clockId = raw[p + 3] & 0xff;
                if (curSub == 2 && st == 0x01 && len >= 11) candCh = raw[p + 10] & 0xff;
                if (curSub == 2 && st == 0x02 && len >= 11) {
                    candCh = raw[p + 4] & 0xff; candSub = raw[p + 5] & 0xff; candRes = raw[p + 6] & 0xff;
                    candRate = bestRate(raw, p, len);
                } else if (curSub == 2 && st == 0x02 && len >= 6) {
                    candSub = raw[p + 4] & 0xff; candRes = raw[p + 5] & 0xff;
                }
            } else if (type == 5 && len >= 7 && curSub == 2) {
                int addr = raw[p + 2] & 0xff, attr = raw[p + 3] & 0xff;
                // isochronous data input (not an explicit-feedback-only endpoint)
                if ((addr & 0x80) != 0 && (attr & 3) == 1 && (attr & 0x30) != 0x10 && inEp < 0) {
                    inEp = addr; inIfNum = curIf; inAltNum = curAlt;
                    inMaxPacket = (raw[p + 4] & 0xff) | ((raw[p + 5] & 0x07) << 8);
                    inChannels = candCh; inSubslot = candSub;
                }
                if ((addr & 0x80) == 0 && (attr & 3) == 1 && ep < 0) {
                    ep = addr; outIf = curIf; outAlt = curAlt;
                    maxPacket = (raw[p + 4] & 0xff) | ((raw[p + 5] & 0x07) << 8);
                    interval = raw[p + 6] & 0xff;
                    channels = candCh; subslot = candSub; bitRes = candRes;
                    if (candRate > 0) { rate = candRate; classOne = true; }
                    adaptive = (attr & 0x0C) == 0x08;
                }
            }
            p += len;
        }
        // an adaptive output follows our pace: its input would only cost a second stream
        if (classOne && adaptive) { inEp = -1; inIfNum = -1; }
    }

    // 48 kHz if the card has it, as the engine's sound was tuned there; otherwise its highest up to 96 kHz.
    private static int bestRate(byte[] raw, int p, int len) {
        int n = raw[p + 7] & 0xff, best = 0;
        if (n == 0 && len >= 14) {
            int lo = hz(raw, p + 8), hi = hz(raw, p + 11);
            return lo <= 48000 && 48000 <= hi ? 48000 : Math.min(hi, 96000);
        }
        for (int k = 0; k < n && p + 10 + 3 * k < p + len; k++) {
            int r = hz(raw, p + 8 + 3 * k);
            if (r == 48000) return r;
            if (r <= 96000 && r > best) best = r;
        }
        return best;
    }

    private static int hz(byte[] raw, int at) {
        return (raw[at] & 0xff) | ((raw[at + 1] & 0xff) << 8) | ((raw[at + 2] & 0xff) << 16);
    }

    String describe() {
        return "ep=0x" + Integer.toHexString(ep) + " if" + outIfNum + "/alt" + outAltNum
                + " +" + others.size() + " ifs " + channels + "ch subslot=" + subslot
                + (inEp >= 0 ? " fb=0x" + Integer.toHexString(inEp) + "/" + inChannels + "ch" : " no-fb")
                + " bits=" + bitRes + " rate=" + rate + " maxPkt=" + maxPacket;
    }

    void close() {
        if (conn == null) return;
        if (idle != null) conn.setInterface(idle);
        if (inIdle != null) conn.setInterface(inIdle);
        if (streaming != null) conn.releaseInterface(streaming);
        for (UsbInterface x : others) conn.releaseInterface(x);
        others.clear();
        conn.close();
        conn = null;
    }
}
