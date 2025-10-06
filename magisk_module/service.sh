#!/system/bin/sh
# This script will be executed in late_start service mode
# More info in the Magisk Documentation
# https://topjohnwu.github.io/Magisk/guides.html

# Wait for the boot to complete
while [ "$(getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
  sleep 1
done
sleep 10

LOG_TAG="oLinkyMagisk"

log_info() {
  log -t "${LOG_TAG}" "$1"
}

log_warn() {
  log -p w -t "${LOG_TAG}" "$1"
}

apply_policy_rule() {
  local rule="$1"
  if magiskpolicy --live "$rule"; then
    log_info "Applied policy: $rule"
  else
    log_warn "Failed to apply policy: $rule"
  fi
}

apply_usb_policies() {
  local rule
  while IFS= read -r rule; do
    [ -n "${rule}" ] || continue
    apply_policy_rule "${rule}"
  done <<'EOF'
allow untrusted_app configfs dir { create search getattr setattr read write add_name remove_name rmdir }
allow untrusted_app configfs file { create getattr setattr read write open }
allow untrusted_app configfs lnk_file { create getattr setattr read write }
allow untrusted_app sysfs dir { search getattr }
allow untrusted_app sysfs file { open read write getattr setattr }
allow untrusted_app sysfs_usb dir { search getattr }
allow untrusted_app sysfs_usb file { open read write getattr setattr }
allow untrusted_app sysfs_net dir { search getattr }
allow untrusted_app sysfs_net file { open read write getattr setattr }
allow untrusted_app sysfs_usb_supply file { write }
allow untrusted_app sysfs_usb_device file { write }
allow untrusted_app sysfs_devices dir { search getattr }
allow untrusted_app sysfs_devices file { open read write getattr setattr }
allow untrusted_app sysfs_devices_platform dir { search getattr }
allow untrusted_app sysfs_devices_platform file { open read write getattr setattr }
EOF
}

set_usb_configfs_flag() {
  local desired="${1:-1}"
  local current
  current=$(getprop sys.usb.configfs | tr -d '\r')
  if [ "${current}" = "${desired}" ]; then
    return 0
  fi

  local attempt=1
  while [ "${attempt}" -le 3 ]; do
    setprop sys.usb.configfs "${desired}"
    sleep 0.5
    current=$(getprop sys.usb.configfs | tr -d '\r')
    if [ "${current}" = "${desired}" ]; then
      log_info "sys.usb.configfs stabilized at ${desired} (attempt ${attempt})"
      return 0
    fi
    attempt=$((attempt + 1))
  done

  log_warn "sys.usb.configfs remained ${current} after forcing ${desired}"
  return 1
}

ensure_usb_interface_down() {
  local iface="${1:-usb0}"
  local operstate_path="/sys/class/net/${iface}/operstate"
  if [ ! -f "${operstate_path}" ]; then
    return
  fi

  local state
  state=$(cat "${operstate_path}" 2>/dev/null | tr -d '\r')
  if [ "${state}" != "up" ]; then
    return
  fi

  if ip link set "${iface}" down 2>/dev/null; then
    log_info "Brought ${iface} down before PXE configuration"
  else
    log_warn "Failed to bring ${iface} down"
  fi
}

CONFIGFS_ROOT="/config/usb_gadget"
APP_PROCESS="com.olinky.app"
UDC_SYS_PATH="/sys/class/udc"

ensure_configfs_mounted() {
  if [ ! -d "${CONFIGFS_ROOT}" ]; then
    mkdir -p "${CONFIGFS_ROOT}" 2>/dev/null
  fi
  if ! mountpoint -q "${CONFIGFS_ROOT}"; then
    mount -t configfs none "${CONFIGFS_ROOT}" 2>/dev/null
  fi
}

release_udc_slots() {
  if [ ! -d "${CONFIGFS_ROOT}" ]; then
    return
  fi
  for gadget in "${CONFIGFS_ROOT}"/*; do
    [ -d "${gadget}" ] || continue
    udc_file="${gadget}/UDC"
    gadget_name=$(basename "${gadget}")
    if [ -f "${udc_file}" ]; then
      current_udc=$(cat "${udc_file}" 2>/dev/null)
      if [ -n "${current_udc}" ]; then
        if printf '%s' "${current_udc}" | grep -q '^dummy_udc'; then
          log_info "Skipping dummy controller ${current_udc} for ${gadget_name}"
          continue
        fi
        log_info "Releasing ${gadget_name} from ${current_udc}"
        if printf '' > "${udc_file}" 2>/dev/null; then
          if [ "${gadget_name}" = "g1" ]; then
            log_warn "Released stock gadget ${gadget_name}; USB defaults temporarily disabled"
          fi
        else
          log_warn "Failed to release ${gadget_name} from ${current_udc}"
        fi
        sleep 0.2
      fi
    fi
  done
}

start_gadget_watchdog() {
  while true; do
    if pgrep -f "${APP_PROCESS}" >/dev/null 2>&1; then
      ensure_configfs_mounted
      set_usb_configfs_flag 1
      ensure_usb_interface_down usb0
      release_udc_slots
    fi
    sleep 1
  done
}

ensure_configfs_mounted
apply_usb_policies
set_usb_configfs_flag 1
start_gadget_watchdog &
