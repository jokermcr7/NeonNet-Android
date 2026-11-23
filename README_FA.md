# پروژه اندروید NeonNet — نسخه 1.0.0

این پروژه شامل هر ۱۰ صفحه HTML اصلی است و آن‌ها را در WebView محلی Android اجرا می‌کند. رابط کاربری اصلی حفظ شده و یک لایه مشترک برای ناوبری، ذخیره کانفیگ‌ها، ورود لینک VLESS، ویرایش/حذف، اشتراک‌گذاری، اسکن QR با `BarcodeDetector` (در WebViewهایی که پشتیبانی می‌کنند) و ارتباط با کد Native اضافه شده است.

## موتور VPN
موتور انتخاب‌شده **Xray-core از طریق libXray رسمی XTLS** است؛ این انتخاب برای کانفیگ VLESS + Reality انجام شده. پیاده‌سازی Android از `VpnService` یک TUN می‌سازد، رابط فیزیکی اینترنت را پیش از ساخت VPN تشخیص می‌دهد، `autoOutboundsInterface` را برای جلوگیری از حلقهٔ مسیریابی تنظیم می‌کند و FD را پیش از اجرای Xray در `XRAY_TUN_FD` قرار می‌دهد.

برای فعال‌شدن واقعی موتور، باید AAR رسمی libXray را به این مسیر اضافه کنی:

`app/libs/libXray.aar`

مخزن رسمی و راهنمای ساخت: https://github.com/XTLS/libXray
نسخهٔ مورد بررسی در زمان ساخت پروژه: `v26.11.9` (API version 3). خود پروژه AAR را به‌صورت باینری داخل ZIP قرار نمی‌دهد؛ باید آن را از Release رسمی دریافت کنی یا با دستور `python3 build/main.py android` در مخزن رسمی بسازی. به‌دلیل اینکه API رسمی تضمین پایداری ندارد، نسخه AAR و کد adapter را با هم نگه دار و جداگانه ارتقا بده.

## ساخت APK
1. ZIP را Extract کن.
2. Android Studio را باز کن و پوشه `NeonNetAndroid` را Open کن.
3. AAR رسمی libXray را در `app/libs/` قرار بده.
4. Gradle Sync را انجام بده.
5. از Build > Build APK(s) خروجی بگیر.

خروجی معمول:
`app/build/outputs/apk/debug/app-debug.apk`

## نکته‌های مهم و محدودیت‌های این خروجی
- محیط تولید فعلی Android SDK/Gradle نصب ندارد؛ بنابراین APK در این محیط ساخته و روی دستگاه تست نشده است. ZIP شامل سورس پروژه Android Studio است.
- بدون `libXray.aar`، رابط برنامه و بخش ذخیره/مدیریت کانفیگ کار می‌کند، اما اتصال VPN عمداً موفق اعلام نمی‌شود.
- پشتیبانی فعلی parser و سازنده کانفیگ روی VLESS لینک‌محور متمرکز است (TCP/WS/gRPC و TLS/Reality). کانفیگ‌های JSON دلخواه و subscriptionهای اختصاصی ممکن است به توسعه parser جدا نیاز داشته باشند.
- دوربین QR به پشتیبانی `BarcodeDetector` در نسخه WebView دستگاه وابسته است. در صورت نبود آن، لینک را از کلیپ‌بورد وارد کن.
- مقادیر پینگ، ترافیک و تست سرعتی که در طرح اولیه ثابت بودند، نباید به‌عنوان آمار زنده تلقی شوند. تست سرعت فعلی فقط زمان پاسخ تقریبی را گزارش می‌کند و آزمون واقعی throughput نیست.
- Kill switch سراسری باید از تنظیمات Android (Always-on VPN و Block connections without VPN) فعال شود؛ برنامه این گزینه سیستمی را به‌جای کاربر تغییر نمی‌دهد.
- گزینه‌های FakeDNS، Port Hopping و تنظیمات پیشرفته‌ای که در نسخه اولیه فقط ظاهری بودند، به‌عنوان قابلیت عملیاتی تضمین نشده‌اند.

## ساختار
- `app/src/main/assets/www/`: ده صفحه HTML
- `MainActivity.kt`: WebView، مجوز VPN و پل JS/Native
- `NeonVpnService.kt`: Android VpnService و مدیریت TUN
- `XrayConfig.kt`: ساخت Xray JSON برای VLESS
- `LibXray.kt`: فراخوانی API رسمی Invoke
- `AppCatalog.kt`: فهرست اپ‌های نصب‌شده

## منابع فنی
- XTLS/libXray: https://github.com/XTLS/libXray
- Xray TUN Android: https://github.com/XTLS/Xray-core/blob/main/proxy/tun/README.md

## وضعیت تحویل
این یک پروژهٔ سورس Android Studio است، نه APK امضاشده. در محیط فعلی Android SDK و ابزار Gradle موجود نبود؛ همچنین فایل باینری AAR در ZIP قرار نگرفته است. بنابراین ادعای ساخت یا تست موفق APK نمی‌کنم. پس از قرار دادن AAR رسمی و بازکردن پروژه در Android Studio می‌توان آن را Build کرد.

**مهم:** Xray برای جلوگیری از برگشت سوکت‌های خودش به TUN باید روی رابط فیزیکی bind شود؛ مستندات Xray صراحتاً این مورد را برای Android لازم می‌دانند. اگر رابط فیزیکی قابل تشخیص نباشد، سرویس اتصال را متوقف می‌کند و تونل ناقص را روشن نگه نمی‌دارد.
