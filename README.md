# oLinky

oLinky is a modern, open-source reboot of the classic DriveDroid concept: it lets a rooted Android device masquerade as a bootable USB drive or PXE host so you can start PCs from ISO/IMG images on the go. The project focuses on a seamless, user-friendly experience while exposing powerful tooling for power users.

## Project Goals
- Mount ISO/IMG files as USB mass storage gadgets with optional read/write overlays.
- Expose lightweight PXE (DHCP/TFTP/HTTP) services over USB Ethernet (RNDIS/ECM) for network boot scenarios.
- Offer a polished Jetpack Compose UI for managing images, mounts, and automation profiles.
- Maintain strong safety practices around root access, SELinux, and background services.

## Architecture & Requirements
See [`docs/architecture.md`](docs/architecture.md) for the current high-level design, feature roadmap, and security considerations.

## Prerequisites
- A rooted Android device running Android 10 or newer with Magisk/SuperSU providing an interactive `su` binary.
- `adb` installed on your workstation (Android SDK Platform Tools 35.x or newer recommended).
- Java 17+ (we test with OpenJDK 21) and Gradle wrapper (bundled) available on your workstation.
- Optional but helpful: a USB-C ↔ USB-A/C data cable that supports both data and power.

## Build & Install
1. Clone the repository and connect your rooted device with USB debugging enabled.
2. From the project root run:
	```bash
	./gradlew :app:assembleDebug
	adb install -r app/build/outputs/apk/debug/app-debug.apk
	```
3. Launch **oLinky** on the device. The onboarding wizard will guide you through:
	- Granting root when Magisk/SuperSU prompts.
	- Approving **All Files Access** so oLinky can manage `/storage/emulated/0/oLinky/images`.
	- Confirming the auto-detected USB profile (Pixel, Samsung, Qualcomm QTI, MediaTek, or Generic).

> Tip: if the APK install fails because a release is already present, uninstall the old build first with `adb uninstall com.olinky.app`.

## Root & Permissions Checklist
- Ensure Magisk (or your superuser manager) is set to “Prompt” and allow oLinky permanently when asked.
- On Android 11+, approve “Allow access to manage all files” in system settings when the app redirects you.
- After onboarding, you can revisit the steps from **Settings ▸ Onboarding** inside the app if you swap kernels or storage locations.

## Roadmap
- Flesh out the main dashboard with mount/unmount flows and PXE service controls.
- Implement real ConfigFS mass-storage binding using the gadget core module.
- Add ISO library scanning with checksum verification and favorites.
- Ship a background service that keeps mounts alive while the UI is minimized.
- Publish device compatibility reports and community-driven profile tweaks.

## Status
🚧 Pre-alpha planning. Core architecture and project scaffolding are underway. Community feedback and device compatibility reports are welcome.