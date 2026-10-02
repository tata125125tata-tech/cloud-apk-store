# Cosmo Cloud Manager 🚀
*A mobile Cloudflare R2 bucket manager with chunked multipart background uploads, resilient streaming, and automated GitHub Actions APK builds.*

---

## 1. Project Overview

**Cosmo Cloud Manager** is a native Android application built using Kotlin and Jetpack Compose. It allows developers and users to manage Cloudflare R2 object storage directly from their Android devices through a serverless Cloudflare Worker API.

### Key Highlights:
- **Zero Cloudflare Secrets in App**: The mobile client communicates exclusively with the serverless Worker API over HTTPS. R2 S3 credentials and Cloudflare API keys are kept safe in the Worker environment.
- **Multipart / Chunked Upload Engine**: Streams files in 10 MB chunks (configurable to 5 MB or 20 MB). Never loads multi-GB APKs, XAPKs, or video files into RAM.
- **Android Foreground Service**: Continues uploads seamlessly when the app is minimized, screen is locked, or another app is opened. Persistent notifications show upload speed, part progress, and ETA with Pause/Cancel actions.
- **Offline Resumption**: Upload metadata is stored in a local SQLite/Room database. Interrupted uploads automatically resume from the last completed part.
- **File & Image Browser**: Native support for APK, XAPK, APKM, APKS, ZIP, JPG, PNG, WEBP, and GIF files with search, sort, and category filtering.
- **Immediate Public URLs**: View, copy, and share public CDN links upon upload completion.
- **Android App Builder & GitHub Actions Workflow**: Designed to be exported from Android App Builders, pushed to GitHub, and built automatically into downloadable APK artifacts via GitHub Actions.

---

## 2. Architecture

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

### GitHub Actions CI/CD Pipeline:
```text
Android App Builder
        ↓  (Export Source)
GitHub Repository
        ↓  (git push)
GitHub Actions Workflow (.github/workflows/android-build.yml)
        ↓  (Gradle assembleDebug / assembleRelease)
APK Artifacts
        ↓
Downloadable Build Artifact (cosmo-cloud-manager-debug-apk)
```

---

## 3. Android App Builder Import

1. **Direct Import**: Cosmo Cloud Manager uses standard Android Gradle conventions (AGP 9.1+, Kotlin Compose plugin, Jetpack Compose BOM, Room KSP).
2. Simply open or import the root project directory in your Android App Builder or Android Studio.
3. No proprietary build plugins or server-side build steps are required.
4. You do **not** need Cloudflare credentials during the APK build.

---

## 4. Local Build Instructions

### Prerequisites
- JDK 17 or JDK 21 (Eclipse Temurin recommended)
- Android SDK Platform 36 (build-tools 36.x)

### Commands
```bash
# Clone the repository
git clone https://github.com/your-username/cosmo-cloud-manager.git
cd cosmo-cloud-manager

# Grant execution permission to Gradle Wrapper
chmod +x gradlew

# Build Debug APK
./gradlew assembleDebug --stacktrace

# The generated APK is located at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. GitHub Repository Setup

When pushing this project to GitHub, ensure that:
1. `.gitignore` ignores `build/`, `.gradle/`, and `local.properties`.
2. Gradle Wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`) are committed so GitHub Actions can execute Gradle.
3. Sensitive keys and keystores are **never** committed to Git.

```bash
git init
git add .
git commit -m "Initial commit of Cosmo Cloud Manager"
git branch -M main
git remote add origin https://github.com/your-username/cosmo-cloud-manager.git
git push -u origin main
```

---

## 6. GitHub Actions Workflow

The automated build workflow is configured in `.github/workflows/android-build.yml`.

### Triggers
- Automatic on every `push` to `main` or `master`.
- Automatic on every `pull_request` targeting `main` or `master`.
- Manual trigger via `workflow_dispatch` with an option to select build type (`debug`, `release`, or `both`).

### What the Workflow Does:
1. Checks out the repository.
2. Sets up JDK 17 with Gradle dependency caching.
3. Makes `gradlew` executable.
4. Executes `./gradlew assembleDebug --stacktrace`.
5. Uploads `app/build/outputs/apk/debug/app-debug.apk` as a GitHub Actions build artifact named `cosmo-cloud-manager-debug-apk`.

---

## 7. Downloading the APK Artifact from GitHub

1. Open your repository on GitHub.
2. Click on the **Actions** tab.
3. Click on the latest workflow run (e.g., *"Android CI/CD - Cosmo Cloud Manager"*).
4. Scroll down to the **Artifacts** section at the bottom of the page.
5. Click on **`cosmo-cloud-manager-debug-apk`** to download the ZIP file containing `app-debug.apk`.
6. Transfer or install the APK on any Android phone (Android 7.0 / SDK 24+).

---

## 8. Release Signing & GitHub Secrets

To generate production-signed release APKs automatically:

### 1. Generate an Upload Keystore locally:
```bash
keytool -genkey -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Encode Keystore to Base64:
```bash
# On Linux/macOS:
base64 -w 0 my-upload-key.jks > keystore_base64.txt

# On Windows PowerShell:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks")) | Out-File -Encoding ASCII keystore_base64.txt
```

### 3. Add GitHub Repository Secrets:
Go to your GitHub repository -> **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**:

| Secret Name | Value |
|---|---|
| `KEYSTORE_BASE64` | The entire base64 string from `keystore_base64.txt` |
| `KEYSTORE_PASSWORD` | Password used when creating the keystore |
| `KEY_ALIAS` | `upload` (or your chosen alias) |
| `KEY_PASSWORD` | Password for the key alias |

### 4. Run Release Build:
Go to **Actions** -> Select **Android CI/CD** -> Click **Run workflow** -> Select `release` or `both`.
The signed APK will be uploaded as `cosmo-cloud-manager-release-apk`.

*Note: If release secrets are not configured, GitHub Actions gracefully continues building the debug APK without failing.*

---

## 9. Worker URL Configuration

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

## 10. Cloudflare Worker API Specification

The Android app expects the following endpoints from your Cloudflare Worker:

### `GET /health` or `GET /status`
Returns:
```json
{
  "status": "ok",
  "bucket": "my-r2-bucket",
  "version": "v1"
}
```

### `GET /files`
Returns:
```json
{
  "files": [
    {
      "key": "games/CosmoRacer.apk",
      "size": 157286400,
      "uploaded": 1727827200000,
      "etag": "0123456789abcdef",
      "contentType": "application/vnd.android.package-archive",
      "url": "https://pub.example.com/games/CosmoRacer.apk"
    }
  ]
}
```

### `POST /init`
Initiates a multipart upload.
Request JSON:
```json
{
  "filename": "MyGame.apk",
  "fileSize": 314572800,
  "contentType": "application/vnd.android.package-archive"
}
```
Response JSON:
```json
{
  "uploadId": "ib7q3...",
  "key": "MyGame.apk"
}
```

### `PUT /part?uploadId=...&partNumber=...&key=...`
Uploads a binary chunk.
Headers:
- `X-Upload-Id`: uploadId
- `X-Part-Number`: partNumber
- `X-Key`: key
- `Content-Type`: `application/octet-stream`

Response JSON:
```json
{
  "partNumber": 1,
  "etag": "c01824..."
}
```

### `POST /complete`
Finalizes the multipart upload in R2.
Request JSON:
```json
{
  "uploadId": "ib7q3...",
  "key": "MyGame.apk",
  "parts": [
    { "partNumber": 1, "etag": "etag1" },
    { "partNumber": 2, "etag": "etag2" }
  ]
}
```
Response JSON:
```json
{
  "success": true,
  "url": "https://pub.example.com/MyGame.apk",
  "key": "MyGame.apk",
  "size": 314572800
}
```

### `POST /abort`
Aborts an in-progress multipart upload and cleans up chunks.
Request JSON:
```json
{
  "uploadId": "ib7q3...",
  "key": "MyGame.apk"
}
```

### `DELETE /files/:key` or `POST /delete`
Deletes the object from R2.

---

## 11. Reference Cloudflare Worker Implementation (`worker.js`)

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

## 12. Troubleshooting

| Issue | Cause | Solution |
|---|---|---|
| **Worker Offline / Red indicator** | Incorrect URL or Worker asleep | Go to Settings, verify the Worker URL, and tap "Test Connection (Ping)". |
| **HTTP 401 Unauthorized** | Worker requires an Auth Token | In Settings, enter your Worker Auth Token. |
| **HTTP 413 Payload Too Large** | Chunk size exceeds Cloudflare limits | In Settings, switch the chunk size to 5 MB or 10 MB. |
| **Foreground notification not showing** | Notification permission not granted | Open Android App Info -> Notifications -> Turn notifications ON. |
| **Upload stopped after phone sleep** | Battery optimization killing background process | Android Foreground Service uses `WAKE_LOCK` and `dataSync`; ensure battery optimization is set to "Unrestricted" for large multi-GB transfers. |
| **GitHub Actions build fails on gradlew** | Permission denied | Ensure `chmod +x gradlew` is executed in the repository. |

---

## 13. License
Cosmo Cloud Manager is open source under the MIT License.
