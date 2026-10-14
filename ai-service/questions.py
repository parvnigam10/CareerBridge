def question(text, words, tip):
    return {"question": text, "keywords": words, "tip": tip}

def bank(role, skill):
    return [
        question(f"Describe a project using {skill} relevant to a {role} role.", ["problem", "approach", "result"], "Explain the problem, your contribution and the result."),
        question(f"How would you debug an intermittent error in a {skill} application?", ["reproduce", "logs", "test"], "Start with reproducible evidence, logs and a focused test."),
        question(f"How would you test a new feature using {skill}?", ["unit", "integration", "edge"], "Cover isolated logic, dependencies and edge cases."),
        question("How would you investigate and fix a performance bottleneck?", ["measure", "profile", "optimize"], "Measure first and compare before and after."),
        question("Explain how you would handle a technical disagreement within a team.", ["team", "discuss", "result"], "Give a specific example, the discussion and its outcome."),
    ]

def generate(role, skill):
    """Local question bank only; credentials never enable external generation."""
    return {"questions": bank(role, skill), "engine": "PYTHON_QUESTION_BANK"}
