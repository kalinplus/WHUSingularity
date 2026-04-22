# Python API 集成测试小项目

这个目录是一个独立的 Python 集成测试项目，用于测试已启动容器中的 `singularity-user` API。

## 测试覆盖

- `POST /api/user/register` 注册
- `POST /api/user/login` 登录
- `GET /api/user/me` 鉴权用户信息查询
- 错误密码登录返回 401

## 目录结构

```text
api-integration-tests-python/
  ├─ README.md
  └─ tests/
     └─ test_user_api_integration.py
```

## 运行方式

在仓库根目录执行：

```powershell
python -m unittest discover -s api-integration-tests-python/tests -p "test_*.py" -v
```

## 可选环境变量

- `API_BASE_URL`：API 地址，默认 `http://localhost:8090`

例如：

```powershell
$env:API_BASE_URL="http://localhost:18090"
python -m unittest discover -s api-integration-tests-python/tests -p "test_*.py" -v
```
