/**
 * Account Settings Page - Redirect to Profile Overview
 *
 * Phase C - Profile Simplification
 * All profile functionality consolidated into ProfileOverview
 * This file kept for route compatibility
 *
 * Last Updated: 2024-12-21
 */

import { Navigate } from 'react-router-dom';

// ==============================|| ACCOUNT SETTINGS - REDIRECT ||============================== //

export default function AccountSettings() {
  // Redirect to main profile page
  return <Navigate to="/profile" replace />;
}
