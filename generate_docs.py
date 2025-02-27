import openai
import os
import concurrent.futures
import hashlib
import json
import time
import re  # For replacing existing documentation

# Azure OpenAI API Configuration
AZURE_OPENAI_ENDPOINT = "https://code-to-docs-gen-openai.openai.azure.com/"
AZURE_OPENAI_API_KEY = os.getenv("AZURE_OPENAI_API_KEY")
DEPLOYMENT_NAME = "code-to-docs-llm-gpt-4-8k"

if not AZURE_OPENAI_API_KEY:
    raise ValueError("Azure OpenAI API key is not set.")

openai.api_base, openai.api_key, openai.api_type, openai.api_version = (
    AZURE_OPENAI_ENDPOINT, AZURE_OPENAI_API_KEY, "azure", "2024-05-01-preview"
)

CACHE_FILE = "cache.json"
SRC_FOLDER = os.getenv("SRC_FOLDER", "src")

MAX_LINES, MAX_CHARS = 1000, 4000  # Truncate files to prevent exceeding 8K token limit

# Load cache if available
CACHE = json.load(open(CACHE_FILE, "r", encoding="utf-8")) if os.path.exists(CACHE_FILE) else {}

def get_code_hash(code):
    return hashlib.md5(code.encode()).hexdigest()

def truncate_code(code):
    """Truncate code to avoid exceeding token limits."""
    lines = code.splitlines()[:MAX_LINES]  # Keep only the first N lines
    return "\n".join(lines)[:MAX_CHARS]  # Limit to MAX_CHARS characters

def extract_existing_code(content):
    """Remove all existing documentation blocks."""
    return re.sub(r"/\*.*?\*/\s*", "", content, flags=re.DOTALL)  # Remove all `/* ... */` blocks

def generate_documentation(code):
    """Generate documentation and cache results, ensuring code fits within model limits."""
    truncated_code = truncate_code(code)
    code_hash = get_code_hash(truncated_code)
    if code_hash in CACHE:
        return CACHE[code_hash]

    start_time = time.time()
    try:
        response = openai.ChatCompletion.create(
            engine=DEPLOYMENT_NAME,
            messages=[{"role": "user", "content": f"Generate documentation for:\n\n```js\n{truncated_code}\n```"}],
            max_tokens=500, temperature=0, top_p=1.0,
        )
        print(f"Model Response Time: {time.time() - start_time:.2f} seconds")
        CACHE[code_hash] = response["choices"][0]["message"]["content"]
        return CACHE[code_hash]
    except Exception as e:
        print(f"Error: {e}")
        return None

def process_file(file_path):
    with open(file_path, "r", encoding="utf-8") as f:
        original_content = f.read().strip()
    if not original_content:
        return None

    # Remove existing documentation if present
    clean_code = extract_existing_code(original_content)
    
    documentation = generate_documentation(clean_code)
    if documentation:
        updated_code = f"/*\n{documentation}\n*/\n{clean_code}"
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(updated_code)
        print(f"Updated: {file_path}")
        return file_path
    return None

def list_files_in_src_folder():
    print(f"Starting to walk through the folder: {SRC_FOLDER}")

    if not os.path.exists(SRC_FOLDER):
        print(f"Error: The source folder '{SRC_FOLDER}' does not exist.")
    else:
        for dirpath, subdirs, filenames in os.walk(SRC_FOLDER):
            print(f"\n📂 Entering Directory: {dirpath}")
            print(f"📁 Subdirectories: {subdirs}")
            print(f"📄 Files Found: {filenames}")

            if not filenames:
                print(f"⚠️ No files found in {dirpath}")

            for file in filenames:
                full_path = os.path.join(dirpath, file)
                print(f"✅ Processing File: {full_path}")

    print("Traversal completed.")

def traverse_and_update_files():
    valid_files = [
        os.path.join(dirpath, file) for dirpath, _, filenames in os.walk(SRC_FOLDER)
        for file in filenames if file.endswith((".js", ".ts", ".kt")) and not file.endswith((".test.js", ".test.ts", ".json"))
    ]   
    print(f"Found {len(valid_files)} files.")

    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        updated_files = list(filter(None, executor.map(process_file, valid_files)))

    if updated_files:
        with open("updated_files.txt", "w", encoding="utf-8") as f:
            f.writelines("\n".join(updated_files))

    json.dump(CACHE, open(CACHE_FILE, "w", encoding="utf-8"))

    return updated_files

if __name__ == "__main__":
    print("Starting documentation generation...")
    list_files_in_src_folder()
    updated_files = traverse_and_update_files()
    print(f"Updated {len(updated_files)} files.") if updated_files else print("No files updated.")