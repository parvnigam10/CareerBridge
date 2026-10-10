import os
import unittest
from unittest.mock import patch
from fastapi.testclient import TestClient
from main import app

class ApiTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)

    def test_health_and_ranking_contract(self):
        self.assertEqual(self.client.get("/health").status_code, 200)
        response = self.client.post("/rank", json={"profileText": "Java SQL backend", "jobs": [{"id": 4, "text": "Java SQL developer"}]})
        self.assertEqual(response.status_code, 200)
        self.assertGreater(response.json()["scores"]["4"], 0)

    def test_invalid_request_is_rejected(self):
        self.assertEqual(self.client.post("/rank", json={"jobs": []}).status_code, 422)
        self.assertEqual(self.client.post("/questions", json={"role": "", "skill": "Java"}).status_code, 422)

    @patch.dict(os.environ, {"OPENAI_API_KEY": "", "OPENAI_MODEL": ""})
    def test_question_contract(self):
        response = self.client.post("/questions", json={"role": "Java developer", "skill": "Java"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.json()["questions"]), 5)

if __name__ == "__main__":
    unittest.main()
