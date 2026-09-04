# محمّل يوتيوب (YTPlaylistDownloader)

تطبيق أندرويد بـ Kotlin لتحميل فيديوهات يوتيوب المفردة أو قوائم تشغيل كاملة، باستخدام
[youtubedl-android](https://github.com/yausername/youtubedl-android) (غلاف حول yt-dlp).

## البنية المعمارية

```
Application (init لمكتبة yt-dlp/ffmpeg مرة واحدة فقط)
    → MainActivity (إدخال الرابط)
    → PlaylistExtractorViewModel (استخراج --flat-playlist، فحص التكرار بـ Room)
    → PreviewFragment (اختيار الفيديوهات + الجودة الموحدة)
    → DownloadForegroundService (تحميل متسلسل، Coroutine على Dispatchers.IO)
    → تخزين مؤقت داخل التطبيق → عند الاكتمال → نسخ إلى MediaStore (Movies/)
    → تحديث الحالة بقاعدة بيانات Room (PENDING/DOWNLOADING/DONE/FAILED/CANCELLED)
```

قرارات معمارية مقصودة (وأسبابها):
- **Foreground Service بدل WorkManager**: التحميل عملية طويلة synchronous، وبعض الشركات المصنعة
  (Samsung/Xiaomi) بتقتل WorkManager بالخلفية.
- **تخزين مؤقت داخلي ثم نقل لـ MediaStore بعد الاكتمال**: خاصية `--continue` (استئناف التحميل)
  بتحتاج مسار ملف مباشر تقدر تتحقق من حجمه، وهاد مش متوفر بسهولة عبر MediaStore أثناء التحميل.
- **`--progress-template` مخصص**: بدل ما نعتمد على parsing نص حر من output الأداة (هش وعرضة للكسر
  عند تحديث yt-dlp).
- **Dedup عبر `OnConflictStrategy.IGNORE`** على `videoId` كـ Primary Key بجدول Room.

## البناء

المشروع مُعد للبناء عبر GitHub Actions (`.github/workflows/build.yml`) بدون الحاجة لبيئة أندرويد
محلية — بيولّد APK قابل للتحميل من تبويب Actions بعد كل push لفرع `main`.

للبناء محليًا (لو بدك):
```bash
gradle assembleDebug
```

> ملاحظة: ملف `gradlew` (الـ wrapper) غير مضمّن هون لأنو بيحتاج JAR ثنائي. الـ workflow بيستخدم
> `gradle/actions/setup-gradle` اللي بيثبّت Gradle مباشرة، فما في داعي له على GitHub Actions.
> لو بدك `./gradlew` محليًا، شغّل `gradle wrapper` مرة وحدة داخل المشروع.

## ملاحظة قانونية

تحميل فيديوهات يوتيوب بشكل عام يخالف شروط استخدام يوتيوب، حتى للاستخدام الشخصي. هاد التطبيق
مخصص لأغراض مشروعة فقط: محتواك الخاص، فيديوهات مرخصة صراحة للتحميل، أو محتوى Creative Commons.
