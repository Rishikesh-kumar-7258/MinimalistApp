# How to Build and Install the App Without Android Studio

Everything is done from the terminal. This takes about 15–20 minutes the first time.

---

## Part 1 — Install Prerequisites

### 1. Java 17
```bash
sudo apt update
sudo apt install openjdk-17-jdk -y
```

Verify it worked:
```bash
java -version
# should print: openjdk version "17.x.x"
```

---

### 2. Android SDK Command Line Tools

These are the build tools Android needs — a small download (~100 MB), no full Android Studio required.

```bash
# Create a folder for the SDK
mkdir -p ~/android-sdk/cmdline-tools

# Download the command line tools
cd ~/Downloads
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip

# Extract into the right folder structure
unzip commandlinetools-linux-11076708_latest.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
```

Add these lines to your `~/.zshrc` (since you are using zsh):
```bash
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
```

Apply the changes:
```bash
source ~/.zshrc
```

Verify it worked:
```bash
sdkmanager --version
# should print a version number
```

---

### 3. Install Required Android SDK Packages
```bash
sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;34.0.0"
```

Accept all the licenses when prompted (type `y` and press Enter for each):
```bash
sdkmanager --licenses
```

---

### 4. Gradle (to generate the build wrapper)
```bash
# Install SDKMAN (a version manager for dev tools)
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh

# Install Gradle 8.7
sdk install gradle 8.7
```

Verify:
```bash
gradle --version
# should print: Gradle 8.7
```

---

## Part 2 — Build the App

### 1. Go into the project folder
```bash
cd ~/Desktop/Misc/Minimalist\ App/MinimalistLauncher
```

### 2. Generate the Gradle wrapper (one-time setup)
```bash
gradle wrapper
```

This creates the `gradlew` script and `gradle/wrapper/gradle-wrapper.jar` that the project needs.

### 3. Tell the build system where your Android SDK is
```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

### 4. Build the APK
```bash
./gradlew assembleDebug
```

This downloads all app dependencies and compiles the app. It takes 3–10 minutes the first time. When it finishes you will see:

```
BUILD SUCCESSFUL
```

Your APK is now at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Part 3 — Install on Your Phone

You have two options.

---

### Option A — Install via USB (Fastest)

**On your phone:**
1. Go to **Settings → About Phone**
2. Tap **Build Number** 7 times → "You are now a developer"
3. Go to **Settings → System → Developer Options**
4. Toggle **USB Debugging** ON

**On your laptop:**
1. Plug your phone in via USB
2. On your phone, tap **Allow** when the USB debugging prompt appears
3. Check your phone is detected:
   ```bash
   adb devices
   # should show your phone's serial number
   ```
4. Install the app:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
5. Done — the app is now installed on your phone

---

### Option B — Transfer the APK File Manually

If you don't want to use a cable or USB debugging:

1. Copy the APK to a location you can access from your phone:
   ```bash
   # Option: copy to USB drive
   cp app/build/outputs/apk/debug/app-debug.apk /path/to/usb-drive/

   # Option: copy to Google Drive folder (if Drive is synced on your laptop)
   cp app/build/outputs/apk/debug/app-debug.apk ~/GoogleDrive/
   ```
   Or email it to yourself as an attachment.

2. On your phone, open the file and tap **Install**
   (you may need to allow installation from unknown sources — Settings → Apps → Special App Access → Install Unknown Apps)

---

## Part 4 — Set It as Your Default Launcher

When you open the app for the first time it will automatically ask you to set it as the default launcher. Tap **Set as default** and confirm.

If you missed the prompt:
1. Go to **Settings → Apps → Default Apps → Home App**
2. Select **Minimalist**

---

## Rebuilding After Code Changes

Every time you make changes to the code, just rebuild and reinstall:

```bash
cd ~/Desktop/Misc/Minimalist\ App/MinimalistLauncher
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `-r` flag reinstalls without uninstalling first, so your settings are preserved.

---

## Switching Back to Your Old Launcher

1. Go to **Settings → Apps → Default Apps → Home App**
2. Select your previous launcher (e.g., Pixel Launcher, One UI Home)

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `sdkmanager: command not found` | Run `source ~/.zshrc` and try again |
| `BUILD FAILED` — SDK not found | Make sure `local.properties` exists and contains the correct path: `sdk.dir=/home/yourname/android-sdk` |
| `adb: command not found` | Run `source ~/.zshrc`; confirm `platform-tools` installed with `sdkmanager --list_installed` |
| Phone not detected by `adb devices` | Unplug and replug; make sure USB mode is set to **File Transfer**, not Charging |
| "App not installed" error on phone | Uninstall any previous version first: `adb uninstall com.minimalist.launcher` |
| Gradle wrapper jar missing | Re-run `gradle wrapper` from inside the `MinimalistLauncher/` folder |
