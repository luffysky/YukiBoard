"""
Faster-Whisper Server — YukiBoard 語音辨識後端

FastAPI server wrapping faster-whisper for speech-to-text.
Deploy on Zeabur with GPU for best performance.

Endpoints:
  POST /v1/transcribe       — upload audio, get text
  POST /v1/transcribe/stream — upload audio, SSE partial results
  GET  /health              — health check
"""

import io
import os
import time
import logging
from typing import Optional

import uvicorn
from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.responses import StreamingResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from faster_whisper import WhisperModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("whisper-server")

# ── Config ──
MODEL_SIZE = os.getenv("WHISPER_MODEL", "base")
DEVICE = os.getenv("WHISPER_DEVICE", "auto")  # "auto", "cpu", "cuda"
COMPUTE_TYPE = os.getenv("WHISPER_COMPUTE_TYPE", "auto")  # "auto", "int8", "float16"
MAX_FILE_SIZE_MB = int(os.getenv("MAX_FILE_SIZE_MB", "25"))
API_KEY = os.getenv("WHISPER_API_KEY", "")  # optional auth

app = FastAPI(
    title="Faster-Whisper Server",
    description="YukiBoard speech-to-text API powered by faster-whisper",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Model loading ──
model: Optional[WhisperModel] = None


@app.on_event("startup")
def load_model():
    global model
    logger.info(f"Loading model: {MODEL_SIZE} (device={DEVICE}, compute={COMPUTE_TYPE})")
    start = time.time()
    model = WhisperModel(MODEL_SIZE, device=DEVICE, compute_type=COMPUTE_TYPE)
    elapsed = time.time() - start
    logger.info(f"Model loaded in {elapsed:.1f}s")


def check_auth(api_key: Optional[str]):
    """Optional API key check."""
    if API_KEY and api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")


# ── Endpoints ──

@app.get("/health")
def health():
    return {
        "status": "ok",
        "model": MODEL_SIZE,
        "device": DEVICE,
    }


@app.post("/v1/transcribe")
async def transcribe(
    file: UploadFile = File(...),
    language: Optional[str] = Form(None),
    model_size: Optional[str] = Form(None),
    api_key: Optional[str] = Form(None),
):
    """Transcribe audio file to text."""
    check_auth(api_key)

    content = await file.read()
    if len(content) > MAX_FILE_SIZE_MB * 1024 * 1024:
        raise HTTPException(status_code=413, detail=f"File too large (max {MAX_FILE_SIZE_MB}MB)")

    audio_io = io.BytesIO(content)
    start = time.time()

    try:
        segments, info = model.transcribe(
            audio_io,
            language=language,
            beam_size=5,
            vad_filter=True,
            vad_parameters=dict(
                min_silence_duration_ms=500,
                speech_pad_ms=200,
            ),
        )

        full_text = ""
        segment_list = []
        for seg in segments:
            full_text += seg.text
            segment_list.append({
                "start": round(seg.start, 2),
                "end": round(seg.end, 2),
                "text": seg.text.strip(),
            })

        elapsed = time.time() - start
        logger.info(f"Transcribed {info.duration:.1f}s audio in {elapsed:.1f}s, lang={info.language}")

        return {
            "text": full_text.strip(),
            "language": info.language,
            "language_probability": round(info.language_probability, 3),
            "duration": round(info.duration, 2),
            "processing_time": round(elapsed, 2),
            "segments": segment_list,
        }

    except Exception as e:
        logger.error(f"Transcription failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/v1/transcribe/stream")
async def transcribe_stream(
    file: UploadFile = File(...),
    language: Optional[str] = Form(None),
    api_key: Optional[str] = Form(None),
):
    """Transcribe with SSE streaming partial results."""
    check_auth(api_key)

    content = await file.read()
    if len(content) > MAX_FILE_SIZE_MB * 1024 * 1024:
        raise HTTPException(status_code=413, detail=f"File too large (max {MAX_FILE_SIZE_MB}MB)")

    audio_io = io.BytesIO(content)

    def generate():
        try:
            segments, info = model.transcribe(
                audio_io,
                language=language,
                beam_size=5,
                vad_filter=True,
            )

            accumulated = ""
            for seg in segments:
                accumulated += seg.text
                import json
                data = json.dumps({
                    "type": "partial",
                    "text": accumulated.strip(),
                    "segment": seg.text.strip(),
                    "start": round(seg.start, 2),
                    "end": round(seg.end, 2),
                }, ensure_ascii=False)
                yield f"data: {data}\n\n"

            # Final event
            import json
            final = json.dumps({
                "type": "final",
                "text": accumulated.strip(),
                "language": info.language,
                "duration": round(info.duration, 2),
            }, ensure_ascii=False)
            yield f"data: {final}\n\n"

        except Exception as e:
            import json
            error = json.dumps({"type": "error", "message": str(e)})
            yield f"data: {error}\n\n"

    return StreamingResponse(
        generate(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
        },
    )


if __name__ == "__main__":
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=False)
