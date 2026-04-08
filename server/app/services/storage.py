"""
Media file storage service.

Provides async file I/O for event media (video files) stored under MEDIA_ROOT.
Files are organized by user_id and event_id to prevent path collisions.

Path layout: {MEDIA_ROOT}/{user_id}/{event_id}/{filename}
Returned paths are MEDIA_ROOT-relative (e.g. "42/7/video.mp4").
"""

import os

import aiofiles

from app.config import settings


async def save_media(user_id: int, event_id: int, data: bytes, filename: str) -> str:
    """
    Write media bytes to {MEDIA_ROOT}/{user_id}/{event_id}/{filename}.

    Creates the directory structure if it does not exist.

    @param user_id: ID of the owning user (used as top-level directory)
    @param event_id: ID of the associated event (used as sub-directory)
    @param data: raw bytes to write (may be AES-GCM ciphertext)
    @param filename: file name to use on disk (e.g. "video.mp4" or "video.enc")
    @return: MEDIA_ROOT-relative path (e.g. "42/7/video.mp4")
    """
    directory = os.path.join(settings.MEDIA_ROOT, str(user_id), str(event_id))
    os.makedirs(directory, exist_ok=True)

    full_path = os.path.join(directory, filename)
    async with aiofiles.open(full_path, "wb") as f:
        await f.write(data)

    # Return path relative to MEDIA_ROOT
    return os.path.join(str(user_id), str(event_id), filename)


async def load_media(path: str) -> bytes:
    """
    Read media bytes from {MEDIA_ROOT}/{path}.

    @param path: MEDIA_ROOT-relative path as returned by save_media
    @return: raw bytes of the file
    @raises FileNotFoundError: if the file does not exist
    """
    full_path = os.path.join(settings.MEDIA_ROOT, path)
    async with aiofiles.open(full_path, "rb") as f:
        return await f.read()


async def delete_media(path: str) -> None:
    """
    Remove the file at {MEDIA_ROOT}/{path} if it exists.

    Silently no-ops if the file is already absent.

    @param path: MEDIA_ROOT-relative path as returned by save_media
    """
    full_path = os.path.join(settings.MEDIA_ROOT, path)
    try:
        os.remove(full_path)
    except FileNotFoundError:
        pass
