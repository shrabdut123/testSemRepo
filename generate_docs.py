import os
import requests

SRC_FOLDER = "src"
# Read GitHub Token from environment variable
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")

if not GITHUB_TOKEN:
    raise ValueError("❌ GITHUB_TOKEN is not set. Ensure it is passed from GitHub Actions.")


HEADERS = {
    "Authorization": f"Bearer {GITHUB_TOKEN}",
    "Accept": "application/vnd.github+json"
}
def copilot_edit(code):
    """Use GitHub Copilot to generate documentation for the given code."""
    url = "https://api.github.com/copilot/v1/edits"
    payload = {
        "prompt": "Add detailed documentation comments to this Kotlin function.",
        "code": code,
        "temperature": 0.3  # Lower value for consistent edits
    }
    response = requests.post(url, json=payload, headers=HEADERS)
    
    if response.status_code == 200:
        return response.json().get("edited", code)  # Return edited code or original
    else:
        print(f"❌ Error: {response.json()}")
        return code

def process_files():
    """Iterate over Kotlin files and add Copilot-generated documentation."""
    for file_name in os.listdir(SRC_FOLDER):
        if file_name.endswith(".kt"):
            file_path = os.path.join(SRC_FOLDER, file_name)
            with open(file_path, "r") as f:
                code = f.read()
            
            # Get Copilot-generated documentation
            updated_code = copilot_edit(code)
            
            with open(file_path, "w") as f:
                f.write(updated_code)
            
            print(f"✅ Updated {file_name} with Copilot edits.")

if __name__ == "__main__":
    process_files()