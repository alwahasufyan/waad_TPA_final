import requests

BASE_URL = "http://localhost:8080"
LOGIN_ENDPOINT = "/api/v1/auth/login"
ELIGIBILITY_ENDPOINT = "/api/v1/members/eligibility"
TIMEOUT = 30

def test_get_api_v1_members_eligibility_with_valid_query():
    login_payload = {
        "identifier": "superadmin@tba.sa",
        "password": "Admin@123"
    }
    headers = {
        "Content-Type": "application/json"
    }
    # Login and get the JWT token
    try:
        login_resp = requests.post(
            BASE_URL + LOGIN_ENDPOINT,
            json=login_payload,
            headers=headers,
            timeout=TIMEOUT
        )
        assert login_resp.status_code == 200, f"Login failed with status {login_resp.status_code}"
        json_login = login_resp.json()
        token = json_login.get("data", {}).get("token")
        assert token, "JWT token not found in login response"
    except requests.RequestException as e:
        assert False, f"Login request failed: {e}"

    # Use the token to call the eligibility endpoint
    headers_auth = {
        "Authorization": f"Bearer {token}"
    }
    params = {
        "query": "1234567890"
    }
    try:
        eligibility_resp = requests.get(
            BASE_URL + ELIGIBILITY_ENDPOINT,
            headers=headers_auth,
            params=params,
            timeout=TIMEOUT
        )
        # Expect 200 or 404 but NOT 500
        assert eligibility_resp.status_code in (200, 404), f"Unexpected status code: {eligibility_resp.status_code}"
        assert eligibility_resp.status_code != 500, "Server error 500 encountered"
    except requests.RequestException as e:
        assert False, f"Eligibility request failed: {e}"

test_get_api_v1_members_eligibility_with_valid_query()