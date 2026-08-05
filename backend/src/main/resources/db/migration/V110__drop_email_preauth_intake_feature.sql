-- ============================================================
-- V110: إزالة ميزة استقبال طلبات الموافقة المسبقة عبر البريد الإلكتروني
-- The email-to-pre-authorization intake pipeline (IMAP polling of an inbox
-- to auto-create pre-authorizations) has been fully discontinued per
-- explicit product decision. This migration drops the tables that backed
-- it (created in V23/V24), which are no longer referenced by any code.
-- Children (tables with FKs) are dropped before their parents.
-- ============================================================

DROP TABLE IF EXISTS pre_auth_email_attachments;
DROP TABLE IF EXISTS pre_auth_email_requests;
DROP TABLE IF EXISTS email_settings;
