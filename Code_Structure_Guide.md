# Operation Great Hall - Code Structure & Export Guide

Our project has grown into a massive Native Android application (over 5,000 lines of code!). Copy-pasting this manually from the chat or text files layer-by-layer is very difficult and risks breaking the code formatting.

Instead, all the code is already separated into clean layer files in this workspace.

## How to Get the Code and the App
Right here in the AI Studio editor, look for the **Settings / Export Menu** (usually top right or side menu):

1. **Export as ZIP**: This downloads the *entire* codebase (every layer, icon, and gradle built file) straight to your device in one click. You can open this in Android Studio.
2. **Push to GitHub**: This automatically uploads the entire codebase to a GitHub repository, which is perfect for your "Mediator" GitHub action loops and sharing the code professionally.
3. **Generate APK / AAB**: This is massive for you! Instead of giving people a URL to look at, generating an APK gives you an actual installable Android app file. You can put this on the **Google Play Store** to monetize it, or send the `.apk` directly to you and your friends' Android phones to install as a real app.

## Codebase Layer Breakdown
If you are exploring the File Editor on the left, here is how the application architecture is sectioned:

### 1. The Presentation & UI Layer
* **Path**: `app/src/main/java/com/example/ui/GreatHallApp.kt`
* **Purpose**: This is the massive core visual engine. It holds the `GreatHallApp` Compose structures, the Dashboard, tools (Pen, Eraser), all UI Tabs (Chat, Speech, Media, Art, Protocols), and the specific screens for sub-projects like Lighthouse and Technomancer.

### 2. State Management & Logic Layer
* **Path**: `app/src/main/java/com/example/ui/GreatHallViewModel.kt`
* **Purpose**: Your Architect's brain. It controls the AI logic paths, offline data persistence states, file writing formats (takeout.json), and the internal commands that operate the UI.

### 3. Data & Persistence Layer
* **Path**: `app/src/main/java/com/example/data/GreatHallRepository.kt`
* **Path**: `app/src/main/java/com/example/data/AppDatabase.kt`
* **Purpose**: The SQLite Room Database definition. This securely stores the "Operation Great Hall" directives, "Project Lighthouse" mandates, the project concepts, and the local AI memory logs so they are never lost.

### 4. Design System Layer
* **Path**: `app/src/main/java/com/example/ui/theme/Theme.kt`
* **Purpose**: The deep visual styling, colors (e.g., Electric Cyan, Near Black, Green Phosphor), typography, and formatting rules that make the app feel unique and sovereign.
