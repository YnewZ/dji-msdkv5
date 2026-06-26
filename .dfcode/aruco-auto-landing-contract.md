# ArUco Auto Landing Contract

## Goal
Implement the first conservative ArUco-guided landing stage for the DJI MSDK V5 sample app.

## User choices
- Descent strategy: conservative staged descent.
- Final low-height stage: stop visual control and call DJI auto landing.
- User says downward obstacle avoidance is already disabled.
- Keep using MSDK V5 Virtual Stick Advanced Mode.

## Relevant existing files
- `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/models/ArucoLandingVM.kt`
- `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/aruco/ArucoAlignController.kt`
- `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/aruco/ArucoGuidance.kt`
- `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/pages/ArucoLandingFragment.kt`
- `SampleCode-V5/android-sdk-v5-sample/src/main/res/layout/frag_aruco_landing_page.xml`
- `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/models/BasicAircraftControlVM.kt` for reference to `KeyStartAutoLanding`.

## DJI forum guidance from downloaded article
The downloaded article `如何使用虚拟摇杆降落？ – 大疆创新SDK技术支持论坛 (2026_6_26 13：56：57).html` says:
- Virtual-stick descent can be blocked by downward obstacle avoidance; it should be disabled before descent.
- After landing on the ground, `KeyStartAutoLanding` can be called to stop motors.

## Control constraints
- Existing horizontal Auto Align has been field-tested as correct after pitch/roll mapping fix.
- In MSDK V5 advanced velocity mode based on user research:
  - `pitchVelocity` corresponds to lateral left/right velocity.
  - `rollVelocity` corresponds to forward/backward velocity.
  - `verticalThrottle < 0` means descending in vertical velocity mode.
  - `yawRate = 0.0` for this round.
- Keep horizontal correction very slow: max no more than `0.05 m/s`.
- Descent velocity should be very conservative: use about `-0.08 m/s` to `-0.10 m/s`.
- Never descend while marker is lost.
- Never descend while horizontal error is outside the alignment threshold.
- If marker is lost, send zero horizontal/vertical/yaw command and hover.
- Do not implement yaw alignment in this round.

## Landing state machine
Add a small internal state machine in `ArucoLandingVM` or a new file owned by the same worker:
- `IDLE`
- `ALIGN_ONLY`
- `DESCENDING`
- `FINAL_AUTO_LANDING`

Behavior:
1. `Auto Align` continues current horizontal-only behavior.
2. Add a new `Auto Land` or `Start Landing` button.
3. `Auto Land` starts detection, enables virtual stick advanced mode, and begins in `ALIGN_ONLY`.
4. When marker is visible and aligned for a short stable period (for example 1.0-1.5 seconds), enter `DESCENDING`.
5. In `DESCENDING`:
   - keep horizontal correction active,
   - set vertical speed to a small negative descent speed only if aligned,
   - pause descent and keep horizontal correction if not aligned,
   - zero all commands if marker is lost.
6. At low altitude threshold, stop visual control, disable virtual stick, and call `FlightControllerKey.KeyStartAutoLanding`.

## Altitude handling
Use MSDK altitude keys if feasible:
- Prefer `FlightControllerKey.KeyUltrasonicHeight` when available and reasonable.
- Otherwise use `FlightControllerKey.KeyAltitude`.
- Conservative final threshold: about `0.8m` to `1.0m`.
If robust altitude integration is too risky for one round, implement a guarded fallback:
- Provide a button/status that warns final auto landing will be triggered only when altitude is known.
- Do not trigger final auto landing without a reasonable altitude value.

## Safety requirements
- `STOP` must stop auto align/landing, send zero command, disable virtual stick if it was enabled, and stop detection.
- Do not call `KeyStartAutoLanding` repeatedly.
- If auto landing is triggered, stop the align loop first.
- Keep UI status explicit: aligning, descending, marker lost, final auto landing, stopped.
- No changes to API key, Gradle config, downloaded HTML files, or unrelated sample pages.

## Worker ownership
Single worker role `landing-impl` owns:
- ArUco landing model/page/layout files listed above.
- It may add one new ArUco landing state/controller file under `.../aircraft/aruco/` if useful.

## Verification
- Build `./gradlew :sample:assembleDebug --quiet` from `SampleCode-V5/android-sdk-v5-as` if local SDK config allows.
- Report exact modified files and build result.
