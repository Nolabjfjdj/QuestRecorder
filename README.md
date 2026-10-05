# QuestRecorder

QuestRecorder is an Android recorder designed for Meta Quest 2, Quest 3 and Quest 3S.

The goal is to record a single Android application, such as PojavLauncher, instead of recording the whole headset view.

## Goals

- Single-app capture through Android MediaProjection
- 16:9 recording output
- Game audio capture when Android and the target application allow playback capture
- Optional microphone capture
- No headset movement in the recorded image
- APK builds through GitHub Actions

## Current status

The repository contains the Android foundation and the MediaProjection permission flow.

The actual video encoder, audio capture and MP4 muxing are the next implementation step.

## Build

GitHub Actions builds a debug APK automatically on pushes to the main branch.

Open the Actions tab and download the QuestRecorder-debug artifact from the completed workflow.
