# VISITS-DOCUMENTS-ADMIN-REVIEWER-FIX-1

## الحالة

READY FOR REVIEW — لم يتم commit أو push.

## حالة Git الأولية

كان الفرع `medical-dictionary-remediation` يحتوي على تغييرات غير مرتبطة بالقاموس والتنقل والواجهة، وملفات مولدة غير متتبعة. لم يتم حذفها أو تضمينها في هذه المهمة.

## ما تمت مراجعته

- `VisitController`, `VisitService`, `VisitRepository` و`VisitAttachmentController/Service`.
- `AuthorizationService` و`ProviderContextGuard` و`ReviewerProviderIsolationService`.
- `ClaimAttachmentController` ومسارات مرفقات المطالبات.
- صفحات `VisitsList` و`VisitView` و`ProviderVisitLog` و`ProviderDocuments` وخدمات API الخاصة بها.
- مسارات `MainRoutes` ونافذة System Categories.

## السبب والإصلاح

1. قائمة الزيارات الإدارية كانت لا تطبق عزل مقدمي الخدمة للمراجع الطبي؛ أضيفت استعلامات visits حسب قائمة مقدمي الخدمة المسندين للمراجع.
2. فتح تفاصيل الزيارة كان يمر عبر `AuthorizationService.canAccessVisit` الذي لا يعالج `MEDICAL_REVIEWER`؛ أصبح التحقق يستخدم `ReviewerProviderIsolationService` للمراجع فقط، مع بقاء SUPER_ADMIN/ACCOUNTANT دون قيود.
3. مرفقات الزيارات كانت تتحقق من ملكية مقدم الخدمة فقط. أضيف تحقق reviewer-provider isolation قبل العرض والتنزيل، مع إبقاء Provider Staff مقيداً بمقدم الخدمة الخاص به.
4. لا يوجد دور `ADMIN` مستقل في `SystemRole`; لا تمت إضافته. صلاحيات الإدارة الحالية هي `SUPER_ADMIN` و`ACCOUNTANT` وفق بنية المشروع.
5. صفحة `ProviderDocuments` مقيدة عمداً بـ `PROVIDER_STAFF` ولا تُعرض كوثائق إدارية للمراجع؛ وثائق المطالبات والزيارات تُقرأ من endpoints الخاصة بالكيان مع التحقق الأمني.

## السلوك بعد الإصلاح

- SUPER_ADMIN: جميع الزيارات والتفاصيل والمرفقات.
- ACCOUNTANT: نطاق الإدارة الحالي كما هو.
- MEDICAL_REVIEWER: الزيارات والمرفقات لمقدمي الخدمة المسندين فقط.
- PROVIDER_STAFF: زيارات ومرفقات مقدم الخدمة المرتبط فقط.
- EMPLOYER_ADMIN: نطاق صاحب العمل حيث تدعمه الخدمة.
- لا توجد صلاحية ADMIN مستقلة في النظام الحالي.

## الملفات المعدلة لهذه التذكرة

- `backend/src/main/java/com/waad/tba/modules/visit/repository/VisitRepository.java`
- `backend/src/main/java/com/waad/tba/modules/visit/service/VisitService.java`
- `backend/src/main/java/com/waad/tba/modules/visit/controller/VisitAttachmentController.java`

## التحقق

- Backend Maven compile and test-compile: PASS.
- Focused `VisitServiceTest` and `VisitAttachmentControllerAuthorizationTest`: PASS.
- Frontend ESLint على الملفات المتأثرة: PASS؛ توجد تحذيرات Prettier/unused موجودة مسبقاً، ولا توجد أخطاء lint.
- Frontend production build: PASS في آخر إعادة بناء للواجهة.
- `git diff --check`: لا توجد أخطاء whitespace؛ ظهرت تحذيرات CRLF المعتادة فقط.
- Docker image build: PASS. بعد إعادة تشغيل الصورة الأخيرة كان التطبيق يمر بفحص Flyway طويل (المسح التفصيلي للترحيلات) وتجاوز مهلة `waad.ps1 health` قبل اكتمال startup؛ لم يظهر استثناء من التعديلات الجديدة. كانت صحة backend/frontend/engine مؤكدة في إعادة البناء السابقة.
- لم يتم تنفيذ اختبار HTTP حي بحساب مراجع لعدم توفر fixture مؤكدة لمراجع ومقدمين مسندين في قاعدة التطوير.

## المستندات والتنزيل

مسارات claim/visit attachments تتحقق من ارتباط المرفق بالكيان وتعيد 404 عند عدم التطابق، وتستخدم الاستجابة المصادق عليها. مرفقات الملفات غير القابلة للمعاينة تُنزّل كمرفق، ولا تم تغيير نظام التخزين.

## Git والتنظيف

لم يتم staging أو commit أو push. بقيت تغييرات القاموس والتنقل والملفات المولدة و`.recovery`/env (إن وجدت) خارج نطاق هذه التذكرة ولم تُلمس.

## rollback

يمكن التراجع عن هذه المهمة بإعادة الملفات الثلاثة أعلاه إلى نسختها السابقة؛ لا توجد migration أو تغييرات بيانات.
