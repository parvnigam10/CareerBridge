"""Internal service. Keep private; the Spring API owns authentication."""
from fastapi import FastAPI
from pydantic import BaseModel, Field
from matching import rank
from questions import generate

app = FastAPI(title="CareerBridge career service", version="1.0.0")

class JobText(BaseModel):
    id: int
    text: str = Field(max_length=30000)

class RankingRequest(BaseModel):
    profileText: str = Field(max_length=30000)
    jobs: list[JobText] = Field(max_length=1000)

class QuestionRequest(BaseModel):
    role: str = Field(min_length=1, max_length=150)
    skill: str = Field(min_length=1, max_length=150)

@app.get("/health")
def health():
    return {"status": "UP", "ranking": "TF_IDF_COSINE", "scoring": "LOCAL_KEYWORD_HEURISTIC"}

@app.post("/rank")
def rankings(body: RankingRequest):
    return {"scores": rank(body.profileText, [job.model_dump() for job in body.jobs]), "method": "TF_IDF_COSINE"}

@app.post("/questions")
def questions(body: QuestionRequest):
    return generate(body.role, body.skill)
