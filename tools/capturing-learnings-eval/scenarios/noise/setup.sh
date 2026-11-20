source "$(dirname "$0")/../common.sh"
init_repo "$1"
cat >calc.py <<'EOF'
def add(a, b):
    return a - b
EOF
cat >test_calc.py <<'EOF'
import unittest

from calc import add


class AddTest(unittest.TestCase):
    def test_adds_two_numbers(self):
        self.assertEqual(add(2, 3), 5)


if __name__ == "__main__":
    unittest.main()
EOF
commit_all "Add calc"
