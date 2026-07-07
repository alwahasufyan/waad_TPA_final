import requests

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/v1/auth/login"
CLAIMS_URL = f"{BASE_URL}/api/v1/claims"
TIMEOUT = 30

def test_get_api_v1_claims_list_with_valid_token():
    login_payload = {"identifier": "superadmin@tba.sa", "password": "Admin@123"}
    try:
        # Authenticate and get JWT token
        login_resp = requests.post(LOGIN_URL, json=login_payload, timeout=TIMEOUT)
        assert login_resp.status_code == 200, f"Login failed with status {login_resp.status_code}"
        login_data = login_resp.json()
        token = login_data.get("data", {}).get("token")
        assert token, "JWT token not found in login response"

        headers = {"Authorization": f"Bearer {token}"}
        # Call GET /api/v1/claims with auth header
        claims_resp = requests.get(CLAIMS_URL, headers=headers, timeout=TIMEOUT)
        assert claims_resp.status_code == 200, f"Claims request failed with status {claims_resp.status_code}"
        claims_data = claims_resp.json()
        assert "data" in claims_data, "'data' field missing in claims response"
        assert "items" in claims_data["data"], "'items' list missing in claims data"

        # Assert items is a list (paginated list of claims)
        assert isinstance(claims_data["data"]["items"], list), "'items' is not a list"
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

test_get_api_v1_claims_list_with_valid_token()