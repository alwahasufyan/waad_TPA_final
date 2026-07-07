package com.waad.tba.modules.medicaltaxonomy.enums;

/**
 * Clinical context for a Medical Category.
 *
 * <p>Defines where (clinically) a category of services is delivered.
 * Used by benefit-policy rules to differentiate coverage by care setting.
 *
 * <p>Mapping to Arabic terms used in benefit tables:
 * <ul>
 *   <li>INPATIENT  → إيواء داخل المستشفى</li>
 *   <li>OUTPATIENT → عيادات خارجية</li>
 *   <li>OPERATING_ROOM → عمليات (جراحية / تداخلية)</li>
 *   <li>EMERGENCY  → طوارئ وإسعاف</li>
 *   <li>SPECIAL    → منافع خاصة (مزمن، نفسي، إخلاء…)</li>
 *   <li>ANY        → أي سياق (لا قيد)</li>
 * </ul>
 */
public enum CategoryContext {

    /** إيواء — admitted/hospitalised patient */
    INPATIENT,

    /** عيادات خارجية — ambulatory/clinic visit */
    OUTPATIENT,

    /** عمليات — surgical or interventional procedure in an OR/cath lab */
    OPERATING_ROOM,

    /** طوارئ — emergency or ambulance services */
    EMERGENCY,

    /** منافع خاصة — chronic disease drugs, psychiatric, evacuation, etc. */
    SPECIAL,

    /** أي سياق — applies regardless of care setting (default / fallback) */
    ANY
}
