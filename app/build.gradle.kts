name: Build Android APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build-apk:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '9.3.1'

      - name: Inject API Secrets
        env:
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}
          LIVEKIT_TOKEN_SERVER_ID: ${{ secrets.LIVEKIT_TOKEN_SERVER_ID }}
        run: |
          echo "GEMINI_API_KEY=$GEMINI_API_KEY" >> local.properties
          echo "LIVEKIT_TOKEN_SERVER_ID=$LIVEKIT_TOKEN_SERVER_ID" >> local.properties
          printf "GEMINI_API_KEY=%s\nLIVEKIT_TOKEN_SERVER_ID=%s\n" "$GEMINI_API_KEY" "$LIVEKIT_TOKEN_SERVER_ID" > .env

      - name: Prepare keystore & Firebase resource, then build APK
        env:
          DEBUG_KEYSTORE_B64: ${{ secrets.DEBUG_KEYSTORE_B64 }}
        run: |
          sed -i '/foojay-resolver-convention/d' settings.gradle.kts

          if [ -n "$DEBUG_KEYSTORE_B64" ]; then
            echo "$DEBUG_KEYSTORE_B64" | base64 -d > debug.keystore
          elif [ ! -f debug.keystore ]; then
            keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
          fi

          echo "=== YOUR SHA-1 FINGERPRINT ==="
          keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android | grep "SHA1:"
          echo "=== YOUR DEBUG_KEYSTORE_B64 ==="
          base64 -w 0 debug.keystore
          echo ""

          CLIENT_ID="000000000000-placeholder.apps.googleusercontent.com"
          if [ -f app/google-services.json ]; then
            EXTRACTED=$(python3 -c 'import json; d=json.load(open("app/google-services.json")); c=[o["client_id"] for x in d.get("client",[]) for o in x.get("oauth_client",[]) if o.get("client_type")==3]; print(c[0] if c else "")')
            if [ -n "$EXTRACTED" ]; then
              CLIENT_ID="$EXTRACTED"
            fi
          fi

          mkdir -p app/src/main/res/values
          cat << EOF > app/src/main/res/values/firebase_placeholder.xml
          <?xml version="1.0" encoding="utf-8"?>
          <resources>
              <string name="default_web_client_id" translatable="false">$CLIENT_ID</string>
          </resources>
          EOF

          gradle assembleDebug

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
