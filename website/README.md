# AirAssistant Download Website 🎧

This directory contains the landing and APK download page for **AirAssistant**. It is designed to be hosted 100% free with no Google Play Console account required.

---

## 🌟 What's Included
- **Modern Landing Page (`index.html`)**: Features dark glassmorphic design, animated phone mockup with Gemini waveform, and responsive layout.
- **Dynamic QR Code Generator**: Desktop visitors can scan the QR code to instantly download the APK on their phone.
- **Step-by-Step Installation Guide**: Sideloading walkthrough with instructions for Android's "Unknown Sources" setting and accessibility permissions.
- **Device Compatibility Matrix**: Details supported gestures for AirPods (1/2/3/Pro/Max) and other Bluetooth headsets.
- **FAQ Accordion**: Addresses security, battery usage, and why the app is distributed outside Google Play.

---

## 🚀 Free Hosting Options (Takes < 2 Minutes)

### Option 1: Netlify Drop (Easiest - Drag & Drop)
1. Go to [app.netlify.com/drop](https://app.netlify.com/drop).
2. Drag and drop the `website` folder into the browser.
3. You immediately get a live `https://<random-name>.netlify.app` URL (you can change the domain name in Site Settings for free).
4. Users can now visit the link and download `AirAssistant-v1.0.0.apk` directly!

### Option 2: GitHub Pages + GitHub Releases (Recommended for Git Users)
Because the APK file is ~58 MB, the cleanest way to host on GitHub without repository bloat is:
1. **Push your code to GitHub**.
2. Go to your repository &rarr; **Releases** &rarr; **Draft a new release**.
3. Set tag to `v1.0.0`, title to `AirAssistant v1.0.0`, and drag `AirAssistant-v1.0.0.apk` into the release assets.
4. Copy the direct link of the uploaded `.apk` file (e.g., `https://github.com/<username>/<repo>/releases/download/v1.0.0/AirAssistant-v1.0.0.apk`).
5. In `website/script.js`, paste this link into `CONFIG.downloadPath`.
6. In your repo settings &rarr; **Pages** &rarr; select **Deploy from branch**, branch `main`, and folder `/website` (or `/docs`).

### Option 3: Vercel or Cloudflare Pages
- Connect your GitHub repository to [vercel.com](https://vercel.com) or [pages.cloudflare.com](https://pages.cloudflare.com).
- Set root directory to `website`.
- Click **Deploy**.

---

## 📱 Local Testing
To preview the website locally on your computer:
1. Open PowerShell and run:
   ```powershell
   cd website
   python -m http.server 8000
   ```
2. Open your browser and navigate to `http://localhost:8000`.
