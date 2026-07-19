import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resolveApiErrorMessage } from './apiErrorMessage.mjs';

test('prefers messageAr over message when both are present', () => {
  const errorData = { messageAr: 'لا يمكن تقديم مطالبة في الحالة الحالية.', message: 'Cannot submit claim in this status.' };
  assert.equal(resolveApiErrorMessage(errorData, 'fallback'), 'لا يمكن تقديم مطالبة في الحالة الحالية.');
});

test('falls back to message when messageAr is absent', () => {
  const errorData = { message: 'Cannot submit claim in this status.' };
  assert.equal(resolveApiErrorMessage(errorData, 'fallback'), 'Cannot submit claim in this status.');
});

test('falls back to error field when message and messageAr are absent', () => {
  assert.equal(resolveApiErrorMessage({ error: 'Bad request' }, 'fallback'), 'Bad request');
});

test('returns the provided default when errorData has nothing usable', () => {
  assert.equal(resolveApiErrorMessage({}, 'الفشل الافتراضي'), 'الفشل الافتراضي');
  assert.equal(resolveApiErrorMessage(null, 'الفشل الافتراضي'), 'الفشل الافتراضي');
});

test('passes through a plain string errorData as-is', () => {
  assert.equal(resolveApiErrorMessage('حدث خطأ من الخادم', 'fallback'), 'حدث خطأ من الخادم');
});
