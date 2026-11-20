# shared_fixture <dir>: a Python service whose tests pass only with APP_ENV=test.
# CI sets it, and no file in the repository says so. The fact concerns every test run,
# so no single source file is its place.
shared_fixture() {
    init_repo "$1"
    mkdir -p config .github/workflows
    printf '{"database_url": "sqlite:///:memory:", "currency": "EUR"}\n' >config/test.json
    printf '{"currency": "EUR"}\n' >config/production.json
    cat >settings.py <<'EOF'
import json
import os

ENV = os.environ.get("APP_ENV", "production")
with open(os.path.join(os.path.dirname(__file__), "config", ENV + ".json")) as f:
    CONFIG = json.load(f)
DATABASE_URL = CONFIG.get("database_url") or os.environ["DATABASE_URL"]
EOF
    cat >billing.py <<'EOF'
from settings import CONFIG


def invoice_total(lines):
    return round(sum(qty * price for qty, price in lines), 2), CONFIG["currency"]
EOF
    cat >test_billing.py <<'EOF'
import unittest

from billing import invoice_total


class InvoiceTest(unittest.TestCase):
    def test_sums_the_lines(self):
        self.assertEqual(invoice_total([(2, 1.5), (1, 4.0)]), (7.0, "EUR"))


if __name__ == "__main__":
    unittest.main()
EOF
    cat >.github/workflows/test.yml <<'EOF'
name: test
on: [push]
jobs:
  test:
    runs-on: ubuntu-latest
    env:
      APP_ENV: test
    steps:
      - uses: actions/checkout@v4
      - run: python3 -m unittest
EOF
    commit_all "Add billing"
}
