-- Provider Portal runtime settings (feature flags).
-- Adds the two flags that were missing from the settings UI. The "direct claims"
-- flag (DIRECT_CLAIM_SUBMISSION_ENABLED) already exists from V25.
-- Idempotent: ON CONFLICT DO NOTHING.

INSERT INTO feature_flags (flag_key, flag_name, description, enabled, created_by, created_at, updated_at)
VALUES
    (
        'DIRECT_PREAUTH_SUBMISSION_ENABLED',
        'التقديم المباشر للموافقات المسبقة',
        'السماح لمزودي الخدمة بطلب موافقات مسبقة جديدة مباشرة عبر البوابة. يتطلب تفعيل PROVIDER_PORTAL_ENABLED أيضاً.',
        false, 'SYSTEM', NOW(), NOW()
    ),
    (
        'PREAUTH_REQUIRED_SERVICES_ONLY',
        'أصناف بموافقة مسبقة فقط',
        'عند التفعيل تُفلتَر قائمة الخدمات لتشمل فقط الأصناف التي تتطلب موافقة مسبقة؛ وعند التعطيل تُعرض جميع الأصناف.',
        false, 'SYSTEM', NOW(), NOW()
    )
ON CONFLICT (flag_key) DO NOTHING;
