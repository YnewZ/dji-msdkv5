# ArUco Auto Align Mapping Contract

## Goal
Fix the ArUco auto-align control mapping for DJI MSDK V5 Virtual Stick Advanced Mode based on user research and field observations.

## User-confirmed assumptions
- The app is using MSDK V5.
- Virtual Stick Advanced Mode should be used.
- In `RollPitchControlMode.VELOCITY` under Virtual Stick Advanced Mode:
  - `roll` controls forward/backward velocity.
  - `pitch` controls left/right lateral velocity.
  - `yaw` in angular velocity mode controls aircraft rotation speed/direction.
- The gimbal points downward and is forward-centered without obvious yaw offset.

## Required mapping
Use the existing `ArucoDetection.normalizedErrorX/Y` values:
- `normalizedErrorX > 0`: marker is on the right side of image.
- `normalizedErrorX < 0`: marker is on the left side of image.
- `normalizedErrorY > 0`: marker is below image center.
- `normalizedErrorY < 0`: marker is above image center.

For BODY + VELOCITY advanced virtual stick mode, implement:
- image horizontal error -> `pitchVelocity` lateral command.
- image vertical error -> `rollVelocity` forward/back command.
- keep vertical speed `0.0`.
- keep yaw rate `0.0`.

The previous implementation likely used pitch/roll semantics incorrectly.

## Safety constraints
- Keep low max horizontal speed for testing: no more than `0.05 m/s` unless explicitly changed later.
- Keep single-axis dominant-axis behavior for now to reduce diagonal fly-away risk.
- Do not add automatic descent in this round.
- Do not change OpenCV/detector/video frame code.
- Do not change API key or Gradle config.

## Owned files
Worker role `align-mapping` owns only:
- `SampleCode-V5/android-sdk-v5-sample/src/main/java/dji/sampleV5/aircraft/aruco/ArucoAlignController.kt`

## Verification
- Build the sample debug APK with Gradle.
- Report the exact mapping implemented and whether build succeeded.
