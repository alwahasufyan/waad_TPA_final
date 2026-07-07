import requests

BASE_URL = "http://localhost:8080"
LOGIN_PATH = "/api/v1/auth/login"
MEDICAL_CATEGORIES_PATH = "/api/v1/medical-categories/all"
MEDICAL_SERVICES_PATH_TEMPLATE = "/api/v1/medical-categories/{}/medical-services"
TIMEOUT = 30
CREDENTIALS = {
    "identifier": "superadmin@tba.sa",
    "password": "Admin@123"
}

def test_tc010_get_medical_services_by_category_id():
    try:
        # Step 1: Login and extract JWT token
        login_url = BASE_URL + LOGIN_PATH
        login_resp = requests.post(login_url, json=CREDENTIALS, timeout=TIMEOUT)
        assert login_resp.status_code == 200, f"Login failed with status {login_resp.status_code}"
        login_json = login_resp.json()
        token = login_json.get("data", {}).get("token")
        assert token, "Token not found in login response"

        headers = {"Authorization": f"Bearer {token}"}

        # Step 2: Get all medical categories
        categories_url = BASE_URL + MEDICAL_CATEGORIES_PATH
        categories_resp = requests.get(categories_url, headers=headers, timeout=TIMEOUT)
        assert categories_resp.status_code == 200, f"Medical categories fetch failed with status {categories_resp.status_code}"
        categories_json = categories_resp.json()
        # We expect a list inside categories_json, so check accordingly
        # As per instruction, claim list is at data.items, but categories response details not explicitly shown
        # So attempt to access data or root list
        # Try data.items:
        items = None
        if isinstance(categories_json.get("data"), dict):
            items = categories_json["data"].get("items")
        if items is None:
            # if no data.items, then try root list:
            if isinstance(categories_json.get("data"), list):
                items = categories_json["data"]
            else:
                items = categories_json.get("items")
        assert items and isinstance(items, list) and len(items) > 0, "No medical categories found"
        category_id = items[0].get("id")
        assert category_id, "Category ID not found in first medical category"

        # Step 3: Get medical services for the category ID
        services_url = BASE_URL + MEDICAL_SERVICES_PATH_TEMPLATE.format(category_id)
        services_resp = requests.get(services_url, headers=headers, timeout=TIMEOUT)
        assert services_resp.status_code == 200, f"Medical services fetch failed with status {services_resp.status_code}"
        services_json = services_resp.json()
        # Validate that services_json is a list or contains a list at data key
        services_list = None
        if isinstance(services_json.get("data"), list):
            services_list = services_json["data"]
        elif isinstance(services_json, list):
            services_list = services_json
        else:
            # fallback: If data is a dict and contains items
            if isinstance(services_json.get("data"), dict) and "items" in services_json["data"]:
                services_list = services_json["data"]["items"]
        assert services_list is not None, "Medical services list not found in response"
        assert isinstance(services_list, list), "Medical services is not a list"
        # Can be empty, but must be present

    except (requests.RequestException, AssertionError) as e:
        raise AssertionError(f"Test TC010 failed: {e}")

test_tc010_get_medical_services_by_category_id()