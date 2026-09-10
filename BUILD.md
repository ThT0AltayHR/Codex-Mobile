# Codex Mobile — Derleme Talimatları (GitHub Actions ile)

Bilgisayarın olmadığı için bu proje **GitHub Actions** üzerinden, tamamen
bulutta derlenecek şekilde hazırlandı. Aşağıdaki adımları sırayla takip et.

## 1) GitHub'da yeni bir repo oluştur

- github.com'da yeni, **boş** bir repository oluştur (örn. `codex-mobile`).
- **Private** yapmanı öneririm, çünkü içinde OAuth client ID gibi bilgiler var.

## 2) Git LFS'i etkinleştir (ÇOK ÖNEMLİ)

Bu projenin içinde **251 MB'lık bir native binary** var
(`app/src/main/jniLibs/arm64-v8a/libcodexcore.so`). GitHub, normal git
dosyaları için 100 MB sınırı koyar — bu dosya LFS (Large File Storage)
olmadan **push edilemez**.

Eğer bilgisayarın yoksa ve sadece telefon/tarayıcı üzerinden GitHub
kullanıyorsan, bunu yapmanın en kolay yolu **GitHub Codespaces** veya
**Termux + git** kullanmaktır (Termux zaten telefonunda kurulu):

```bash
# Termux içinde:
pkg install git git-lfs -y
git lfs install

cd codex-mobile   # bu klasörün (zip'ten çıkardığın) içine gir
git init
git lfs track "app/src/main/jniLibs/arm64-v8a/libcodexcore.so"
git add .gitattributes
git add .
git commit -m "İlk sürüm"
git branch -M main
git remote add origin https://github.com/KULLANICI_ADIN/codex-mobile.git
git push -u origin main
```

`.gitattributes` dosyası zaten projede hazır — `git lfs track` komutu onu
tekrar yazacak ama zaten doğru olduğu için sorun çıkarmaz.

**Alternatif**: Eğer Termux'a git kuramıyorsan, GitHub'ın web arayüzünden
"Add file → Upload files" ile yükleyebilirsin, ama 251MB'lık dosya için bu
muhtemelen tarayıcıda zaman aşımına uğrar. Termux + git yolu çok daha
güvenilir.

## 3) Workflow otomatik çalışacak

Repo'ya push ettiğin an, `.github/workflows/build-apk.yml` dosyası GitHub
Actions'ı tetikler ve otomatik olarak:

1. Kodu çeker
2. Git LFS'ten native binary'yi indirir
3. Binary'nin gerçekten indiğini (LFS "pointer" dosyası kalmadığını) doğrular
4. JDK 17 ve Android SDK kurar
5. Gradle wrapper'ı yeniden oluşturur (bu repo'da `gradle-wrapper.jar`
   bulunmuyor çünkü internetsiz ortamda ikili dosya indiremedim — CI bunu
   kendisi indirir, tamamen otomatik)
6. İmzalı release APK'sını `./gradlew assembleRelease` ile derler
7. APK'yı `Codex.apk` adıyla bir **Artifact** ve `Codex Mobile Stable` GitHub Release'i olarak yayınlar

## 4) APK'yı indirme

- GitHub reposunda **Actions** sekmesine git
- En son çalışan workflow'a tıkla ("Build Codex Mobile APK")
- Sayfanın altında **Artifacts** bölümünde `Codex` göreceksin
- Ona tıklayınca bir `.zip` iner, içinde `Codex.apk` var
- Bu APK'yı telefonuna kopyala (Google Drive, kendine mail atma, vs.) ve kur
  (Ayarlar'dan "bilinmeyen kaynaklardan yükleme"ye izin vermen gerekebilir)

## Bilinmesi gerekenler / sınırlamalar

- **Bu bir release APK**'dır. Workflow her derlemede kurulum için imzalı
  `Codex.apk` üretir; Google Play yayın koşulları ayrıca karşılanmalıdır.
- **Sadece arm64-v8a (ARM64) cihazlarda çalışır** — modern Android
  telefonların neredeyse tamamı bu mimaride, dolayısıyla senin telefonun
  (Android 16) sorunsuz çalışır. Çok eski/düşük bütçeli bazı cihazlar
  (32-bit ARM) çalışmayabilir.
- **Font dosyaları eksik**: `ui/theme/Type.kt` içinde not ettiğim gibi,
  italik başlık fontu ve terminal monospace fontu şu an sistem fontlarına
  düşüyor (proje derlensin diye). Gerçek bir `.ttf` eklemek istersen
  `res/font/` klasörüne koyup `Type.kt`'deki yorumları takip etmen yeterli.
- **Codex girişi gerçek OAuth akışıdır** — `auth.openai.com` üzerinden,
  gerçek Codex hesabınla giriş yapacaksın.
- Bu proje **DioNanos/codex-termux** (Davide A. Guglielmi) tarafından
  Android/Termux için portlanmış Codex CLI'ı kullanır — NOTICE dosyasına
  bakabilirsin. Resmi OpenAI mobil uygulaması değildir.

## Sorun giderme

- **"LFS pointer" hatası alırsan**: `git lfs pull` komutunu tekrar çalıştır,
  binary'nin gerçekten indiğinden emin ol (`ls -la` ile boyutuna bak, 250MB
  civarı olmalı, birkaç KB'lık bir metin dosyası değil).
- **Gradle build hatası**: Actions sekmesindeki log'u aç, hangi adımda
  patladığını gösterir — genelde bağımlılık indirme sorunları geçicidir,
  "Re-run jobs" ile tekrar deneyebilirsin.
