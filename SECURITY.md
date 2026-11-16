<p align="center"><img src="docs/logo.png" alt="Stagebook" width="64"></p>

# Security policy

**English** · [Português (Brasil)](SECURITY.pt-BR.md)

## Supported versions

Only the latest release on [GitHub Releases](https://github.com/julianoAffonso/stagebook/releases/latest) gets fixes. The app tells you when a new version is out.

## Reporting a vulnerability

Please don't open a public issue for a security problem.

Report it privately on GitHub: open the [Security tab](https://github.com/julianoAffonso/stagebook/security) and choose **Report a vulnerability**. Only the maintainer sees the report.

Tell us what you found, the app version, the device and Android version, and the steps to reproduce it. A proof of concept helps, but don't access other people's data or devices to make one.

## What happens next

- You get an answer within a week.
- Once confirmed, the fix goes out in a new release, and the release notes mention it.
- You're credited in the advisory, unless you'd rather stay anonymous.

## Scope

The Android app, the web editor it serves on the local network, the band sharing (local network, QR codes and internet links), including the PDFs that travel with a song (checked before they are kept: a real PDF header, at most 8 MB and 300 pages, no password, a name the app generates, and only from someone already in the band), the `.stagebook` files and backups, the MCP server in this repository, and the GitHub workflows.

How the web editor trusts a computer, so you can judge it: a browser asks to come in and the person allows it on the device, comparing a 4-digit code. The browser then keeps a random key in a cookie for 90 days, renewed on each visit, and an assistant connected by MCP keeps its own key in a file only that user can read. The device keeps only a hash of each key, and removing a computer under Settings → Edit on the computer ends its access at once. The key travels over plain HTTP on the local network (the editor only answers to an address, not to a name), so use it on a network you trust.

Out of scope: problems in Android itself, in a phone maker's software or in a USB audio interface's firmware.
