import requests

BASE_URL = "http://localhost:8080"
LOGIN_PATH = "/api/v1/auth/login"
MEDICAL_CATEGORIES_PATH = "/api/v1/medical-categories/all"
TIMEOUT = 30

def test_get_api_v1_medical_categories_all_with_valid_token():
    login_url = BASE_URL + LOGIN_PATH
    login_payload = {
        "identifier": "superadmin@tba.sa",
        "password": "Admin@123"
    }
    try:
        login_response = requests.post(login_url, json=login_payload, timeout=TIMEOUT)
        assert login_response.status_code == 200, f"Login failed with status {login_response.status_code}"
        login_json = login_response.json()
        token = login_json.get("data", {}).get("token")
        assert token and isinstance(token, str), "Token not found in login response"

        headers = {"Authorization": f"Bearer {token}"}
        categories_url = BASE_URL + MEDICAL_CATEGORIES_PATH
        categories_response = requests.get(categories_url, headers=headers, timeout=TIMEOUT)
        assert categories_response.status_code == 200, f"Expected 200, got {categories_response.status_code}"
        categories_json = categories_response.json()
        # Expecting a list of medical categories (could be list or dict containing list)
        assert isinstance(categories_json, dict) or isinstance(categories_json, list), "Response is not a JSON object or list"
        # Commonly expecting a list under key, fallback check if response is list directly
        if isinstance(categories_json, dict):
            # Try to find list in top level keys or check for "data" or "items"
            found_list = False
            for key in ["data", "items", "medicalCategories"]:
                if key in categories_json and isinstance(categories_json[key], list):
                    found_list = True
                    break
            assert found_list, "Response JSON does not contain a list of medical categories"
        else:
            # If it's a list check it's not empty
            assert all(isinstance(item, dict) for item in categories_json), "Not all items are dicts in the categories list"
    except requests.RequestException as e:
        assert False, f"Request failed: {e}"

test_get_api_v1_medical_categories_all_with_valid_token()