import requests

def test_get_api_v1_medical_categories_all_without_authorization():
    base_url = "http://localhost:8080"
    url = f"{base_url}/api/v1/medical-categories/all"

    try:
        response = requests.get(url, timeout=30)
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

    assert response.status_code == 401, f"Expected 401 Unauthorized, got {response.status_code}"

test_get_api_v1_medical_categories_all_without_authorization()