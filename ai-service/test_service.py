import os
import unittest
from unittest.mock import patch
from matching import rank
from questions import generate, bank

class MatchingTests(unittest.TestCase):
    def test_relevant_role_ranks_above_unrelated_role(self):
        scores = rank("Java Spring SQL backend", [{"id": 1, "text": "Java Spring backend SQL"}, {"id": 2, "text": "Graphic illustrator visual design"}])
        self.assertGreater(scores["1"], scores["2"])
        self.assertTrue(all(0 <= value <= 1 for value in scores.values()))

    def test_empty_profile_does_not_divide_by_zero(self):
        self.assertEqual(rank("", [{"id": 2, "text": "Java"}]), {"2": 0.0})

    def test_empty_jobs(self):
        self.assertEqual(rank("Java", []), {})

    @patch.dict(os.environ, {"OPENAI_API_KEY": "", "OPENAI_MODEL": ""})
    def test_question_bank_needs_no_credentials(self):
        response = generate("Backend developer", "Java")
        self.assertEqual(len(response["questions"]), 5)
        self.assertEqual(response["engine"], "PYTHON_QUESTION_BANK")

    @patch.dict(os.environ, {"OPENAI_API_KEY": "fake-test-key", "OPENAI_MODEL": "test-model"})
    @patch("urllib.request.urlopen", side_effect=AssertionError("External network must not be used"))
    def test_old_provider_credentials_cannot_enable_paid_calls(self, request):
        response = generate("Developer", "Java")
        self.assertEqual(response, {"questions": bank("Developer", "Java"), "engine": "PYTHON_QUESTION_BANK"})
        request.assert_not_called()

if __name__ == "__main__":
    unittest.main()
