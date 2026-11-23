# پروژه اندروید NeonNet — مرحله اول اصلاح هسته

این پروژه رابط ۱۰ صفحه‌ای اصلی NeonNet را در WebView محلی Android اجرا می‌کند. ظاهر صفحات حفظ شده و پل JavaScript/Native برای درخواست اتصال، قطع اتصال، وضعیت و انتخاب برنامه‌ها وجود دارد.

## تغییرات این مرحله

- ساخت Xray JSON از طریق مبدل رسمی `convertShareLinksToXrayJson` در libXray، به‌جای ساخت دستی JSON مخصوص VLESS.
- افزودن FD تونل Android در فیلد ریشه‌ای `env.xray.tun.fd` مطابق قرارداد API v3.
- اتصال TUN اندروید به Xray و تعیین `autoOutboundsInterface` با نام رابط فیزیکی اینترنت برای جلوگیری از حلقه مسیریابی.
- صف تک‌نخی برای سریال‌کردن شروع/توقف موتور و جلوگیری از شروع هم‌زمان چند نمونه.
- بررسی وضعیت `getXrayState` قبل از اعلام اتصال موفق.
- پذیرش لینک‌های VLESS، VMess، Trojan، Shadowsocks، SOCKS و Hysteria2/Hy2 در رابط؛ اعتبارسنجی نهایی هر لینک را libXray انجام می‌دهد.
- به‌روزرسانی دوره‌ای وضعیت داشبورد از Native.

## محدوده واقعی parser فعلی

پشتیبانی لینک‌ها تابع نسخه AAR رسمی libXray است. مستندات libXray نسخه API v3 موارد VMess AEAD، VLESS، Hysteria2، Shadowsocks، SOCKS و Trojan را در parser share-link معرفی می‌کند. فرمت قدیمی `vmess://Base64(JSON)` در parser رسمی فعلی پشتیبانی نمی‌شود. کانفیگ‌های Clash/Mihomo نیز ورودی مستقیم این parser نیستند.

## Build

Workflow موجود در `.github/workflows/build-apk.yml` فایل AAR نسخه `v26.11.9` را از Release رسمی دریافت می‌کند و سپس `assembleDebug` را اجرا می‌کند. برای ساخت محلی باید AAR سازگار در `app/libs/libXray.aar` قرار بگیرد.

## محدودیت تست این تحویل

در محیط فعلی امکان دریافت Gradle از `services.gradle.org` به دلیل خطای DNS وجود نداشت؛ بنابراین این نسخه در این محیط Build نشده و روی گوشی اجرا/تست نشده است. تغییرات سورس انجام شده‌اند، اما هنوز باید Build در GitHub Actions و تست اتصال واقعی روی Android انجام شود. `testXray` فقط ساختار/ساخت نمونه Core را بررسی می‌کند و به‌تنهایی اتصال شبکه را اثبات نمی‌کند.

## مواردی که هنوز تکمیل نشده‌اند

- Subscription URL، Base64 subscription و واردکردن JSON کامل
- VMess قدیمی QR/Base64 JSON
- ذخیره امن کانفیگ‌ها و مدیریت کامل چند Subscription
- تست تأخیر گروهی با `pingBatch`
- آمار واقعی ترافیک و تست throughput
- اتصال کامل تمام گزینه‌های DNS، Routing و تنظیمات پیشرفته رابط به تنظیمات Xray
- ثبت لاگ زنده و مدیریت چرخه عمر شبکه/تغییر Wi-Fi به دیتای موبایل
- بررسی و تست runtime مربوط به DNS و Socket protection روی دستگاه واقعی

## منابع فنی

- libXray API: https://github.com/XTLS/libXray/blob/main/README.md
- Xray TUN: https://xtls.github.io/en/config/inbounds/tun.html
- Android VpnService: https://developer.android.com/develop/connectivity/vpn
