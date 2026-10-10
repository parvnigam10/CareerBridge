import os
import unittest
from unittest.mock import patch
from matching import rank
from questions import generate, extract_questions, bank

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

    def test_response_parser_ignores_reasoning_items(self):
        import json
        response = {"output": [{"type": "reasoning"}, {"content": [{"type": "output_text", "text": json.dumps({"questions": bank("Developer", "SQL")})}]}]}
        self.assertEqual(len(extract_questions(response)), 5)

    @patch.dict(os.environ, {"OPENAI_API_KEY": "fake-test-key", "OPENAI_MODEL": "test-model"})
    @patch("urllib.request.urlopen", side_effect=TimeoutError)
    def test_provider_failure_returns_honest_fallback(self, request):
        self.assertEqual(generate("Developer", "Java")["engine"], "PYTHON_QUESTION_BANK_FALLBACK")

if __name__ == "__main__":
    unittest.main()
