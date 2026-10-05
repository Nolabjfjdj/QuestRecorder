# QuestRecorder

QuestRecorder is an Android recorder designed for Meta Quest 2, Quest 3 and Quest 3S.

The goal is to record a single Android application, such as PojavLauncher, instead of recording the whole headset view.

## Current version

**0.2.0**

QuestRecorder can currently:

- record a single Android application through MediaProjection
- capture H.264 video into MP4
- record at 600p, 720p or 1080p
- choose landscape or portrait orientation
- choose 30 or 60 FPS
- choose economic, standard or high video quality
- capture supported internal game/media audio with Android AudioPlaybackCapture
- capture microphone audio
- combine internal audio and microphone audio into the MP4 recording
- save recordings to `Movies/QuestRecorder`
- stop recording from the app or its foreground notification

Internal audio capture depends on Android and on the application being recorded allowing playback capture.

The recorder is designed for Meta Quest 2, Quest 3 and Quest 3S, including use with applications such as PojavLauncher.

## Build

GitHub Actions builds a debug APK automatically on pushes to the main branch.

Open the Actions tab and download the QuestRecorder-debug artifact from the completed workflow.
