#Importing necessary libraries
from langchain_groq import ChatGroq
from langchain_core.prompts import ChatPromptTemplate,MessagesPlaceholder
from langchain_community.document_loaders import PyMuPDFLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_community.vectorstores import FAISS
from langchain.chains.combine_documents import create_stuff_documents_chain
from langchain.chains import create_retrieval_chain
from langchain.chains.history_aware_retriever import create_history_aware_retriever
from langchain_core.messages import HumanMessage, AIMessage
from langchain_community.chat_message_histories import ChatMessageHistory
from langchain_core.chat_history import BaseChatMessageHistory
from langchain_core.runnables import RunnableWithMessageHistory


import streamlit as st
from dotenv import load_dotenv
import os

#loading environment and getting all api_keys and keeping tracing system on for langchain.
load_dotenv()
os.environ['HF_TOKEN'] = os.getenv('HF_TOKEN')
os.environ["LANGCHAIN_PROJECT"] = "my-rag-bot-groq"
os.environ["LANGCHAIN_TRACING_V2"] = "true"
st.title("Conversational RAG Q&A chatbot with Groq and ChatGPT OSS")
groq_api_key = st.text_input("Enter your Groq API Key:  ",type="password")

#initializing session_id which can be further be used for showing and utilizing chat history.
session_id=st.text_input("Session ID",value="default_session")

#below index_folder will ensure every vectorstore will be saved in this folder for future uses.
index_folder = r'C:\Users\Yash\Desktop\Python\Langchain Projects\1. Q&A Chatbot\faiss_index'
os.makedirs(index_folder, exist_ok=True)

#using uploading files feature in streamlit and saving it into temporary folder to use it in future for chatting with user.
uploaded_files = st.file_uploader(label="Kindly upload PDFs to perform RAG operations: ",type=['pdf'],accept_multiple_files=True)
if groq_api_key:     
    if uploaded_files:
        docs = []
        for uploaded_file in uploaded_files:
            temp_file = "./temp.pdf"
            with open(temp_file,mode="wb") as file:
                file.write(uploaded_file.getvalue())
                file_name = file.name
            loader = PyMuPDFLoader(temp_file)
            docs.extend(loader.load())

        #Asking user prompt from the user
        user_input = st.chat_input("Please ask your question from the given source: ")

        #Generating responses; doing data ingestion, splitting into chunks and embedding the same into vector databases. Saving the vector DB afterwards.
        def create_vector():
                if "vectors" not in st.session_state:
                        st.session_state.vectors = None        
                        st.session_state.splitter = RecursiveCharacterTextSplitter()
                        st.session_state.document_chunks = st.session_state.splitter.split_documents(documents=docs)
                        st.session_state.embedding = HuggingFaceEmbeddings()
                        st.session_state.vectors = FAISS.from_documents(st.session_state.document_chunks, st.session_state.embedding)
                        st.session_state.vectors.save_local(folder_path=index_folder,index_name="RAG_Groq_hisory")

        #Invoking the generating response function after user inputting prompt.
        if user_input:
            create_vector()
            contextualize_q_system_prompt = (
                                                "Given a chat history and the latest user question "
                                                "which might reference context in the chat history, "
                                                "formulate a standalone question which can be understood "
                                                "without the chat history. Do NOT answer the question, "
                                                "just reformulate it if needed and otherwise return it as is."
                                            )
            contextualize_q_prompts = ChatPromptTemplate.from_messages(
                                            [
                                                ("system",contextualize_q_system_prompt),
                                                MessagesPlaceholder("chat_history"),
                                                ("human","{input}")
                                            ]
                                            )
            
            vectors = FAISS.load_local(folder_path=index_folder,
                                embeddings=st.session_state.embedding,
                                index_name = "RAG_Groq_hisory",
                                allow_dangerous_deserialization=True)

            retrieval = vectors.as_retriever()
            llm = ChatGroq(model="openai/gpt-oss-20b",api_key=groq_api_key)
            
            #history aware retrieval chain can be used for context fetching prompt i.e. from where we need to take context.
            history_aware_chain = create_history_aware_retriever(llm=llm, retriever=retrieval,prompt=contextualize_q_prompts)


            qa_system_prompt = (
                                "You are a helpful assistant. Please provide answer best to your knowledge on 5 lines."
                                "{context} can be used for context."
                                )
            qa_prompt = ChatPromptTemplate.from_messages(
                                            [
                                                ("system",qa_system_prompt),
                                                MessagesPlaceholder("chat_history"),
                                                ("human","{input}")
                                            ]
                                            )

            # Below two chains can chain together conext prompt, QA prompt and include LLM and output parser.
            basic_chain = create_stuff_documents_chain(llm,qa_prompt)
            history_retrival_chain = create_retrieval_chain(history_aware_chain,basic_chain)

            #initializing store to pu session_id or chat history.
            if 'store' not in st.session_state:
                st.session_state.store = {}

            def get_session_history(session_id:str)->BaseChatMessageHistory:
                if session_id not in st.session_state.store:
                    st.session_state.store[session_id] = ChatMessageHistory()
                return st.session_state.store[session_id]

            session_history = get_session_history(session_id) 

            #manin function on which generating history chain will be invoked and also response will get generated.
            conversational_rag_chain = RunnableWithMessageHistory(
                get_session_history=get_session_history,
                input_messages_key="input",
                history_messages_key="chat_history", #this parameter will help LLM & create_stuff_document_chain to store chat message history. 
                output_messages_key="answer",
                runnable=history_retrival_chain
            )

            #Invoking response        
            history_response = conversational_rag_chain.invoke(
                {"input":user_input},
                config= {
                        "configurable":{"session_id" : session_id}},
                        )["answer"]
            
            #writing responses.
            st.write("Assistant:", history_response)
            with st.expander("Store"):
                 st.write("store",st.session_state.store)
            
            with st.sidebar:
                st.subheader("Chat History")
                for msg in session_history.messages:
                    st.markdown(f"**{msg.type.capitalize()}:** {msg.content}")

else:
     st.error("Enter API key")