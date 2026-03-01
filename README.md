# 🤖 Conversational RAG Q&A Chatbot

> Chat with your PDF documents using Retrieval-Augmented Generation (RAG) — powered by **Groq**, **HuggingFace Embeddings**, **FAISS**, and **LangChain**.

[![Python](https://img.shields.io/badge/Python-3.10%2B-blue?logo=python)](https://python.org)
[![LangChain](https://img.shields.io/badge/LangChain-0.2%2B-green)](https://langchain.com)
[![Streamlit](https://img.shields.io/badge/Streamlit-1.35%2B-red?logo=streamlit)](https://streamlit.io)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 📌 What This Does

Upload one or more PDF documents and ask questions about them in natural language. The chatbot:

- **Understands context** — remembers previous questions in the same session
- **Retrieves accurately** — uses FAISS vector search to find the most relevant document chunks before answering
- **Runs fast** — Groq's inference API delivers near-instant responses
- **Stays honest** — if the answer isn't in your documents, it says so

---

## 🏗️ Architecture

```
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
```

**Key components:**

| Component | Role |
|-----------|------|
| `PyMuPDFLoader` | Extracts text from uploaded PDFs |
| `RecursiveCharacterTextSplitter` | Chunks documents (1000 chars, 150 overlap) |
| `HuggingFaceEmbeddings` | Converts chunks → dense vectors (`all-MiniLM-L6-v2`) |
| `FAISS` | Stores & retrieves vectors locally |
| `create_history_aware_retriever` | Reformulates follow-up questions using chat history |
| `RunnableWithMessageHistory` | Manages per-session conversation state |
| `ChatGroq` | LLM inference via Groq API |
| `Streamlit` | Web UI |

---

## 🚀 Quick Start

### 1. Clone the repo
```bash
git clone https://github.com/yash0304/rag-chatbot-groq.git
cd rag-chatbot-groq
```

### 2. Create a virtual environment
```bash
python -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
```

### 3. Install dependencies
```bash
pip install -r requirements.txt
```

### 4. Set up environment variables
```bash
cp .env.example .env
# Edit .env and add your HF_TOKEN (and optionally LANGCHAIN_API_KEY)
```

**Get your keys:**
- 🤗 HuggingFace token → [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens)
- ⚡ Groq API key → [console.groq.com](https://console.groq.com) *(enter in the app UI)*
- 🔍 LangSmith key → [smith.langchain.com](https://smith.langchain.com) *(optional — for tracing)*

### 5. Run the app
```bash
streamlit run app.py
```

---

## 🖥️ Usage

1. Open the app in your browser (`http://localhost:8501`)
2. Enter your **Groq API key** in the sidebar
3. Set a **Session ID** (use different IDs to keep conversations separate)
4. **Upload PDFs** using the file uploader
5. Ask questions in the chat input — the bot answers from your documents

---

## 📁 Project Structure

```
rag-chatbot-groq/
│
├── app.py                  # Main Streamlit application
├── requirements.txt        # Python dependencies
├── .env.example            # Environment variable template
├── .gitignore              # Git ignore rules
└── README.md               # This file
```

---

## ⚙️ Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `chunk_size` | 1000 | Characters per document chunk |
| `chunk_overlap` | 150 | Overlap between chunks for context continuity |
| `k` (retriever) | 4 | Number of chunks retrieved per query |
| `model` | `llama3-8b-8192` | Groq model used for inference |
| Embeddings model | `all-MiniLM-L6-v2` | HuggingFace sentence transformer |

---

## 🔧 Tech Stack

- **[LangChain](https://langchain.com)** — RAG orchestration, chains, memory
- **[Groq](https://groq.com)** — Ultra-fast LLM inference (LLaMA 3)
- **[FAISS](https://github.com/facebookresearch/faiss)** — Local vector similarity search
- **[HuggingFace](https://huggingface.co)** — Open-source sentence embeddings
- **[Streamlit](https://streamlit.io)** — Interactive web UI
- **[PyMuPDF](https://pymupdf.readthedocs.io)** — PDF text extraction

---

## 🗺️ Roadmap

- [ ] Support for `.docx` and `.txt` files
- [ ] Source citation with page numbers in answers
- [ ] Persistent chat history across sessions (SQLite)
- [ ] Deployable Docker container
- [ ] Option to switch between multiple Groq models

---

## 👤 Author

**Yash Agarwal**
- GitHub: [@yash0304](https://github.com/yash0304)
- LinkedIn: [linkedin.com/in/yash-agarwal](https://linkedin.com/in/yash-agarwal)
- Role: Associate Manager — Finance & Business Transformation, HCL Technologies
- MBA: Data Science & Operations Management, IFMR GSB (Rank 6)

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
