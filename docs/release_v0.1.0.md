# Svita v0.1.0 — release runbook (owner only)

One-time setup (generate the signing key), then per-release build + publish.
The agent never creates or touches the keystore — these are YOUR commands.

---

## Українською

### 1. Одноразово: створіть ключ підпису

Створюйте keystore **поза репозиторієм** (наприклад, `~/keystores/svita/`), щоб
випадково не закомітити його. `.gitignore` уже блокує `*.jks`, `*.keystore`,
`keystore.properties` — але тримайте файл і поза деревом проєкту.

```bash
mkdir -p ~/keystores/svita && cd ~/keystores/svita
keytool -genkeypair -v \
  -keystore svita-release.jks \
  -alias svita-release \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12
```

Паролі: мінімум 20+ символів, з менеджера паролів (не з голови, не з парольних фраз
з інших сервісів). Для PKCS12 пароль ключа = пароль сховища — у `keystore.properties`
вкажіть той самий в обох полях. `keytool` запитає ім'я/організацію — можна
`Svita, Personal project`.

**Резервна копія — обов'язково:** скопіюйте `svita-release.jks` на офлайн-носій
(USB) і в зашифроване сховище. Втрата ключа = неможливість оновити застосунок
для вже встановлених копій.

### 2. Підключіть конфіг (ніколи не комітиться)

```bash
cd /srv/serv/Projects/Svita/svita-public
cp keystore.properties.template keystore.properties
$EDITOR keystore.properties   # storeFile=/home/pixl/keystores/svita/svita-release.jks + паролі
git check-ignore -v keystore.properties   # має підтвердити: проігноровано
```

### 3. Зберіть підписаний APK

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease
ls -la app/build/outputs/apk/release/   # очікуємо app-release.apk (НЕ -unsigned)
```

Якщо бачите `app-release-unsigned.apk` — `keystore.properties` не підхопився;
перевірте шлях `storeFile`.

### 4. Перевірте підпис і чексуми

```bash
APK=app/build/outputs/apk/release/app-release.apk
/home/pixl/Android/Sdk/build-tools/35.0.0/apksigner verify --print-certs "$APK"
sha256sum "$APK" | tee "$APK.sha256"
sha256sum -c "$APK.sha256"        # контроль: OK
keytool -list -v -keystore ~/keystores/svita/svita-release.jks   # валідність до ~2054
```

Додатково: встановіть APK на чистий пристрій/емулятор (`adb install -r "$APK"`),
перевірте перший запуск і створення wardrobe.

### 5. Опублікуйте GitHub release

```bash
gh release create v0.1.0 \
  --title "Svita v0.1.0" \
  --notes-file changelogs/v0.1.0.md \
  "$APK" "$APK.sha256"
```

Після релізу: `git status --short` — впевніться, що `keystore.properties` не
з'явився у статусі (інакше — терміново перевіряти `.gitignore`).

---

## English

### 1. One-time: generate the signing key

Create the keystore **outside the repository** (e.g. `~/keystores/svita/`).
`.gitignore` already blocks `*.jks`, `*.keystore` and `keystore.properties`, but
keep the file out of the project tree anyway.

```bash
mkdir -p ~/keystores/svita && cd ~/keystores/svita
keytool -genkeypair -v \
  -keystore svita-release.jks \
  -alias svita-release \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12
```

Passwords: 20+ characters from a password manager. With PKCS12 the key password
equals the store password — put the same value in both fields of
`keystore.properties`. The distinguished-name prompt can be answered with
`Svita, Personal project`.

**Back up the keystore offline (USB) and in encrypted storage.** Losing it means
you can never update the app for existing installs.

### 2. Wire the config (never committed)

```bash
cd /srv/serv/Projects/Svita/svita-public
cp keystore.properties.template keystore.properties
$EDITOR keystore.properties   # storeFile=/home/pixl/keystores/svita/svita-release.jks + passwords
git check-ignore -v keystore.properties   # must confirm: ignored
```

### 3. Build the signed APK

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease
ls -la app/build/outputs/apk/release/   # expect app-release.apk (NOT -unsigned)
```

If you get `app-release-unsigned.apk`, `keystore.properties` was not picked up;
check the `storeFile` path.

### 4. Verify signature and checksums

```bash
APK=app/build/outputs/apk/release/app-release.apk
/home/pixl/Android/Sdk/build-tools/35.0.0/apksigner verify --print-certs "$APK"
sha256sum "$APK" | tee "$APK.sha256"
sha256sum -c "$APK.sha256"        # must print: OK
keytool -list -v -keystore ~/keystores/svita/svita-release.jks   # validity ~2054
```

Also install on a clean device/emulator (`adb install -r "$APK"`) and check the
first run + wardrobe creation.

### 5. Publish the GitHub release

```bash
gh release create v0.1.0 \
  --title "Svita v0.1.0" \
  --notes-file changelogs/v0.1.0.md \
  "$APK" "$APK.sha256"
```

After publishing: `git status --short` — confirm `keystore.properties` never
appears (if it does, re-check `.gitignore` immediately).

---

## CI note

Without `keystore.properties` on the machine, `./gradlew assembleRelease` still
succeeds and produces an unsigned APK with a build warning — CI stays green with
no secrets in the repo.
