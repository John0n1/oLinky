# oLinky

oLinky is a modern, open-source reboot of the classic DriveDroid concept: it lets a rooted Android device masquerade as a bootable USB drive or PXE host so you can start PCs from ISO/IMG images on the go. The project focuses on a seamless, user-friendly experience while exposing powerful tooling for power users.

## Project Goals
- Mount ISO/IMG files as USB mass storage gadgets with optional read/write overlays.
- Expose lightweight PXE (DHCP/TFTP/HTTP) services over USB Ethernet (RNDIS/ECM) for network boot scenarios.
- Offer a polished Jetpack Compose UI for managing images, mounts, and automation profiles.
- Maintain strong safety practices around root access, SELinux, and background services.

## Architecture & Requirements
See [`docs/architecture.md`](docs/architecture.md) for the current high-level design, feature roadmap, and security considerations.

## Status
🚧 Pre-alpha planning. Core architecture and project scaffolding are underway. Community feedback and device compatibility reports are welcome.