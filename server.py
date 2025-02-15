from fastapi import FastAPI
import subprocess

app = FastAPI()

@app.get("/generate")
def generate(prompt: str):
    """Send a prompt to Ollama and return the response"""
    command = f"ollama run mistral '{prompt}'"
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    return {"response": result.stdout.strip()}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)