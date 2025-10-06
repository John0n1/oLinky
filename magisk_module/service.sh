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
    sleep 1
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

wait_for_usb_state_change() {
  local baseline="${1}"
  local timeout="${2:-5}"
  local attempts="${timeout}"
  local current

  if [ -z "${baseline}" ]; then
    baseline=$(getprop sys.usb.state | tr -d '\r')
  fi

  while [ "${attempts}" -gt 0 ]; do
    current=$(getprop sys.usb.state | tr -d '\r')
    if [ "${current}" != "${baseline}" ]; then
      if [ -n "${current}" ]; then
        log_info "sys.usb.state transitioned to ${current}"
      fi
      return 0
    fi
    sleep 1
    attempts=$((attempts - 1))
  done

  current=$(getprop sys.usb.state | tr -d '\r')
  log_warn "sys.usb.state stuck at ${current} (baseline ${baseline})"
  return 1
}

CONFIGFS_ROOT="/config/usb_gadget"
APP_PROCESS="com.olinky.app"
UDC_SYS_PATH="/sys/class/udc"
PREFERRED_UDC_PROP="sys.olinky.preferred_udc"
CURRENT_PREFERRED_UDC=""

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
        state_before=$(getprop sys.usb.state | tr -d '\r')
        if printf '' > "${udc_file}" 2>/dev/null; then
          wait_for_usb_state_change "${state_before}" 6 || true
          if [ "${gadget_name}" = "g1" ]; then
            log_warn "Released stock gadget ${gadget_name}; USB defaults temporarily disabled"
          fi
        else
          log_warn "Failed to release ${gadget_name} from ${current_udc}"
          if wait_for_usb_state_change "${state_before}" 6; then
            log_info "Retrying release for ${gadget_name}"
            if printf '' > "${udc_file}" 2>/dev/null; then
              if [ "${gadget_name}" = "g1" ]; then
                log_warn "Released stock gadget ${gadget_name} after retry"
              else
                log_info "Released ${gadget_name} after retry"
              fi
            else
              log_warn "Retry failed for ${gadget_name} bound to ${current_udc}"
            fi
          fi
        fi
        sleep 0.2
      fi
    fi
  done
}

publish_preferred_udc() {
  if [ ! -d "${UDC_SYS_PATH}" ]; then
    return
  fi

  local preferred=""
  local fallback=""
  local entry
  local udc_name

  for entry in "${UDC_SYS_PATH}"/*; do
    [ -e "${entry}" ] || continue
    udc_name=$(basename "${entry}")
    if printf '%s' "${udc_name}" | grep -q '^dummy_udc'; then
      if [ -z "${fallback}" ]; then
        fallback="${udc_name}"
      fi
      continue
    fi

    if [ -e "${entry}/device" ] || [ -e "${entry}/uevent" ]; then
      preferred="${udc_name}"
      break
    fi

    if [ -z "${preferred}" ]; then
      preferred="${udc_name}"
    fi
  done

  if [ -z "${preferred}" ]; then
    preferred="${fallback}"
  fi

  if [ -z "${preferred}" ]; then
    return
  fi

  if [ "${preferred}" != "${CURRENT_PREFERRED_UDC}" ]; then
    setprop "${PREFERRED_UDC_PROP}" "${preferred}"
    CURRENT_PREFERRED_UDC="${preferred}"
    log_info "Preferred UDC set to ${preferred}"
  fi
}

start_gadget_watchdog() {
  while true; do
    if pgrep -f "${APP_PROCESS}" >/dev/null 2>&1; then
      ensure_configfs_mounted
      set_usb_configfs_flag 1
      ensure_usb_interface_down usb0
      publish_preferred_udc
      release_udc_slots
    fi
    sleep 1
  done
}

if [ -z "${OLINKY_SKIP_MAIN}" ]; then
  ensure_configfs_mounted
  apply_usb_policies
  set_usb_configfs_flag 1
  publish_preferred_udc
  start_gadget_watchdog &
fi
