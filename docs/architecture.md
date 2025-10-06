# oLinky Architecture & Requirements

## Vision
- Turn a rooted Android device into a flexible USB mass-storage and PXE boot host, comparable to historical DriveDroid functionality.
- Provide a modern, user-friendly interface that allows mounting and serving disk images (ISO, IMG, raw) over USB gadget mode and network-based PXE.
- Maintain seamless switching between roles (storage vs. PXE), with minimal manual shell interaction required from the user.

## Target Users & Constraints
- **Users**: Enthusiasts, IT professionals, and system rescuers who rely on rooted Android devices as portable boot media.
- **Device Requirements**:
  - Root access with either Magisk or an unlocked boot image allowing `su` shell execution.
  - Kernel support for USB gadget drivers (FunctionFS, ConfigFS, mass storage, RNDIS/ECM) and writable `/config` hierarchy.
  - Android 10+ recommended for modern API support; aim to keep backward compatibility to Android 8 where practical.
- **Desktop Compatibility**: Host PCs must support booting from USB mass storage or via PXE over USB/Ethernet emulation. Target BIOS and UEFI firmware, Windows/Linux/macOS hosts.

## Feature Requirements
1. **Image Library Management**
   - Import ISO/IMG files from local storage or network.
   - Validate and extract metadata (label, size, filesystem). Detect bootable images.
   - Organize into collections and favorites.
2. **USB Mass Storage Mode**
   - Configure ConfigFS to expose selected image as a writable mass storage gadget.
   - Toggle read-only/read-write modes; ensure safe unmounting.
   - Support multiple partition layouts (MBR, GPT) and common filesystems (FAT/FAT32/exFAT/NTFS/ext4) via raw image passthrough.
3. **Live Writable Media**
   - Provide optional backing file overlays (cow file) to capture writes while preserving pristine ISO.
   - Allow exporting overlays as separate images.
4. **PXE over USB/Ethernet**
   - Spin up lightweight DHCP, TFTP, and HTTP servers on the device.
   - Bridge over RNDIS/ECM gadget to present as USB Ethernet to host.
   - Serve preconfigured boot menus (iPXE scripts) referencing stored images.
5. **Automation & Profiles**
   - Save per-host profiles (e.g., default ISO, PXE script).
   - Offer quick actions (mount last image, start PXE service on connect).
6. **Safety & Monitoring**
   - Detect host disconnects and gracefully unmount.
   - Monitor temperature/battery to prevent damage.
   - Provide logs for debugging gadget setup.

## Non-Functional Requirements
- **Usability**: Intuitive Compose UI, clear status indicators, guided setup for required kernel capabilities.
- **Performance**: Minimal latency when switching images; aim for <3s mount time.
- **Reliability**: Self-healing gadget configuration, watchdog for background services.
- **Security**: Reduce attack surface from exposed services, respect SELinux, restrict root calls to controlled scripts.
- **Extensibility**: Modular architecture allows adding new gadget roles (e.g., HID keyboard injection) later.

## System Architecture Overview
```
+-----------------------------+        +-----------------------------+
|         UI Layer            |        |     Background Services     |
|  - Jetpack Compose screens  |  uses  |  - Foreground service       |
|  - Navigation & state store +-------->  - USB gadget manager       |
|  - Image browser dialogs    |        |  - PXE server controller    |
+-----------------------------+        |  - Overlay/COW handler      |
                                       +--------------+--------------+
                                                      |
                                                      v
                                       +-----------------------------+
                                       |    Root Execution Layer     |
                                       |  - Kotlin JNI/shell bridge  |
                                       |  - ConfigFS script engine   |
                                       |  - Busybox/toybox helpers   |
                                       +--------------+--------------+
                                                      |
                                                      v
                                       +-----------------------------+
                                       |   Android Kernel Interfaces |
                                       |  - /sys/class/udc           |
                                       |  - /config/usb_gadget       |
                                       |  - /dev/loop, /dev/block    |
                                       |  - Netd / tethering APIs    |
                                       +-----------------------------+
```

### Module Breakdown
- **app** (Android application)
  - Compose UI, ViewModels, data repositories.
  - Communicates with service module via `ServiceConnection` or Hilt DI.
- **core-root**
  - Provides Kotlin abstractions over `su` shell, command batching, and result parsing.
  - Includes safety checks (e.g., verifying binary presence, SELinux permissive/enforcing state).
- **core-gadget**
  - ConfigFS manipulation utilities (create gadget, set descriptors, bind functions).
  - Loop device setup for image files and overlay management.
- **feature-pxe**
  - Embedded binaries (dnsmasq/udhcpd, busybox TFTP) or Kotlin-based servers.
  - Configuration generators for PXE menus.
- **data**
  - Room database storing image metadata, profiles, logs.
  - File management helpers using Storage Access Framework or scoped storage exemptions.

### Data Flows
1. User selects image → ViewModel loads metadata → Requests Service to prepare loop device → Service uses root layer to attach image and configure gadget → UI observes state flow updates to render "Mounted" status.
2. PXE profile start → Service enables RNDIS function, configures DHCP/TFTP via root commands → Broadcast receiver monitors host link state.

## Root & Privilege Strategy
- Use the Magisk `su` binary, falling back to standard `su` where available.
- Maintain a dedicated background `su` session to reduce overhead, but revalidate after failures.
- Ship shell scripts alongside the APK in `/data/data/<pkg>/files` and mark executable at runtime.
- Detect SELinux mode; provide guidance if permissive required.

## Security Considerations
- Sandbox root commands: whitelist allowed scripts, avoid interpolating unchecked user input.
- Prompt user before enabling write mode or PXE services; require explicit consent per session.
- Auto-stop PXE servers when screen locks or battery critically low.
- Encrypt overlay files using device keystore when possible.
- Provide clear warning when exposing images containing personal data.

## Compatibility Risks & Mitigations
- **Kernel lacks ConfigFS**: Graceful degradation—guide user to flash compatible kernel or use tethered ISO streaming.
- **SELinux restrictions**: Attempt to use `su --mount-master` or temporarily set permissive (with user approval), fall back otherwise.
- **USB controller quirks**: Maintain device-specific profiles (e.g., Samsung, OnePlus) with alternate UDC names.
- **PXE port conflicts**: Randomize ports or detect existing services.

## Testing Strategy
- Unit tests for command builders and state reducers (JUnit + Robolectric).
- Instrumentation tests for UI flows (Compose testing).
- Integration tests using Android emulator with root (pre-rooted system images) and physical devices in CI via Firebase Test Lab (where root available) or scripted local tests.
- Automated lint checks for permissions and storage usage.

## Roadmap
1. MVP: USB mass storage mounting of single image (read-only), basic UI, logs.
2. Add write overlays, image library metadata, and notifications.
3. Integrate PXE service with RNDIS gadget.
4. Automations, profiles, and advanced settings.
5. Device compatibility matrix & community contributions.

## Onboarding Wizard Concept
To keep first-run friction low while still gathering the capabilities we need, a guided setup will walk the user through three critical steps before unlocking the main dashboard.

### Step 1: Root Permission Handshake
- Detect the presence of `su` binaries (Magisk, SuperSU, or built-in) and issue a no-op test command (`id -u`).
- Trigger a foreground request so the superuser client shows a permission prompt; surface rationale text explaining the requirement.
- Persist the grant status in DataStore; monitor for revocation and provide retry options.
- Offer troubleshooting help if root is missing (link to docs, show detected binaries, SELinux mode).

### Step 2: Image Library Directory
- Propose a default folder under scoped storage (e.g., `/storage/emulated/0/oLinky/images`) with a one-tap “Create & Open” action.
- Allow advanced users to pick an alternate path, including root-protected directories like `/data/media/0/oLinky` when root browsing is available.
- Verify read/write access and loopback support (create tiny temp file, attempt to attach loop device when possible).
- Present quick tips for placing ISO/IMG files and offer sample downloads.

### Step 3: USB Gadget Profile Selection
- Query `/sys/class/udc` and known OEM quirks to list viable UDC targets (e.g., `a600000.dwc3`, `dwc3-musb-hdrc`).
- Provide presets for common OEM kernels (Pixel A/B, Samsung DS, OnePlus) with descriptions of supported gadget combinations (Mass Storage, RNDIS, CDC-ECM).
- Store the chosen profile and allow manual override later in settings.
- Run a smoke test script that binds/unbinds an empty ConfigFS gadget to confirm the selection before completing onboarding.

### Completion & Main Menu
- Once all steps succeed, transition into the dashboard with contextual cards that reflect the configured directory, root status, and selected USB profile.
- The wizard remains accessible under Settings for reconfiguration or onboarding new profiles.

## External Dependencies
- Busybox binaries (statically linked) for consistency.
- dnsmasq or similar lightweight DHCP/TFTP packages.
- Kotlin coroutines, Jetpack Compose, Hilt, Room, DataStore, AndroidX.

## Open Questions
- Should we embed native binaries via the NDK or download on first launch?
- How to handle OTG host detection automatically across OEM builds?
- Can we support non-rooted devices using adb shell + web interface as a limited alternative?
