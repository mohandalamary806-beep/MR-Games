# تحديث مستودع MR Games الحالي

1. فك ضغط حزمة MR Games المعدّلة على الكمبيوتر.
2. افتح مستودع GitHub الحالي.
3. ارفع محتويات المجلدات `branding` و`scripts` إلى جذر المستودع. ارفع ملفات Java الثلاثة وسكربت Python داخل `scripts` بالضبط، ولا ترفع `__pycache__`.
4. افتح `.github/workflows/android-apk.yml` في GitHub واضغط Edit، ثم استبدل محتواه بالكامل بمحتوى الملف الجديد الموجود في الحزمة.
5. استبدل `README_AR.md` و`UPSTREAM_LICENSES.md` بالنسخ الجديدة.
6. اترك ملفات المشروع القديمة كما هي؛ الـWorkflow يبني مصدر Winlator في مجلد مؤقت اسمه `engine-src` على GitHub Actions.
7. اضغط Commit changes، ثم افتح Actions وشغّل **Build MR Games Windows Engine APK**.
8. عند النجاح افتح Artifact `MR-Games-Windows-Engine-APK` وحمّل ملف ZIP. بداخله `MR-Games-debug.apk` ومعلومات البناء وملف تغييرات المصدر.

المسارات المطلوبة بعد الرفع:

```text
.github/workflows/android-apk.yml
branding/MR_Games_Icon.png
branding/MR_Games_UI_Concept.png
scripts/apply_mr_games.py
scripts/MRGamesHomeFragment.java
scripts/MRGamePerformance.java
scripts/MRGameLibrary.java
README_AR.md
UPSTREAM_LICENSES.md
```

إذا ظهر خطأ، افتح الـjob الأحمر ثم أول خطوة حمراء، وانسخ رسالة الخطأ كاملة. لا تكرر البناء قبل إصلاحها.


## إضافة لعبة EXE وأزرار التحكم

1. افتح MR Games واضغط **ADD GAME (.EXE)**.
2. اختر ملف التشغيل `.exe` من مدير الملفات.
3. ينشئ التطبيق اختصاراً في مكتبة Winlator وحاوية تشغيل MR Games مخصصة عند الحاجة، ويربط بالاختصار تخطيط تحكم منفصلاً باسم اللعبة.
4. اختر **PLAY NOW** للتشغيل أو افتح **GAME LIBRARY** لتشغيلها لاحقاً.
5. لتعديل الأزرار أو أماكنها، افتح محرر Input Controls الأصلي داخل Winlator واحفظ الملف التعريفي الخاص باللعبة.

**مهم:** لو كان ملف EXE في مجلد اللعبة المشترك، سيبقى في مكانه. أما لو اختير من موقع لا يظهر داخل محركات Winlator، فسيتم نسخ ملف EXE وحده إلى `E:\MR Games Library`. ملفات DLL والبيانات المصاحبة لا تنتقل تلقائياً من اختيار ملف واحد؛ انسخ مجلد اللعبة كاملاً إلى المجلد نفسه عند الحاجة.
