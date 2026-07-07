import requests

def test_post_api_v1_auth_login_with_invalid_credentials():
    base_url = "http://localhost:8080"
    url = f"{base_url}/api/v1/auth/login"
    headers = {"Content-Type": "application/json"}
    payload = {
        "identifier": "wrong@email.com",
        "password": "wrongpass"
    }

    try:
        response = requests.post(url, json=payload, headers=headers, timeout=30)
        assert response.status_code == 401, f"Expected status code 401, got {response.status_code}"
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

test_post_api_v1_auth_login_with_invalid_credentials()