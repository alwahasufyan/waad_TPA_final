import requests

BASE_ENDPOINT = "http://localhost:8080"
LOGIN_PATH = "/api/v1/auth/login"
TIMEOUT = 30

def test_post_api_v1_auth_login_with_valid_credentials():
    url = BASE_ENDPOINT + LOGIN_PATH
    payload = {
        "identifier": "superadmin@tba.sa",
        "password": "Admin@123"
    }
    headers = {
        "Content-Type": "application/json"
    }
    try:
        response = requests.post(url, json=payload, headers=headers, timeout=TIMEOUT)
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

    assert response.status_code == 200, f"Expected 200 OK, got {response.status_code}"

    try:
        json_resp = response.json()
    except ValueError:
        assert False, "Response is not valid JSON"

    assert "data" in json_resp, "'data' key missing in response"
    assert "token" in json_resp["data"], "'token' key missing in response data"
    token = json_resp["data"]["token"]
    assert isinstance(token, str) and token, "JWT token is empty or not a string"

    return token

test_post_api_v1_auth_login_with_valid_credentials()