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

    // Class 2 USB audio interface (protocol 0x20) with an isochronous output endpoint: the format the engine speaks.
    static boolean isAudioOut(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface x = d.getInterface(i);
            if (x.getInterfaceClass() != 1 || x.getInterfaceSubclass() != 2 || x.getInterfaceProtocol() != 0x20) continue;
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

        int curIf = -1, curAlt = -1, curSub = -1, outIf = -1, outAlt = -1;
        int candCh = 0, candSub = 0, candRes = 0;
        ep = -1;
        for (int p = 0; p + 1 < raw.length; ) {
            int len = raw[p] & 0xff, type = raw[p + 1] & 0xff;
            if (len < 2) break;
            if (type == 4 && len >= 9) {
                curIf = raw[p + 2] & 0xff; curAlt = raw[p + 3] & 0xff; curSub = raw[p + 6] & 0xff;
                candCh = 0; candSub = 0; candRes = 0;
            } else if (type == 0x24 && len >= 4) {
                int st = raw[p + 2] & 0xff;
                if (curSub == 1 && st == 0x0A) clockId = raw[p + 3] & 0xff;
                if (curSub == 2 && st == 0x01 && len >= 11) candCh = raw[p + 10] & 0xff;
                if (curSub == 2 && st == 0x02 && len >= 6) { candSub = raw[p + 4] & 0xff; candRes = raw[p + 5] & 0xff; }
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
                }
            }
            p += len;
        }
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
        if (clockId >= 0) {
            byte[] rate = {(byte) 0x80, (byte) 0xBB, 0, 0}; // 48000 Hz; fixed-rate interfaces may refuse
            conn.controlTransfer(0x21, 0x01, 0x0100, clockId << 8, rate, 4, 1000);
        }
        return null;
    }

    String describe() {
        return "ep=0x" + Integer.toHexString(ep) + " if" + outIfNum + "/alt" + outAltNum
                + " +" + others.size() + " ifs " + channels + "ch subslot=" + subslot
                + (inEp >= 0 ? " fb=0x" + Integer.toHexString(inEp) + "/" + inChannels + "ch" : " no-fb")
                + " bits=" + bitRes + " maxPkt=" + maxPacket;
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
