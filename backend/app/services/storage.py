"""Raw file storage. Local disk (volume-mounted in Docker); swap for S3/GCS in prod."""

import uuid
from pathlib import Path

from app.core.config import get_settings

ALLOWED_EXTENSIONS = {".pdf", ".txt", ".md", ".png", ".jpg", ".jpeg"}


def save_upload(user_id: str, filename: str, data: bytes) -> str:
    settings = get_settings()
    ext = Path(filename).suffix.lower()
    if ext not in ALLOWED_EXTENSIONS:
        raise ValueError(f"Unsupported file type: {ext or '(none)'}")
    if len(data) > settings.max_upload_mb * 1024 * 1024:
        raise ValueError(f"File exceeds {settings.max_upload_mb} MB limit")
    dest_dir = Path(settings.storage_dir) / user_id
    dest_dir.mkdir(parents=True, exist_ok=True)
    dest = dest_dir / f"{uuid.uuid4()}{ext}"
    dest.write_bytes(data)
    return str(dest)


def delete_file(path: str | None) -> None:
    if not path:
        return
    try:
        Path(path).unlink(missing_ok=True)
    except OSError:
        pass
