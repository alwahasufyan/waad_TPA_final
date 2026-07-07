import requests

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/v1/auth/login"
ELIGIBILITY_URL = f"{BASE_URL}/api/v1/eligibility/remaining-limit"
USERNAME = "superadmin@tba.sa"
PASSWORD = "Admin@123"
TIMEOUT = 30


def test_tc005_get_eligibility_remaining_limit_with_valid_and_invalid_token():
    # Step 1: Login to get a valid JWT token
    login_payload = {
        "identifier": USERNAME,
        "password": PASSWORD
    }
    headers = {"Content-Type": "application/json"}
    try:
        login_resp = requests.post(LOGIN_URL, json=login_payload, headers=headers, timeout=TIMEOUT)
        assert login_resp.status_code == 200, f"Login failed with status {login_resp.status_code}"
        login_json = login_resp.json()
        assert "data" in login_json and "token" in login_json["data"], "Token not found in login response"
        valid_token = login_json["data"]["token"]
    except Exception as e:
        raise AssertionError(f"Login request failed or invalid response: {e}")

    # Use a sample member identifier for eligibility check
    # Since no explicit member identifier is provided, use a common default or from login user object if exists
    member_identifier = None
    try:
        user_obj = login_json["data"].get("user", {})
        member_identifier = user_obj.get("memberId") or user_obj.get("memberIdentifier")
    except Exception:
        pass
    if not member_identifier:
        # fallback to a hardcoded common default identifier if known, else fail test early
        # Since not provided, try "1234567890" as a dummy member ID for test purpose
        member_identifier = "1234567890"

    # Step 2: Call /api/v1/eligibility/remaining-limit with valid token and member identifier
    valid_headers = {
        "Authorization": f"Bearer {valid_token}"
    }
    params = {"memberIdentifier": member_identifier}
    try:
        eligibility_resp = requests.get(ELIGIBILITY_URL, headers=valid_headers, params=params, timeout=TIMEOUT)
        assert eligibility_resp.status_code == 200, f"Expected 200 for valid token, got {eligibility_resp.status_code}"
        eligibility_json = eligibility_resp.json()
        # Validate presence of FinancialSummaryDto fields (generic check)
        # We expect a JSON object with some keys related to financial summary,
        # the PRD does not specify the exact schema fields, so do basic checks:
        assert isinstance(eligibility_json, dict), "Response is not a JSON object"
        # Check some common fields if present (e.g., remaining limit or amounts). We'll check any keys exist.
        assert len(eligibility_json) > 0, "FinancialSummaryDto response is empty"
    except Exception as e:
        raise AssertionError(f"GET eligibility with valid token failed: {e}")

    # Step 3: Call with invalid/expired token (use an obviously invalid token)
    invalid_headers = {
        "Authorization": "Bearer INVALID_OR_EXPIRED_TOKEN"
    }
    try:
        invalid_resp = requests.get(ELIGIBILITY_URL, headers=invalid_headers, params=params, timeout=TIMEOUT)
        assert invalid_resp.status_code == 401, f"Expected 401 for invalid token, got {invalid_resp.status_code}"
    except Exception as e:
        raise AssertionError(f"GET eligibility with invalid token failed: {e}")

    # Step 4: Call without Authorization header to verify unauthorized (additional confirmation)
    try:
        noauth_resp = requests.get(ELIGIBILITY_URL, params=params, timeout=TIMEOUT)
        assert noauth_resp.status_code == 401, f"Expected 401 without token, got {noauth_resp.status_code}"
    except Exception as e:
        raise AssertionError(f"GET eligibility without token failed: {e}")


test_tc005_get_eligibility_remaining_limit_with_valid_and_invalid_token()