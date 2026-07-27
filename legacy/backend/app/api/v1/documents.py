from fastapi import (
    APIRouter,
    BackgroundTasks,
    Depends,
    File,
    Form,
    HTTPException,
    UploadFile,
    status,
)
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.session import get_db
from app.models import Document, DocumentChunk, User
from app.schemas.document import ChunkOut, DocumentOut
from app.services import gamification, storage
from app.services.vector import get_vector_store
from app.workers.ingestion import process_document

router = APIRouter(prefix="/documents", tags=["documents"])


@router.post("", response_model=DocumentOut, status_code=status.HTTP_202_ACCEPTED)
async def upload_document(
    background: BackgroundTasks,
    file: UploadFile = File(...),
    title: str | None = Form(default=None),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    data = await file.read()
    try:
        path = storage.save_upload(user.id, file.filename or "upload", data)
    except ValueError as exc:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, str(exc)) from exc

    doc = Document(
        user_id=user.id,
        title=title or (file.filename or "Untitled"),
        filename=file.filename or "upload",
        mime_type=file.content_type or "application/octet-stream",
        storage_path=path,
        status="processing",
    )
    db.add(doc)
    db.flush()  # assign doc.id before it is used as the XP event ref
    gamification.award(db, user.id, "document_uploaded", ref_id=doc.id)
    db.commit()
    background.add_task(process_document, doc.id)
    return doc


@router.get("", response_model=list[DocumentOut])
def list_documents(
    status_filter: str | None = None,
    domain: str | None = None,
    limit: int = 50,
    offset: int = 0,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    stmt = select(Document).where(Document.user_id == user.id)
    if status_filter:
        stmt = stmt.where(Document.status == status_filter)
    if domain:
        stmt = stmt.where(Document.domain == domain)
    stmt = stmt.order_by(Document.created_at.desc()).limit(min(limit, 200)).offset(offset)
    return db.scalars(stmt).all()


def _owned_document(db: Session, user: User, document_id: str) -> Document:
    doc = db.get(Document, document_id)
    if doc is None or doc.user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Document not found")
    return doc


@router.get("/{document_id}", response_model=DocumentOut)
def get_document(document_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _owned_document(db, user, document_id)


@router.get("/{document_id}/chunks", response_model=list[ChunkOut])
def get_chunks(document_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    doc = _owned_document(db, user, document_id)
    return db.scalars(
        select(DocumentChunk).where(DocumentChunk.document_id == doc.id).order_by(DocumentChunk.seq)
    ).all()


@router.delete("/{document_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_document(
    document_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)
):
    doc = _owned_document(db, user, document_id)
    get_vector_store().delete_document(user.id, doc.id)
    storage.delete_file(doc.storage_path)
    db.delete(doc)
    db.commit()
