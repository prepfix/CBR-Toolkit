CBR TOOLKIT - WEB + ANDROID APP IN ONE REPOSITORY (prepfix/CBR-Toolkit)
=======================================================================

WHAT IS IN THIS FOLDER
- index.html, web.json, words.txt, sw.js, manifest.webmanifest, icons/  = the web version (GitHub Pages), version 2
- android/                                                              = the Android app (real phone lock)
- .github/workflows/android.yml                                         = builds the Android app file (APK)

UPLOAD INTO YOUR EXISTING REPOSITORY (github.com/prepfix/CBR-Toolkit)
1. Open the repository > Add file > Upload files.
2. Drag in index.html, web.json, words.txt, sw.js, manifest.webmanifest, README-ANDROID.txt,
   the "icons" folder and the "android" folder. Commit changes. (This replaces the old web files with the new ones.)
3. The hidden ".github" folder often will not drag in. Do this instead:
   Add file > Create new file. In the name box type:   .github/workflows/android.yml
   (typing the slashes makes the folders). Open "android/android-workflow.yml.txt" from this zip,
   copy everything in it, paste it into the big box, then Commit changes.

BUILD THE APK
1. Actions tab > "Build Android APK" > Run workflow. Wait about 5 minutes.
   (If the Actions tab asks, press the green button to enable workflows.)
2. Open the finished run > Artifacts > download "CBR-Toolkit-APK" > unzip > app-debug.apk.
3. Send app-debug.apk to the phone, tap it, allow "Install unknown apps".
   If Play Protect warns, tap "More details" > "Install anyway".
4. Open CBR Toolkit and finish the 5 set-up steps (emergency code, display over other apps,
   usage access, notifications, no battery limits), then press "Start CBR Toolkit".
   If you start after 4:00 AM without finishing today's reading, the phone locks at once.

PHONES THAT STOP BACKGROUND APPS (Tecno, Infinix, Xiaomi, Samsung)
Switch on "Autostart" / "Allow background activity" for CBR Toolkit and lock it in recent apps.

KEEPING THE APP UP TO DATE
Whenever you upload a new index.html, web.json or words.txt, the workflow rebuilds the APK automatically
(or run it by hand from the Actions tab). In sw.js change "cbr-v2" to "cbr-v3" so web users get the new page.

LIMITS
- Switching the phone off, Safe Mode or a factory reset still get around the lock.
- iPhones cannot be locked this way. They can only use the web version.
- Notes and progress stay on each device and do not sync.
