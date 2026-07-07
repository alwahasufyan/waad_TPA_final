import requests

BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/v1/auth/login"
MEDICAL_CATEGORIES_URL = f"{BASE_URL}/api/provider/medical-categories"
CONTRACT_SERVICES_URL = f"{BASE_URL}/api/provider/my-contract/services"
CLAIMS_URL = f"{BASE_URL}/api/v1/claims"
TIMEOUT = 30

USERNAME = "superadmin@tba.sa"
PASSWORD = "Admin@123"

def test_post_api_v1_claims_with_valid_and_invalid_claim_data():
    session = requests.Session()

    # Step 1: Authenticate and get token
    login_payload = {
        "identifier": USERNAME,
        "password": PASSWORD
    }
    try:
        login_resp = session.post(
            LOGIN_URL,
            json=login_payload,
            timeout=TIMEOUT
        )
        assert login_resp.status_code == 200, f"Login failed with status {login_resp.status_code}"
        login_json = login_resp.json()
        assert login_json.get("status") == "success", f"Login status not success: {login_json}"
        token = login_json.get("data", {}).get("token")
        assert token and isinstance(token, str), "Token not found in login response"

    except Exception as e:
        raise AssertionError(f"Authentication failed: {e}")

    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }

    # Step 2: Retrieve medical categories
    try:
        categories_resp = session.get(
            MEDICAL_CATEGORIES_URL,
            headers=headers,
            timeout=TIMEOUT
        )
        assert categories_resp.status_code == 200, f"Medical categories fetch failed with status {categories_resp.status_code}"
        categories = categories_resp.json()
        # Expecting a list at the root or some key pointing to list
        medical_categories = []
        if isinstance(categories, list):
            medical_categories = categories
        elif isinstance(categories, dict):
            # Try common keys
            for k in ["data", "medicalCategories", "results"]:
                if k in categories and isinstance(categories[k], list):
                    medical_categories = categories[k]
                    break
            else:
                # fallback: try keys containing list
                for v in categories.values():
                    if isinstance(v, list):
                        medical_categories = v
                        break
        # At least one category needed
        assert len(medical_categories) > 0, "No medical categories found"
        category_id = medical_categories[0].get("id")
        assert category_id, "categoryId missing in medical category"
    except Exception as e:
        raise AssertionError(f"Fetching medical categories failed: {e}")

    # Step 3: Retrieve contract services
    try:
        services_resp = session.get(
            CONTRACT_SERVICES_URL,
            headers=headers,
            timeout=TIMEOUT
        )
        assert services_resp.status_code == 200, f"Contract services fetch failed with status {services_resp.status_code}"
        services = services_resp.json()
        # Same approach to detect list
        medical_services = []
        if isinstance(services, list):
            medical_services = services
        elif isinstance(services, dict):
            for k in ["data", "medicalServices", "results"]:
                if k in services and isinstance(services[k], list):
                    medical_services = services[k]
                    break
            else:
                for v in services.values():
                    if isinstance(v, list):
                        medical_services = v
                        break
        assert len(medical_services) > 0, "No contract medical services found"
        medical_service = medical_services[0]
        medical_service_id = medical_service.get("id")
        # Some services might have a categoryId mapping - verify
        medical_service_category_id = medical_service.get("categoryId")

        assert medical_service_id, "medicalService id missing"
        # categoryId here may be None, which affects the third test case later
    except Exception as e:
        raise AssertionError(f"Fetching contract services failed: {e}")

    # Step 4: Find a valid visitId to use for claim - no direct endpoint given.
    # Without an endpoint, we fallback to a dummy visitId (hope system has it).
    # Preferable to discover from a first visit retrieval, but not available.
    # Use a placeholder id (int/uuid) string likely valid, e.g. "1"
    visit_id = "1"

    # Step 5: Prepare claim lines for the valid claim test
    valid_claim_line = {
        "medicalServiceId": medical_service_id,
        "quantity": 1,
        "categoryId": medical_service_category_id if medical_service_category_id else category_id,
        "unitPrice": 0  # pricing manual is not allowed, presumably back-end sets price, but include for schema
    }

    # Claims API schema likely requires:
    # {
    #   "visitId": "...",
    #   "categoryId": "...",
    #   "claimLines": [ {medicalServiceId, categoryId, quantity, ...} ]
    # }
    # We set unitPrice zero or omit to allow backend price enforcement, but will include for schema compliance.

    # --- Test valid ClaimCreateDto ---
    valid_claim_payload = {
        "visitId": visit_id,
        "categoryId": category_id,
        "claimLines": [valid_claim_line]
    }

    # Step 6: Test valid claim submission
    try:
        resp_valid = session.post(
            CLAIMS_URL,
            headers=headers,
            json=valid_claim_payload,
            timeout=TIMEOUT
        )
        assert resp_valid.status_code == 200, f"Valid claim creation failed with status {resp_valid.status_code}"
        claim_view = resp_valid.json()
        # Generic checks on response content
        assert isinstance(claim_view, dict), "ClaimViewDto response not a dict"
        assert "id" in claim_view or "claimId" in claim_view, "ClaimViewDto missing claim id"
    except Exception as e:
        raise AssertionError(f"Valid claim submission failed: {e}")

    # Get created claim id to cleanup (if delete possible, not in spec)
    created_claim_id = claim_view.get("id") or claim_view.get("claimId")

    # --- Test missing visitId (expect 400 Business Rule Exception) ---
    missing_visitid_payload = {
        # "visitId" omitted
        "categoryId": category_id,
        "claimLines": [valid_claim_line]
    }
    try:
        resp_missing_visitid = session.post(
            CLAIMS_URL,
            headers=headers,
            json=missing_visitid_payload,
            timeout=TIMEOUT
        )
        assert resp_missing_visitid.status_code == 400, f"Missing visitId did not produce 400, got {resp_missing_visitid.status_code}"
        error_json = resp_missing_visitid.json()
        # Should contain info about Business Rule Exception and visitId mandatory
        assert any(
            term in str(error_json).lower()
            for term in ("business rule", "visitid", "mandatory", "visit id", "visitId")
        ), f"Error message does not mention visitId or business rule: {error_json}"
    except Exception as e:
        raise AssertionError(f"Missing visitId test failed: {e}")

    # --- Test claim line referencing medical service lacking category mapping and no categoryId provided ---
    # First find or fabricate a medical service with no categoryId mapping
    try:
        service_without_category = None
        for svc in medical_services:
            if svc.get("categoryId") in (None, "", "null"):
                service_without_category = svc
                break
        if not service_without_category:
            # If none found, fabricate a claim line with categoryId omitted
            service_without_category = medical_services[0]
            if service_without_category.get("categoryId"):
                # intentionally omit categoryId from claim line to simulate error case
                pass
        claim_line_no_categoryid = {
            "medicalServiceId": service_without_category.get("id"),
            "quantity": 1
            # categoryId omitted here intentionally
        }
        # Prepare claim payload without top-level categoryId as well (to force error)
        invalid_claim_payload = {
            "visitId": visit_id,
            # no categoryId at top-level
            "claimLines": [claim_line_no_categoryid]
        }
        resp_no_category = session.post(
            CLAIMS_URL,
            headers=headers,
            json=invalid_claim_payload,
            timeout=TIMEOUT
        )
        assert resp_no_category.status_code == 400, f"Claim with missing categoryId did not produce 400, got {resp_no_category.status_code}"
        error_json = resp_no_category.json()
        assert any(
            term in str(error_json).lower()
            for term in ("business rule", "categoryid", "category id", "mandatory")
        ), f"Error message does not mention categoryId or business rule: {error_json}"
    except Exception as e:
        raise AssertionError(f"Claim line missing categoryId test failed: {e}")

test_post_api_v1_claims_with_valid_and_invalid_claim_data()