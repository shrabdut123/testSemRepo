import openai
import os

# Set your Azure OpenAI API details
AZURE_OPENAI_ENDPOINT = "https://aidocinstance.openai.azure.com/"  # Replace with your endpoint
AZURE_OPENAI_API_KEY = os.getenv("AZURE_OPENAI_API_KEY")  # Get API key from GitHub secret
DEPLOYMENT_NAME = "gpt-4o"  # Replace with your model deployment name

# Debugging API Key
print("Checking API Key...")
if not AZURE_OPENAI_API_KEY:
    raise ValueError("Azure OpenAI API key is not set. Please set it in the environment variables.")
print("API Key is set.")

# Configure OpenAI client for Azure
print("Configuring OpenAI client...")
openai.api_base = AZURE_OPENAI_ENDPOINT
openai.api_key = AZURE_OPENAI_API_KEY
openai.api_type = "azure"
openai.api_version = "2024-05-01-preview"  # Adjust based on the latest available version
print("OpenAI client configured.")

SRC_FOLDER = "src"

# Function to generate documentation
def generate_documentation(code):
    print("Generating documentation for a Kotlin function...")

    prompt = f"""
    Generate detailed documentation for the following Kotlin function using the standard format:

    ## Overview
    Provide a brief description of what the function does.

    ## Function Signature
    Format the function signature as a code block.

    ## Parameters
    List all parameters with descriptions.

    ## Return Value
    Describe the return type and what it represents.

    ## Functionality
    Break down the key steps in the function.

    ## Usage Example
    Provide a sample Kotlin usage example.

    Here is the function:

    ```kotlin
    {code}
    ```
    """
    
    messages = [{"role": "user", "content": prompt}]
    
    try:
        print("Calling Azure OpenAI API...")
        response = openai.ChatCompletion.create(
            engine=DEPLOYMENT_NAME,
            messages=messages,
            max_tokens=800,
            temperature=0.7,
            top_p=0.95,
            frequency_penalty=0,
            presence_penalty=0,
            stop=None,
        )
        print("Documentation generated successfully.")
        return response["choices"][0]["message"]["content"]

    except Exception as e:
        print(f"Error while generating documentation: {e}")
        return None

# Function to traverse files and update documentation
def traverse_and_update_files():
    print(f"Traversing directory: {SRC_FOLDER}")
    
    updated_files = []
    
    for dirpath, _, filenames in os.walk(SRC_FOLDER):
        for file_name in filenames:
            if file_name.endswith(".kt"):  # Process only Kotlin files
                file_path = os.path.join(dirpath, file_name)
                
                print(f"Processing file: {file_path}")
                
                with open(file_path, "r") as f:
                    code = f.read()

                # Generate new documentation
                documentation = generate_documentation(code)
                
                if documentation:
                    # Append the generated documentation at the beginning of the file
                    updated_code = f"/*\n{documentation}\n*/\n{code}"

                    # Write updated code back to the file
                    with open(file_path, "w") as f:
                        f.write(updated_code)

                    updated_files.append(file_path)
                    print(f"Updated file: {file_path}")
                else:
                    print(f"Skipping file (failed to generate documentation): {file_path}")
    
    # Save the list of updated files for GitHub Actions
    if updated_files:
        with open("updated_files.txt", "w") as f:
            f.writelines("\n".join(updated_files))
        print(f"Saved updated files list: {updated_files}")
    else:
        print("No files updated.")

    return updated_files

# Main execution
if __name__ == "__main__":
    print("Starting documentation generation process...")
    updated_files = traverse_and_update_files()
    
    if updated_files:
        print(f"Updated {len(updated_files)} files. Changes will be committed in the workflow.")
    else:
        print("No files were updated.")