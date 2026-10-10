"""Deterministic TF-IDF text similarity; no model downloads or training claims."""
from collections import Counter
from math import log, sqrt
import re

STOP = set("a an the and or to of for in on with is are at be as by you your we our this that".split())

def tokens(text):
    return [word for word in re.findall(r"[a-z0-9+#.]+", text.lower()) if word not in STOP]

def rank(profile_text, jobs):
    documents = [Counter(tokens(profile_text))] + [Counter(tokens(job["text"])) for job in jobs]
    frequencies = Counter(term for document in documents for term in document)
    idf = {term: log((1 + len(documents)) / (1 + count)) + 1 for term, count in frequencies.items()}
    vectors = [{term: (1 + log(count)) * idf[term] for term, count in document.items()} for document in documents]
    source = vectors[0]
    source_norm = sqrt(sum(value * value for value in source.values()))
    result = {}
    for job, vector in zip(jobs, vectors[1:]):
        denominator = source_norm * sqrt(sum(value * value for value in vector.values()))
        result[str(job["id"])] = round(sum(value * vector.get(term, 0) for term, value in source.items()) / denominator, 6) if denominator else 0.0
    return result
