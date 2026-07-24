// ══════════════════════════════════════════════════════════════════════════════
// CONSTANTS & LABELS — extracted verbatim from the pre-Phase-3B monolith
// (ProviderClaimsSubmission.jsx) so the recovered Stage 3A/3B hook and
// components (which import from here) use the exact same, already-live
// Arabic wording — no new/invented labels.
// ══════════════════════════════════════════════════════════════════════════════
export const LABELS = {
  pageTitle: 'إنشاء مطالبة',
  pageSubtitle: 'تقديم مطالبة تأمينية من سجل الزيارات',
  visitRequired: 'يجب الوصول لهذه الصفحة من سجل الزيارات',
  visitInfo: 'بيانات الزيارة',
  memberInfo: 'بيانات المؤمن عليه',
  serviceLines: 'الخدمات الطبية المطالب بها',
  addService: 'إضافة خدمة',
  selectCategory: 'التصنيف الطبي',
  selectService: 'الخدمة الطبية',
  quantity: 'الكمية',
  unitPrice: 'سعر الوحدة',
  totalPrice: 'الإجمالي',
  noContract: 'لا يوجد عقد لهذه الخدمة',
  preAuth: 'الموافقة المسبقة (اختياري)',
  selectPreAuth: 'اختر موافقة مسبقة',
  noPreAuth: 'لا توجد موافقات مسبقة متاحة',
  preAuthOptional: 'اختياري - يمكن ربط المطالبة بموافقة مسبقة إن وجدت',
  diagnosis: 'بيانات التشخيص',
  diagnosisCode: 'رمز التشخيص (ICD-10)',
  diagnosisCodeRequired: 'رمز التشخيص (ICD-10) مطلوب',
  diagnosisDescription: 'وصف التشخيص',
  notes: 'ملاحظات طبية',
  saveDraft: 'حفظ كمسودة',
  savingDraft: 'جاري حفظ المسودة...',
  submitFinal: 'تقديم نهائي للمراجعة',
  submittingFinal: 'جاري التقديم النهائي...',
  cancel: 'إلغاء',
  back: 'رجوع',
  totalClaimAmount: 'إجمالي المطالبة',
  remainingLimit: 'الحد المتبقي',
  annualLimit: 'الحد السنوي',
  usedAmount: 'المستخدم',
  attachments: 'المرفقات والمستندات',
  attachmentHint: 'يمكنك إرفاق التقارير الطبية، الفواتير، أو المستندات الداعمة',
  selectFiles: 'اختر ملفات للرفع',
  uploadingFiles: 'جاري رفع الملفات...',
  coverageInfo: 'معلومات التغطية'
};

export const VISIT_TYPE_LABELS = {
  OUTPATIENT: 'عيادة خارجية',
  INPATIENT: 'تنويم',
  EMERGENCY: 'طوارئ',
  DENTAL: 'أسنان',
  OPTICAL: 'بصريات',
  DAY_CARE: 'رعاية يومية'
};

export const MAX_UPLOAD_SIZE_MB = 10;
export const MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024;
// DOCUMENTS-INTEGRITY-1: must match the backend's single canonical allow-list
// (AttachmentFileTypePolicy) exactly — GIF was previously accepted here but
// rejected server-side (guaranteed 400 on upload), and XLS/XLSX were never
// accepted anywhere despite being a real, needed attachment type.
export const ALLOWED_FILE_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png', 'doc', 'docx', 'xls', 'xlsx'];
export const FILE_ACCEPT_ATTR = '.pdf,.jpg,.jpeg,.png,.doc,.docx,.xls,.xlsx';
