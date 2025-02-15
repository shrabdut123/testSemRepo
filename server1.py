from fastapi import FastAPI
import subprocess
import os
import ollama
import socket

app = FastAPI()

SRC_FOLDER = "src"

def find_free_port():
    """Find an available port dynamically."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(('', 0))  # Bind to an available port
        return s.getsockname()[1]

@app.get("/generate")
def generate(prompt: str):
    """Send a prompt to Ollama and return the response"""
    command = f"ollama run mistral '{prompt}'"
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    return {"response": result.stdout.strip()}

def ollama_edit(code: str) -> str:
    """Use Ollama (CodeGemma) to generate documentation for the given Kotlin code."""
    prompt = f"Generate documentation for the following Kotlin code:\n{code}"
    response = ollama.chat(model="codegemma", messages=[{"role": "user", "content": prompt}])
    return response["message"]["content"]

@app.post("/process_files")
def process_files():
    """Iterate over Kotlin files and add Ollama-generated documentation."""
    updated_files = []
    
    for file_name in os.listdir(SRC_FOLDER):
        if file_name.endswith(".kt"):
            file_path = os.path.join(SRC_FOLDER, file_name)
            with open(file_path, "r") as f:
                code = f.read()

            # Get Ollama-generated documentation
            updated_code = ollama_edit(code)

            # Write the updated code back to the file
            with open(file_path, "w") as f:
                f.write(updated_code)

            updated_files.append(file_name)

    return {"message": "Successfully updated files", "updated_files": updated_files}

if __name__ == "__main__":
    import uvicorn
    port = find_free_port()  # Get a free port
    # Write the port to a file or environment variable
    with open("port.txt", "w") as f:
        f.write(str(port))
    print(f"Starting FastAPI server on port {port}")
    uvicorn.run(app, host="0.0.0.0", port=port)