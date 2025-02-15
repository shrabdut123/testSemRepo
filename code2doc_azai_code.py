import openai
import os

# Set your Azure OpenAI API details
AZURE_OPENAI_ENDPOINT = "https://aidocinstance.openai.azure.com/"  # Replace with your endpoint
AZURE_OPENAI_API_KEY = os.getenv("AZURE_OPENAI_API_KEY")  # Get API key from GitHub secret
DEPLOYMENT_NAME = "gpt-4o"  # Replace with your model deployment name

# Ensure the API key is set
if not AZURE_OPENAI_API_KEY:
    raise ValueError("Azure OpenAI API key is not set. Please set it in the environment variables.")

# Configure OpenAI client for Azure
openai.api_base = AZURE_OPENAI_ENDPOINT
openai.api_key = AZURE_OPENAI_API_KEY
openai.api_type = "azure"
openai.api_version = "2024-05-01-preview"  # Adjust based on the latest available version
code = """
suspend fun fetchFullServeTimeWindows(
        retailUnit: String,
        countryCode: String,
        storeId: String,
        requestedItems: Map<ItemNo, Float>,
    ): CheckoutTimeWindowsResponse {
        val fullServeArticles = getFullServeArticles(requestedItems, storeId, countryCode)
        val deliveryArrangementResponse = getDeliveryArrangements(retailUnit, countryCode, storeId, fullServeArticles)
        val timeWindowsResponse = getTimeWindows(retailUnit, deliveryArrangementResponse)
        saveDeliveryData(deliveryArrangementResponse, timeWindowsResponse)
        return timeWindowsResponse
    }
"""
# Chat prompt
prompt = f"Generate documentation for the following Kotlin code:\n{code}"
messages=[{"role": "user", "content": prompt}]

# Call Azure OpenAI API directly
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

# Print response
print(response["choices"][0]["message"]["content"])