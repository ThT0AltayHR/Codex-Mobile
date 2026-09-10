# Codex Mobile

![Android](https://img.shields.io/badge/Android-ARM64-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![License](https://img.shields.io/github/license/ThT0AltayHR/Codex-Mobile?style=for-the-badge)
![Build](https://img.shields.io/github/actions/workflow/status/ThT0AltayHR/Codex-Mobile/build-apk.yml?branch=main&style=for-the-badge&label=APK%20build)
![Stars](https://img.shields.io/github/stars/ThT0AltayHR/Codex-Mobile?style=for-the-badge)

> **Bu proje resmi OpenAI mobil uygulaması değildir.**

Codex Mobile, OpenAI Codex deneyimini Android ARM64 cihazlarda çalıştırmak amacıyla hazırlanmış, üçüncü taraf bir Android portu ve dağıtım projesidir. Proje; Codex oturum açma akışını, yerel sohbet deneyimini ve geliştirici odaklı araçları küçük bir Android uygulamasında bir araya getirir.

Bu depo yalnızca bir APK arşivi değildir. Kaynak kodu, Android arayüzünü, yerel veri katmanını, Codex çalışma zamanını, ayar ekranlarını ve derleme sürecini incelemek isteyen geliştiriciler için açıkça sunulmuştur.

## Geliştiriciyi destekleyin

Bu projeyi faydalı buluyorsanız:

1. Depoya bir **Yıldız (Star)** bırakın.
2. Projeyi geliştiriciye ve ilgilenen kişilere paylaşın.
3. Karşılaştığınız sorunları [Issues](https://github.com/ThT0AltayHR/Codex-Mobile/issues) bölümünde açık ve yeniden üretilebilir şekilde bildirin.
4. Yeni bir özellik veya düzeltme önermeden önce mevcut kaynak kodunu ve `BUILD.md` dosyasını inceleyin.

Bir yıldız, projenin daha fazla geliştirici tarafından fark edilmesine yardımcı olur ve geliştirmeye devam etmek için doğrudan bir destek işaretidir.

## Uygulamada neler var?

- Codex ile Android üzerinden sohbet etme.
- Gerçek Codex hesap oturum açma akışını uygulama içinde başlatma.
- Akış halindeki yanıtları, Markdown metnini ve kod bloklarını okunabilir biçimde gösterme.
- Model seçimi ve akıl yürütme çabası ayarı.
- Sohbet oluşturma, açma, yeniden adlandırma, sabitleme ve silme.
- Sohbet geçmişini cihaz üzerinde saklama.
- Kişiselleştirme ayarları ve her turda kullanılan özel talimatlar.
- Proje hafızası ve `AGENTS.md` tabanlı talimat desteği.
- GitHub bağlantısı için ayrı bir uygulama ekranı.
- Gizli değerleri cihaz üzerinde korumaya yönelik Secret Vault.
- Desen kilidi ve yerel depolama ayarları.
- Dil, tema ve kişisel tercih ekranları.
- Web arama sonuçları için kaynak kartları.
- Android ARM64 cihazlarda çalışan yerel Codex çalışma zamanı.
- GitHub Actions ve Git LFS üzerinden tekrarlanabilir APK derleme süreci.

## APK indir

Test amaçlı mevcut debug APK sürümü:

[**Codex Mobile debug APK'yı indir**](https://github.com/ThT0AltayHR/Codex-Mobile/releases/download/codex-mobile-build-5329c38/codex-mobile-debug.apk)

Bu sürüm `arm64-v8a` mimarisi içindir. Debug APK, Play Store'a yüklenmek üzere imzalanmış bir release paketi değildir. Telefona kurarken Android'in bilinmeyen kaynaklar için verdiği izni etkinleştirmeniz gerekebilir.

## Kaynak kodu ve derleme

Kaynak kodu bu depoda bulunur. Android projesinin derleme adımları için [BUILD.md](BUILD.md) dosyasına bakın.

Proje, yaklaşık 262 MB boyutundaki native ARM64 binary nedeniyle Git LFS kullanır. GitHub Actions workflow'u:

1. Depoyu ve LFS dosyalarını indirir.
2. JDK 17 ve Android SDK'yı hazırlar.
3. Native binary'nin eksiksiz olduğunu doğrular.
4. Lint ve birim testlerini çalıştırır.
5. Debug APK'yı üretir ve Actions artifact'i olarak yükler.

## Kullanım, inceleme ve port politikası

Bu kaynak kodu şeffaflık, eğitim ve inceleme amacıyla paylaşılmıştır. Kaynak kodunu inceleyebilir, uygulamanın nasıl çalıştığını öğrenebilir ve sorunları bildirebilirsiniz.

**Geliştiriciden açık yazılı izin alınmadan üçüncü taraf bir Android/iOS portu oluşturulmaması, projenin yeniden paketlenmemesi, başka bir uygulama adıyla dağıtılmaması ve APK'nın farklı mağazalarda yeniden yayımlanmaması beklenir.** Projenin adı, görselleri, kaynak kodu, native binary'si ve geliştirici atıfları korunmalıdır.

Bu README, proje sahibinin kullanım politikasını açıklar. Deponun hukuki lisans metni [LICENSE](LICENSE) dosyasında, üçüncü taraf atıflar ise [NOTICE](NOTICE) dosyasında yer alır. Lisans kapsamındaki hak ve yükümlülükler için bu dosyalar esas alınmalıdır.

## Atıflar

Bu proje resmi OpenAI Android uygulaması değildir. Codex çalışma zamanı ve Android/Termux portlama çalışmaları için depodaki [NOTICE](NOTICE) dosyasına bakın. OpenAI, Codex Termux ve Ratatui atıfları ilgili lisans koşullarıyla birlikte korunmalıdır.

## Lisans

Bu depo [Apache License 2.0](LICENSE) ile lisanslanmıştır. Kullanımdan, değişiklik yapmadan veya dağıtımdan önce lisans ve NOTICE metinlerini okuyun.

---

Depoya yıldız bırakmayı unutmayın: [ThT0AltayHR/Codex-Mobile](https://github.com/ThT0AltayHR/Codex-Mobile)
