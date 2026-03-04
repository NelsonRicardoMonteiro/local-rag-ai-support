from datasets import load_dataset
import requests
import time

API = "http://localhost:8080/api/knowledge/ingest"
ds = load_dataset("bitext/Bitext-customer-support-llm-chatbot-training-dataset")
train = ds["train"]

print("columns:", train.column_names)

def pick(example, keys, default=""):
    for k in keys:
        if k in example and example[k]:
            return str(example[k]).strip()
    return default

count = 0
limit = 5000

for ex in train:
    question = pick(ex, ["instruction", "question", "prompt", "user"])
    answer = pick(ex, ["response", "answer", "output", "assistant"])
    if not question or not answer:
        continue

    payload = {
        "title": f"bitext-{count}",
        "content": f"Q: {question}\nA: {answer}"
    }

    r = requests.post(API, json=payload, timeout=30)
    if r.status_code >= 300:
        print("erro:", r.status_code, r.text[:300])
        continue

    count += 1
    if count % 50 == 0:
        print("ingested:", count)
    if count >= limit:
        break
    time.sleep(0.05)

print("done:", count)