"""Knowledge graph: domains ↔ documents ↔ tags with weighted edges."""

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Document


def build_graph(db: Session, user_id: str) -> dict:
    docs = db.scalars(
        select(Document).where(Document.user_id == user_id, Document.status == "ready")
    ).all()

    nodes: dict[str, dict] = {}
    edges: list[dict] = []

    for doc in docs:
        domain_key = f"domain:{doc.domain or 'Uncharted Lands'}"
        if domain_key not in nodes:
            nodes[domain_key] = {
                "id": domain_key,
                "label": doc.domain or "Uncharted Lands",
                "type": "domain",
                "size": 0,
            }
        nodes[domain_key]["size"] += 1

        doc_key = f"doc:{doc.id}"
        nodes[doc_key] = {"id": doc_key, "label": doc.title, "type": "document", "size": 1}
        edges.append({"source": domain_key, "target": doc_key, "weight": 2})

        for tag in doc.tags:
            tag_key = f"tag:{tag.name}"
            if tag_key not in nodes:
                nodes[tag_key] = {"id": tag_key, "label": tag.name, "type": "tag", "size": 0}
            nodes[tag_key]["size"] += 1
            edges.append({"source": doc_key, "target": tag_key, "weight": 1})

    return {"nodes": list(nodes.values()), "edges": edges}
