# QuestRecorder

QuestRecorder is an Android recorder designed for Meta Quest 2, Quest 3 and Quest 3S.

The goal is to record a single Android application, such as PojavLauncher, instead of recording the whole headset view.

## Current version

The first functional version records:

- H.264 video
- MP4 output
- 1280×720
- 30 FPS
- 10 Mbps video bitrate
- Android MediaProjection capture
- foreground recording service
- notification button to stop recording

The output is stored in the application's external Movies directory under:

QuestRecorder/

## Audio

Game audio capture will be added separately with Android AudioPlaybackCapture. Android requires the target application to allow playback capture, so audio support depends on both Android and the application being recorded.

## Build

GitHub Actions builds a debug APK automatically on pushes to the main branch.

Open the Actions tab and download the QuestRecorder-debug artifact from the completed workflow.
