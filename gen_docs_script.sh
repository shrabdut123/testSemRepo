import ollama
import os

def generate_docs(file_path):
    with open(file_path, "r") as f:
        content = f.read()
    
    prompt = f"Generate documentation for this Kotlin code:\n\n{content}"
    
    response = ollama.chat(
        model="codegemma",
        messages=[{"role": "user", "content": prompt}]
    )
    
    docs = response["message"]["content"]
    
    # Write documentation at the top of the file
    with open(file_path, "w") as f:
        f.write(f"/* {docs} */\n\n{content}")

def process_folder(folder_path):
    for root, _, files in os.walk(folder_path):
        for file in files:
            if file.endswith(".kt"):  # Process only Kotlin files
                file_path = os.path.join(root, file)
                print(f"📄 Processing: {file_path}")
                generate_docs(file_path)

# Run for all Kotlin files in 'src/' folder
process_folder("src")