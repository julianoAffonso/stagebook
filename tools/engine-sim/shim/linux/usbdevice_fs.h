// Simulator: only what engine.c uses from Linux's usbdevice_fs.h (same fields and types).
#pragma once
struct usbdevfs_iso_packet_desc { unsigned int length, actual_length, status; };
struct usbdevfs_urb {
    unsigned char type, endpoint;
    int status;
    unsigned int flags;
    void *buffer;
    int buffer_length, actual_length, start_frame;
    union { int number_of_packets; unsigned int stream_id; };
    int error_count;
    unsigned int signr;
    void *usercontext;
    struct usbdevfs_iso_packet_desc iso_frame_desc[];
};
struct usbdevfs_setinterface { unsigned int interface, altsetting; };
#define USBDEVFS_URB_TYPE_ISO 0
#define USBDEVFS_URB_ISO_ASAP 0x02
#define USBDEVFS_SUBMITURB 0x5501
#define USBDEVFS_REAPURB 0x5502
#define USBDEVFS_REAPURBNDELAY 0x5503
#define USBDEVFS_DISCARDURB 0x5504
#define USBDEVFS_SETINTERFACE 0x5505
#define USBDEVFS_GET_SPEED 0x5506
