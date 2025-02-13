import os

SRC_FOLDER = "src"
def ollama_edit(code):
    """Use GitHub Copilot to generate documentation for the given code."""
    prompt = f"Generate documentation for the following Kotlin code:\n{file_content}"
    response = ollama.chat(model="codegemma", messages=[{"role": "user", "content": prompt}])
    return response["message"]["content"]

def process_files():
    """Iterate over Kotlin files and add Copilot-generated documentation."""
    for file_name in os.listdir(SRC_FOLDER):
        if file_name.endswith(".kt"):
            file_path = os.path.join(SRC_FOLDER, file_name)
            with open(file_path, "r") as f:
                code = f.read()
            
            # Get Copilot-generated documentation
            # Get Copilot-generated documentation
        updated_code = ollama_edit(code)
        print(f"\n🔄 Copilot Response:\n{updated_code}\n")       
        # Write the updated code back to the file
        with open(file_path, "w") as f:
            f.write(updated_code)

        print(f"✅ Successfully updated {file_name} with Copilot edits.\n")

if __name__ == "__main__":
    process_files()