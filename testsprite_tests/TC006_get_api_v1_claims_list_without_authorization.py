import requests

BASE_URL = "http://localhost:8080"
CLAIMS_PATH = "/api/v1/claims"
TIMEOUT = 30

def test_get_api_v1_claims_list_without_authorization():
    url = BASE_URL + CLAIMS_PATH
    try:
        response = requests.get(url, timeout=TIMEOUT)
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

    assert response.status_code == 401, f"Expected 401 Unauthorized but got {response.status_code}"

test_get_api_v1_claims_list_without_authorization()