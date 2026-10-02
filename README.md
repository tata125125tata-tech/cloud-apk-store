# Cosmo Cloud Manager 🚀
*A mobile Cloudflare R2 bucket manager with chunked multipart background uploads, resilient streaming, and automated GitHub Actions & GitHub Releases APK publishing.*

---

### 📦 Repository & Direct APK Download

[![Direct APK Download](https://img.shields.io/badge/Direct%20Download-CosmoCloud--debug.apk-00E5FF?style=for-the-badge&logo=android)](https://github.com/tata125125tata-tech/cloud-apk-store/releases/latest/download/CosmoCloud-debug.apk)
[![GitHub Actions Build](https://img.shields.io/github/actions/workflow/status/tata125125tata-tech/cloud-apk-store/android-build.yml?branch=main&label=APK%20Build&style=for-the-badge)](https://github.com/tata125125tata-tech/cloud-apk-store/actions)
[![Latest Release](https://img.shields.io/github/v/release/tata125125tata-tech/cloud-apk-store?style=for-the-badge)](https://github.com/tata125125tata-tech/cloud-apk-store/releases/latest)

> **📱 Direct Mobile Download Link:**  
> **[https://github.com/tata125125tata-tech/cloud-apk-store/releases/latest/download/CosmoCloud-debug.apk](https://github.com/tata125125tata-tech/cloud-apk-store/releases/latest/download/CosmoCloud-debug.apk)**  
> *(Click this link from your Android browser to download and install `CosmoCloud-debug.apk` directly without needing to unzip any GitHub Actions artifacts!)*

---

## 1. Quick Start: Push to `tata125125tata-tech/cloud-apk-store`

To trigger the automated GitHub Actions build and generate the direct download APK:

```bash
# Initialize git if not already done
git init

# Stage all files (including .github/workflows, gradlew, and source code)
git add .

# Commit
git commit -m "Initialize Cosmo Cloud Manager with GitHub Actions APK builder"

# Set default branch to main
git branch -M main

# Link to your GitHub repository
git remote add origin https://github.com/tata125125tata-tech/cloud-apk-store.git

# Push to GitHub (use --force if repository was initialized with a README/license)
git push -u origin main --force
```

Once pushed:
1. GitHub Actions automatically starts running the workflow in **Actions**.
2. Gradle compiles the Android project and generates `CosmoCloud-debug.apk`.
3. The workflow automatically publishes a **GitHub Release (`latest`)** with the direct APK attachment.
4. Anyone can download and install the APK directly from the release page!

---

## 2. Project Overview

**Cosmo Cloud Manager** is a native Android application built using Kotlin and Jetpack Compose. It allows developers and users to manage Cloudflare R2 object storage directly from their Android devices through a serverless Cloudflare Worker API.

### Key Highlights:
- **Zero Cloudflare Secrets in App**: The mobile client communicates exclusively with the serverless Worker API over HTTPS. R2 S3 credentials and Cloudflare API keys are kept safe in the Worker environment.
- **Multipart / Chunked Upload Engine**: Streams files in 10 MB chunks (configurable to 5 MB or 20 MB). Never loads multi-GB APKs, XAPKs, or video files into RAM.
- **Android Foreground Service**: Continues uploads seamlessly when the app is minimized, screen is locked, or another app is opened. Persistent notifications show upload speed, part progress, and ETA with Pause/Cancel actions.
- **Offline Resumption**: Upload metadata is stored in a local SQLite/Room database. Interrupted uploads automatically resume from the last completed part.
- **File & Image Browser**: Native support for APK, XAPK, APKM, APKS, ZIP, JPG, PNG, WEBP, and GIF files with search, sort, and category filtering.
- **Immediate Public URLs**: View, copy, and share public CDN links upon upload completion.
- **Dual APK Distribution**:
  - **GitHub Releases**: Direct 1-click `.apk` download link for end users.
  - **GitHub Actions Artifacts**: Downloadable build artifact bundle for CI tracking.

---

## 3. Architecture

```text
+-----------------------+
|  Android Application  |  (Kotlin + Jetpack Compose + Room + Foreground Service)
+-----------------------+
           │
           │ HTTPS Requests (POST /init, PUT /part, POST /complete, GET /files)
           ▼
+-----------------------+
| Cloudflare Worker API |  (Serverless Worker + Optional Auth)
+-----------------------+
           │
           │ Cloudflare R2 Bindings (env.MY_BUCKET)
           ▼
+-----------------------+
|     Cloudflare R2     |  (Object Storage Bucket)
+-----------------------+
```

### Automated GitHub CI/CD Pipeline:
```text
Android App Builder
        ↓  (Export Source)
GitHub Repository (tata125125tata-tech/cloud-apk-store)
        ↓  (git push main)
GitHub Actions (.github/workflows/android-build.yml)
        ↓  (./gradlew assembleDebug)
        ├──► GitHub Actions Artifact (cosmo-cloud-manager-debug-apk)
        └──► GitHub Release (latest) ──► Direct 1-Click APK Download (CosmoCloud-debug.apk)
```

---

## 4. Local Build Instructions

### Prerequisites
- JDK 17 or JDK 21 (Eclipse Temurin recommended)
- Android SDK Platform 36 (build-tools 36.x)

### Commands
```bash
# Clone the repository
git clone https://github.com/tata125125tata-tech/cloud-apk-store.git
cd cloud-apk-store

# Grant execution permission to Gradle Wrapper
chmod +x gradlew

# Build Debug APK
./gradlew assembleDebug --stacktrace

# The generated APK is located at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. GitHub Actions Workflow Configuration

The automated build workflow is configured in `.github/workflows/android-build.yml`.

### Triggers
- Automatic on every `push` to `main` or `master`.
- Automatic on release tags `v*` (e.g. `git tag v1.0.0 && git push origin v1.0.0`).
- Automatic on every `pull_request` targeting `main` or `master`.
- Manual trigger via `workflow_dispatch` with an option to select build type (`debug`, `release`, or `both`) and choose whether to publish to GitHub Releases.

### Release Permissions
The workflow includes:
```yaml
permissions:
  contents: write
```
This grants GitHub Actions permission to create or update the GitHub Release using the default `GITHUB_TOKEN` without requiring any third-party tokens or setup.

---

## 6. Downloading the Built APK

### Option A: Direct Download via GitHub Releases (Recommended for Phones)
1. Navigate to: **[https://github.com/tata125125tata-tech/cloud-apk-store/releases/latest](https://github.com/tata125125tata-tech/cloud-apk-store/releases/latest)**
2. Click **`CosmoCloud-debug.apk`**.
3. The APK will download directly to your Android device and can be installed immediately.

### Option B: Download via GitHub Actions Artifacts
1. Go to the **Actions** tab in your repository: [https://github.com/tata125125tata-tech/cloud-apk-store/actions](https://github.com/tata125125tata-tech/cloud-apk-store/actions)
2. Click on the latest workflow run.
3. Scroll down to the **Artifacts** section at the bottom.
4. Click on **`cosmo-cloud-manager-debug-apk`** to download the ZIP file containing `CosmoCloud-debug.apk`.

---

## 7. Release Signing & GitHub Secrets (Optional)

To generate production-signed release APKs automatically:

### 1. Generate an Upload Keystore locally:
```bash
keytool -genkey -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Encode Keystore to Base64:
```bash
# Linux/macOS:
base64 -w 0 my-upload-key.jks > keystore_base64.txt

# Windows PowerShell:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks")) | Out-File -Encoding ASCII keystore_base64.txt
```

### 3. Add GitHub Repository Secrets:
Go to **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**:

| Secret Name | Value |
|---|---|
| `KEYSTORE_BASE64` | The entire base64 string from `keystore_base64.txt` |
| `KEYSTORE_PASSWORD` | Password used when creating the keystore |
| `KEY_ALIAS` | `upload` (or your chosen alias) |
| `KEY_PASSWORD` | Password for the key alias |

### 4. Trigger Release Build:
Go to **Actions** -> Select **Android CI/CD** -> Click **Run workflow** -> Select `release` or `both`.
The signed APK will be uploaded to GitHub Releases as `CosmoCloud-release.apk`.

---

## 8. Worker URL Configuration

The Android application comes preconfigured with the developer Worker endpoint:
```text
https://tight-surf-5eff.play125store.workers.dev
```

You can change this anytime inside the app:
1. Open **Settings** in the bottom navigation.
2. Enter your custom Worker URL (e.g. `https://my-r2-worker.example.workers.dev`).
3. (Optional) Provide an Auth Token if your Worker is password-protected.
4. (Optional) Enter a Custom Public CDN Domain (e.g. `https://pub-r2.example.com`).
5. Tap **Test Connection (Ping)** to verify latency and status.
6. Tap **Save Settings**.

---

## 9. Reference Cloudflare Worker Implementation (`worker.js`)

Below is the standard Cloudflare Worker script that connects to your R2 bucket (`MY_BUCKET` binding):

```javascript
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method;

    // CORS Headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Upload-Id, X-Part-Number, X-Key, X-Worker-Auth',
    };

    if (method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    // Optional Worker Auth Check
    const authHeader = request.headers.get('Authorization') || request.headers.get('X-Worker-Auth');
    if (env.AUTH_TOKEN && authHeader !== `Bearer ${env.AUTH_TOKEN}` && authHeader !== env.AUTH_TOKEN) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    try {
      // 1. Health check
      if (path === '/health' || path === '/status' || path === '/') {
        return new Response(JSON.stringify({ status: 'ok', bucket: 'r2', version: 'v1' }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      // 2. List files
      if (path === '/files' && method === 'GET') {
        const listed = await env.MY_BUCKET.list({ limit: 500 });
        const files = listed.objects.map(obj => ({
          key: obj.key,
          size: obj.size,
          uploaded: obj.uploaded.getTime(),
          etag: obj.httpEtag,
          contentType: obj.httpMetadata?.contentType || 'application/octet-stream',
          url: `${url.origin}/files/${encodeURIComponent(obj.key)}`
        }));
        return new Response(JSON.stringify({ files }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      // 3. Init Multipart Upload
      if (path === '/init' && method === 'POST') {
        const data = await request.json();
        const key = data.filename;
        const multipart = await env.MY_BUCKET.createMultipartUpload(key, {
          httpMetadata: { contentType: data.contentType || 'application/octet-stream' }
        });
        return new Response(JSON.stringify({ uploadId: multipart.uploadId, key }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      // 4. Upload Part
      if (path === '/part' && method === 'PUT') {
        const uploadId = url.searchParams.get('uploadId') || request.headers.get('X-Upload-Id');
        const partNumber = parseInt(url.searchParams.get('partNumber') || request.headers.get('X-Part-Number'));
        const key = url.searchParams.get('key') || request.headers.get('X-Key');

        const multipart = env.MY_BUCKET.resumeMultipartUpload(key, uploadId);
        const uploadedPart = await multipart.uploadPart(partNumber, request.body);

        return new Response(JSON.stringify({ partNumber, etag: uploadedPart.etag }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json', 'ETag': uploadedPart.etag },
        });
      }

      // 5. Complete Multipart Upload
      if (path === '/complete' && method === 'POST') {
        const data = await request.json();
        const multipart = env.MY_BUCKET.resumeMultipartUpload(data.key, data.uploadId);
        const completed = await multipart.complete(data.parts);

        return new Response(JSON.stringify({
          success: true,
          key: completed.key,
          url: `${url.origin}/files/${encodeURIComponent(completed.key)}`,
          size: completed.size
        }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      // 6. Abort Upload
      if (path === '/abort' && method === 'POST') {
        const data = await request.json();
        const multipart = env.MY_BUCKET.resumeMultipartUpload(data.key, data.uploadId);
        await multipart.abort();
        return new Response(JSON.stringify({ success: true }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      // 7. Delete File
      if ((path.startsWith('/files/') && method === 'DELETE') || (path === '/delete' && method === 'POST')) {
        let key = path.startsWith('/files/') ? decodeURIComponent(path.slice(7)) : (await request.json()).key;
        await env.MY_BUCKET.delete(key);
        return new Response(JSON.stringify({ success: true, key }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      // 8. Serve / Download File directly
      if (path.startsWith('/files/') && method === 'GET') {
        const key = decodeURIComponent(path.slice(7));
        const object = await env.MY_BUCKET.get(key);
        if (!object) return new Response('File not found', { status: 404, headers: corsHeaders });
        const headers = new Headers(corsHeaders);
        object.writeHttpMetadata(headers);
        headers.set('etag', object.httpEtag);
        return new Response(object.body, { headers });
      }

      return new Response('Not found', { status: 404, headers: corsHeaders });
    } catch (err) {
      return new Response(JSON.stringify({ error: err.message }), {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }
  }
};
```

---

## 10. License
Cosmo Cloud Manager is open source under the MIT License.
