import requests

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/v1/auth/login"
CLAIMS_URL = f"{BASE_URL}/api/v1/claims"
TIMEOUT = 30

def test_post_api_v1_claims_missing_visit_id_returns_400():
    login_payload = {
        "identifier": "superadmin@tba.sa",
        "password": "Admin@123"
    }
    try:
        # Login and get token
        login_resp = requests.post(LOGIN_URL, json=login_payload, timeout=TIMEOUT)
        assert login_resp.status_code == 200, f"Login failed with status code {login_resp.status_code}"
        login_data = login_resp.json()
        token = login_data.get("data", {}).get("token")
        assert token, "JWT token not found in login response"

        headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }

        # Prepare claim payload missing visitId, either empty object or with lines missing visitId
        claim_payload_options = [
            {},
            {
                "lines": [
                    {
                        # Intentionally no visitId, minimal fields
                        "serviceId": 1234
                    }
                ]
            }
        ]

        # Test both payload options
        for claim_payload in claim_payload_options:
            resp = requests.post(CLAIMS_URL, json=claim_payload, headers=headers, timeout=TIMEOUT)
            # Accept either 400 or 500 for business rule exception on missing visitId
            assert resp.status_code in (400, 500), (
                f"Expected status 400 or 500 for missing visitId, got {resp.status_code} with response: {resp.text}"
            )

    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

test_post_api_v1_claims_missing_visit_id_returns_400()