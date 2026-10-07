# 🖥️ ENVIRONMENT.md — Lingkungan & Toolchain `kantin-be`

> **Tujuan:** Supaya setiap anggota tim (dan agen AI) punya lingkungan **identik** dengan `admin-be`, dan tidak ada yang terjebak "di mesin saya jalan". Dokumen ini merujuk **versi teruji** pada 1 Okt 2026.

---

## 1. Versi Teruji (Reference Build)

Versi yang **diverifikasi jalan** di lingkungan pengembangan referensi:

| Komponen | Versi teruji | Sumber |
|---|---|---|
| **JDK** | **Temurin 25+36 LTS** (`openjdk version "25" 2025-09-16 LTS`) | `~/.sdkman/candidates/java/25-tem` |
| **Maven** | **3.9.12** via wrapper (`distributionType=only-script`) | `.mvn/wrapper/maven-wrapper.properties` |
| Docker Engine | 29.8.0 | daemon lokal |
| Docker Compose | 5.5.1 | plugin |
| PostgreSQL (client) | 18.6 | opsional |
| Node.js / npm | 26.8.2 / 11.16.0 | untuk `kantin-fe` |
| Git | 2.55.0 | — |

Container acuan (harus sama dengan `admin-be`):
- Build stage: `azul/zulu-openjdk-alpine:25` + `apk add maven tzdata ttf-dejavu`
- Runtime stage: `azul/zulu-openjdk-alpine:25` + `tzdata ttf-dejavu curl`
- `ENV TZ=Asia/Jakarta`

---

## 2. Aturan Wajib (Non-Negotiable)

1. **JDK = 25.** Jangan 21, jangan 26. `pom.xml` memakai `<java.version>25</java.version>` yang identik dengan `admin-be`.
2. **Jangan install Maven global.** Selalu `./mvnw ...`.
3. **Jangan ubah file wrapper** (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`) tanpa ADR.
4. **Timezone `Asia/Jakarta`** untuk container & DB (logika reset limit harian mengikuti zona sekolah).
5. **`.sdkmanrc` di root repo** (bila memakai SDKMAN) — memaksa `sdk env` memilih `25-tem`.

---

## 3. Verifikasi Cepat (jalankan sebelum mulai)

```bash
java -version          # → openjdk version "25"
echo $JAVA_HOME        # → .../candidates/java/current  (atau path JDK 25)
./mvnw --version       # → "Java version: 25, vendor: Eclipse Adoptium"
docker --version
docker compose version
```

**Semua baris harus benar. Kalau `java -version` masih 21 → baca §4.**

---

## 4. Memasang & Mengaktifkan JDK 25

### 4.1 Cara disarankan — SDKMAN (Linux/macOS/WSL)

```bash
# 1. Install SDKMAN (kalau belum ada)
curl -s "https://get.sdkman.io" | bash

# 2. Install JDK 25 Temurin
sdk install java 25-tem

# 3. Jadikan default
sdk default java 25-tem
sdk use java 25-tem

# 4. Verifikasi
java -version    # → openjdk version "25"
```

Buat **`.sdkmanrc`** di root repo agar proyek ini otomatis memilih JDK 25:
```properties
java=25-tem
```
Lalu setiap masuk folder proyek: `sdk env`.

### 4.2 Debian/Ubuntu — apt (alternatif)

```bash
sudo apt update
sudo apt install -y wget apt-transport-https gpg
# Tambahkan repo Adoptium sesuai panduan resmi, lalu:
sudo apt install -y temurin-25-jdk
sudo update-alternatives --config java   # pilih 25
```

### 4.3 macOS — Homebrew (alternatif)

```bash
brew install --cask temurin@25
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

### 4.4 Shell `fish` — ⚠️ jebakan umum

**Gejala:** `java -version` menampilkan **21**, padahal SDKMAN sudah di-source di `.bashrc`/`.zshrc`. Penyebab: shell `fish` **tidak** membaca `.bashrc`

**Solusi** — tambahkan ke `~/.config/fish/user.fish` (atau `config.fish`):

```fish
# ~/.config/fish/user.fish — Java / SDKMAN
if test -d "$HOME/.sdkman/candidates/java/current"
    set -gx SDKMAN_DIR "$HOME/.sdkman"
    set -gx JAVA_HOME "$HOME/.sdkman/candidates/java/current"
    fish_add_path --prepend "$JAVA_HOME/bin"
end
```

Lalu **buka terminal baru** dan cek `java -version` → harus 25.
(Bagi pengguna Bash/Zsh: pastikan blok `sdkman-init.sh` ada di `.bashrc`/`.zshrc`.)

> Catatan: terminal & IDE yang **sedang terbuka** perlu di-restart agar variabel baru terpakai.

### 4.5 Cek JDK lain yang mungkin "mencuri" PATH

```bash
# Linux
ls -d /usr/lib/jvm/* /opt/java/* ~/.sdkman/candidates/java/* 2>/dev/null
# PATH mana yang menang?
which -a java
```

Pastikan `~/.sdkman/candidates/java/current/bin/java` berada **paling depan** di `PATH`.

---

## 5. Struktur Wrapper yang Wajib Ada di Repo

Disalin **apa adanya** dari `admin-be`:

```
kantin-be/
├── mvnw                                  # executable (chmod +x)
└── .mvn/
    └── wrapper/
        └── maven-wrapper.properties
```

Isi `maven-wrapper.properties` (acuan `admin-be`):
```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.12/apache-maven-3.9.12-bin.zip
```

> `distributionType=only-script` berarti Maven diunduh **sekali** ke `~/.m2/wrapper/dists/` — tidak ada JAR wrapper yang di-commit. Ringan & cepat.

---

## 6. Infrastruktur Dev (Docker)

Target `docker-compose.yml` (dibuat di Fase scaffold):
- **PostgreSQL 18** — database `kantin_db` (terpisah dari admin-be), user/pass dari `.env` lokal
- **Redis 7** — cache, blacklist token, rate limit

```bash
docker compose up -d
docker compose ps      # pastikan keduanya healthy
```

**Waktu & zona:** set `TZ=Asia/Jakarta` pada service, agar konsisten dengan app.

> **Topologi DB (ADR-0010 / Q11).** DB kantin **terpisah** dari admin-be (ADR-0001).
> **Dev/CI** boleh satu host (seperti `docker-compose` di atas) — yang penting
> **nama DB & kredensial berbeda**. **Produksi** sebaiknya **server PostgreSQL
> terpisah** (isolasi failure domain/resource). Kantin-be memeriksa ini saat start
> lewat `kantin.topologi.*`: `enforce=true` **menggagalkan start** bila datasource
> menunjuk DB admin-be; `host-db-admin-be` memicu peringatan bila host sama.

---

## 7. Config Aplikasi

- **Jangan pernah commit** `application-local.properties` (berisi kredensial).
- Yang di-commit hanya `application-local.properties.example` (template).
- Secret (JWT public key, DB pass, MinIO key) → **environment variable**, bukan hardcode.

```bash
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
```

---

## 8. Masalah Umum & Solusinya

| Gejala | Penyebab | Solusi |
|---|---|---|
| `java -version` = 21 | JDK 25 tidak di PATH / selain fish tak ter-load | §4.4 / §4.5, restart terminal |
| `mvn: command not found` | Maven global tidak dipasang | gunakan `./mvnw` |
| `./mvnw` menolak jalan / build gagal | Maven memakai JDK 21 | pastikan `JAVA_HOME` = JDK 25, ulang `./mvnw --version` |
| `invalid target release: 25` | Maven pakai JDK < 25 | §4, lalu restart IDE/terminal |
| Build di container gagal | image belum `25` | pastikan Dockerfile `azul/zulu-openjdk-alpine:25` |
| Waktu di DB beda | TZ belum diset | set `TZ=Asia/Jakarta` di app & container |
| IDE pakai JDK lain | project SDK belum di-set | set Project SDK = JDK 25 |

---

## 9. Ringkasan Satu Baris

> **JDK 25 (Temurin) + `./mvnw` (Maven 3.9.12) + Docker/PG 18/Redis 7 + TZ Asia/Jakarta — apa pun OS-nya, hasil `./mvnw --version` harus menyebut `Java version: 25`.**
