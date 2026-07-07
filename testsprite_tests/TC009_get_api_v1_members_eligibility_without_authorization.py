import requests

def test_get_api_v1_members_eligibility_without_authorization():
    base_url = "http://localhost:8080"
    endpoint = "/api/v1/members/eligibility"
    params = {"query": "1234567890"}
    timeout = 30

    try:
        response = requests.get(f"{base_url}{endpoint}", params=params, timeout=timeout)
        assert response.status_code == 401, f"Expected 401 Unauthorized, got {response.status_code}"
    except requests.RequestException as e:
        assert False, f"Request failed with exception: {e}"

test_get_api_v1_members_eligibility_without_authorization()