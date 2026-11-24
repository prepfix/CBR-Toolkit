CBR TOOLKIT - PHONE VERSION (installable web app)
=================================================

This is the same reading app as the PC version:
- Verses with notes are highlighted, you can copy verses, and your notes show above the chapter notes.
- Fact, Insight and Deed each need at least 3 sentences to unlock for the day.
- Reading at least one chapter (morning or afternoon) is enough. Being behind never blocks you.
- Spelling and word suggestions work offline. A+ / A- change the text size.
- Works offline after the first open.

PUT IT ONLINE (free, GitHub Pages)
1. On github.com create a repository, for example "CBR-Toolkit".
2. Upload EVERYTHING inside this folder (index.html, web.json, words.txt, sw.js,
   manifest.webmanifest and the icons folder). Keep the same file names.
3. Repository Settings > Pages > Source: "Deploy from a branch", branch "main", folder "/ (root)". Save.
4. After about a minute your link appears: https://YOURNAME.github.io/CBR-Toolkit/

INSTALL ON THE PHONE
Android (Chrome): open the link > menu (three dots) > "Install app" or "Add to Home screen".
iPhone (Safari): open the link > Share > "Add to Home Screen".
The first open needs internet (about 6 MB). After that it opens offline from the home screen.

UPDATING LATER
Upload the new files, and in sw.js change VERSION = "cbr-v1" to "cbr-v2" so phones fetch the new version.

NOTES
- Notes and progress are stored on that phone only. They do not sync with the PC.
- A web app cannot lock a phone. The "phone lock" needs the separate CBR phone app.
