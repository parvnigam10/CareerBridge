import io
import json
import os
import unittest
from unittest.mock import patch
from fastapi.testclient import TestClient
from main import app
from resume_feedback import feedback, extract

DATA = {"evidenceText": "Built a Java project with tests.", "targetTitle": "Developer",
        "jobDescription": "Java developer", "missingSkills": ["SQL"], "localSuggestions": ["Describe real outcomes."]}

class ResumeFeedbackTests(unittest.TestCase):
    @patch.dict(os.environ, {"OPENAI_API_KEY": "", "OPENAI_MODEL": ""})
    @patch("urllib.request.urlopen")
    def test_no_credentials_means_no_provider_call(self, request):
        result = TestClient(app).post("/resume-feedback", json=DATA)
        self.assertEqual(result.status_code, 200)
        self.assertEqual(result.json(), {"status": "NOT_CONFIGURED", "suggestions": []})
        request.assert_not_called()

    def test_input_limits_are_enforced(self):
        self.assertEqual(TestClient(app).post("/resume-feedback", json={**DATA, "evidenceText": "x" * 8001}).status_code, 422)

    @patch.dict(os.environ, {"OPENAI_API_KEY": "fake-test-key", "OPENAI_MODEL": "test-model"})
    @patch("urllib.request.urlopen", side_effect=TimeoutError)
    def test_provider_failure_is_an_honest_fallback(self, request):
        self.assertEqual(feedback(DATA), {"status": "UNAVAILABLE", "suggestions": []})

    @patch.dict(os.environ, {"OPENAI_API_KEY": "fake-test-key", "OPENAI_MODEL": "test-model"})
    @patch("urllib.request.urlopen")
    def test_success_uses_structured_advice_and_disables_storage(self, request):
        suggestions = [{"issue": "Testing detail", "recommendation": "Describe the tests you actually wrote."}]
        response = {"status": "completed", "output": [{"type": "reasoning"}, {"content": [
            {"type": "output_text", "text": json.dumps({"suggestions": suggestions})}]}]}
        request.return_value = io.BytesIO(json.dumps(response).encode())
        self.assertEqual(feedback(DATA), {"status": "OPENAI", "suggestions": suggestions})
        payload = json.loads(request.call_args.args[0].data)
        self.assertFalse(payload["store"])
        self.assertTrue(payload["text"]["format"]["strict"])
        self.assertNotIn("score", payload["text"]["format"]["schema"]["properties"])

    def test_refused_incomplete_or_malformed_outputs_are_rejected(self):
        cases = [{"status": "incomplete"}, {"status": "completed", "output": [{"content": [{"type": "refusal", "refusal": "No"}]}]},
                 {"status": "completed", "output": [{"content": [{"type": "output_text", "text": '{"suggestions":[{"score":100}]}'}]}]}]
        for case in cases:
            with self.subTest(case=case), self.assertRaises((ValueError, KeyError)):
                extract(case)

if __name__ == "__main__":
    unittest.main()
