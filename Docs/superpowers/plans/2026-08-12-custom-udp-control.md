# Custom UDP Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a personal “我的功能 / Custom Features” entry in the MSDK 5.18 sample, move the existing ArUco landing page under it, and add a UDP virtual-stick control page that receives 24-byte UDP packets and forwards the parsed values to DJI Virtual Stick without value limiting.

**Architecture:** Keep DJI official sample feature pages intact except replacing the direct ArUco entry in the Common list with one custom-feature hub entry. The hub navigates to custom feature pages through `nav_common.xml`; UDP handling is split into a focused UDP receiver and a ViewModel that owns Virtual Stick lifecycle and page state.

**Tech Stack:** Android Kotlin, DJI MSDK V5.18 `VirtualStickManager`, Android Navigation XML, ViewBinding, `DatagramSocket`, Kotlin thread.

---

## Implementation Tasks

1. Add a custom feature hub entry in `CommonFragmentPageInfoFactory.kt` and `nav_common.xml`, replacing the direct ArUco list item.
2. Create `CustomFeaturesFragment.kt` and `frag_custom_features_page.xml` with buttons for ArUco landing and UDP control.
3. Add `UdpControlServer.kt` to receive UDP packets on port 9999.
4. Add `UdpControlVM.kt` to enable Virtual Stick, enable Advanced Mode, parse 24-byte little-endian float packets, forward values unchanged, send zero on timeout/stop, and disable Virtual Stick on stop.
5. Add `UdpControlFragment.kt` and `frag_udp_control_page.xml` for start/stop UI and received-message display.
6. Add English and Chinese strings for the hub and UDP page.
7. Add `android.permission.INTERNET` if absent.
8. Verify with `./gradlew :sample:compileDebugKotlin` and `./gradlew :sample:assembleDebug` from `SampleCode-V5/android-sdk-v5-as`.

## Key Decisions

- The official DJI sample feature list should not contain every custom feature directly; it will contain only one “Custom Features / 我的功能” entry.
- Existing ArUco landing remains implemented by `ArucoLandingFragment`, but its direct Common-list entry is replaced by the custom hub.
- UDP values are not limited by the app, per user request.
- Non-limiting safety behavior is retained: invalid packet length is ignored, stop sends zero, page exit stops control, and UDP timeout sends zero.
- HTTP control and PSDK-to-virtual-stick control from the old MSDK 5.17 project are not migrated.

## Files

- Modify `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/data/CommonFragmentPageInfoFactory.kt`
- Modify `SampleCode-V5/android-sdk-v5-sample/src/main/res/navigation/nav_common.xml`
- Create `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/pages/CustomFeaturesFragment.kt`
- Create `SampleCode-V5/android-sdk-v5-sample/src/main/res/layout/frag_custom_features_page.xml`
- Create `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/control/UdpControlServer.kt`
- Create `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/models/UdpControlVM.kt`
- Create `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/pages/UdpControlFragment.kt`
- Create `SampleCode-V5/android-sdk-v5-sample/src/main/res/layout/frag_udp_control_page.xml`
- Modify `SampleCode-V5/android-sdk-v5-sample/src/main/res/values/strings.xml`
- Modify `SampleCode-V5/android-sdk-v5-sample/src/main/res/values-zh-rCN/strings.xml`
- Modify `SampleCode-V5/android-sdk-v5-sample/src/main/AndroidManifest.xml`

## Verification

Run:

```bash
./gradlew :sample:compileDebugKotlin
./gradlew :sample:assembleDebug
```

Expected:

- Kotlin compilation succeeds.
- Debug APK builds successfully.
- Navigation path works: `Testing Tools -> 我的功能 -> ArUco 引导降落` and `Testing Tools -> 我的功能 -> UDP 控制`.
- UDP page starts listener on port 9999 and displays received 24-byte packet values.
