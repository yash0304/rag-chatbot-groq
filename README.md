🤖 Conversational RAG Q&A Chatbot

Chat with your PDF documents using Retrieval-Augmented Generation (RAG) — powered by Groq, HuggingFace Embeddings, FAISS, and LangChain.

📌 What This Does
Upload one or more PDF documents and ask questions about them in natural language. The chatbot:

Understands context — remembers previous questions in the same session
Retrieves accurately — uses FAISS vector search to find the most relevant document chunks before answering
Runs fast — Groq's inference API delivers near-instant responses
Stays honest — if the answer isn't in your documents, it says so


🏗️ Architecture
User Question
     │
     ▼
History-Aware Retriever  ←──  Chat History
     │                              ▲
     ▼                              │
FAISS Vector Store                  │
(HuggingFace Embeddings)            │
     │                              │
     ▼                              │
Retrieved Context Chunks            │
     │                              │
     ▼                              │
Groq LLM (LLaMA 3)  ───────────────┘
     │
     ▼
Answer + Updated Chat History
Key components:
ComponentRolePyMuPDFLoaderExtracts text from uploaded PDFsRecursiveCharacterTextSplitterChunks documents (1000 chars, 150 overlap)HuggingFaceEmbeddingsConverts chunks → dense vectors (all-MiniLM-L6-v2)FAISSStores & retrieves vectors locallycreate_history_aware_retrieverReformulates follow-up questions using chat historyRunnableWithMessageHistoryManages per-session conversation stateChatGroqLLM inference via Groq APIStreamlitWeb UI

🚀 Quick Start
1. Clone the repo
bashgit clone https://github.com/yash0304/rag-chatbot-groq.git
cd rag-chatbot-groq
2. Create a virtual environment
bashpython -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
3. Install dependencies
bashpip install -r requirements.txt
4. Set up environment variables
bashcp .env.example .env
# Edit .env and add your HF_TOKEN (and optionally LANGCHAIN_API_KEY)
Get your keys:

🤗 HuggingFace token → huggingface.co/settings/tokens
⚡ Groq API key → console.groq.com (enter in the app UI)
🔍 LangSmith key → smith.langchain.com (optional — for tracing)

5. Run the app
bashstreamlit run app.py

🖥️ Usage

Open the app in your browser (http://localhost:8501)
Enter your Groq API key in the sidebar
Set a Session ID (use different IDs to keep conversations separate)
Upload PDFs using the file uploader
Ask questions in the chat input — the bot answers from your documents


📁 Project Structure
rag-chatbot-groq/
│
├── app.py                  # Main Streamlit application
├── requirements.txt        # Python dependencies
├── .env.example            # Environment variable template
├── .gitignore              # Git ignore rules
└── README.md               # This file

⚙️ Configuration
ParameterDefaultDescriptionchunk_size1000Characters per document chunkchunk_overlap150Overlap between chunks for context continuityk (retriever)4Number of chunks retrieved per querymodelllama3-8b-8192Groq model used for inferenceEmbeddings modelall-MiniLM-L6-v2HuggingFace sentence transformer

🔧 Tech Stack

LangChain — RAG orchestration, chains, memory
Groq — Ultra-fast LLM inference (LLaMA 3)
FAISS — Local vector similarity search
HuggingFace — Open-source sentence embeddings
Streamlit — Interactive web UI
PyMuPDF — PDF text extraction


🗺️ Roadmap

 Support for .docx and .txt files
 Source citation with page numbers in answers
 Persistent chat history across sessions (SQLite)
 Deployable Docker container
 Option to switch between multiple Groq models


👤 Author
Yash Modi

GitHub: @yash0304
LinkedIn: https://www.linkedin.com/in/yash-modi-77838978/
Role: Associate Manager — Finance & Business Transformation, HCL Technologies
MBA: Data Science & Operations Management, IFMR GSB (Rank 6)


📄 License
This project is licensed under the MIT License — see the LICENSE file for details
