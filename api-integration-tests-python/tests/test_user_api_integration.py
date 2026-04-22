import json
import os
import time
import unittest
import urllib.error
import urllib.request
from random import randint


BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8090")
USER_API = f"{BASE_URL}/api/user"


def random_user():
    suffix = f"{int(time.time() * 1000)}_{randint(10000, 99999)}"
    return {
        "username": f"it_user_{suffix}",
        "password": "P@ssw0rd123",
        "nickname": f"IT-{suffix}",
    }


def request_json(path, method="GET", payload=None, headers=None):
    url = f"{USER_API}{path}"
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)

    request = urllib.request.Request(url=url, data=body, method=method, headers=req_headers)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            content = response.read().decode("utf-8") if response.length != 0 else ""
            return response.status, json.loads(content) if content else None
    except urllib.error.HTTPError as http_error:
        content = http_error.read().decode("utf-8")
        parsed = json.loads(content) if content else None
        return http_error.code, parsed


class UserApiIntegrationTest(unittest.TestCase):
    def test_register_login_me(self):
        user = random_user()

        register_status, register_data = request_json("/register", method="POST", payload=user)
        self.assertEqual(
            register_status,
            201,
            msg=f"注册失败: status={register_status}, body={register_data}",
        )
        self.assertTrue(register_data.get("success"))
        self.assertEqual(register_data["data"]["username"], user["username"])
        self.assertEqual(register_data["data"]["nickname"], user["nickname"])
        self.assertNotIn("password", register_data["data"], "注册返回不应包含 password")

        login_status, login_data = request_json(
            "/login",
            method="POST",
            payload={"username": user["username"], "password": user["password"]},
        )
        self.assertEqual(login_status, 200, msg=f"登录失败: status={login_status}, body={login_data}")
        self.assertTrue(login_data.get("success"))
        self.assertEqual(login_data["data"]["tokenType"], "Bearer")
        access_token = login_data["data"].get("accessToken")
        self.assertTrue(access_token, "登录返回缺少 accessToken")

        me_status, me_data = request_json(
            "/me",
            method="GET",
            headers={"Authorization": f"Bearer {access_token}"},
        )
        self.assertEqual(me_status, 200, msg=f"/me 访问失败: status={me_status}, body={me_data}")
        self.assertTrue(me_data.get("success"))
        self.assertEqual(me_data["data"]["username"], user["username"])

    def test_login_with_wrong_password_should_be_401(self):
        user = random_user()
        request_json("/register", method="POST", payload=user)

        bad_login_status, bad_login_data = request_json(
            "/login",
            method="POST",
            payload={"username": user["username"], "password": "bad-password"},
        )
        self.assertEqual(
            bad_login_status,
            401,
            msg=f"错误密码登录未返回 401: status={bad_login_status}, body={bad_login_data}",
        )
        self.assertFalse(bad_login_data.get("success"))
        self.assertEqual(bad_login_data["error"]["code"], "AUTH_BAD_CREDENTIALS")


if __name__ == "__main__":
    unittest.main()
