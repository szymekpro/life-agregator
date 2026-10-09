import uvicorn

from app.factory import create_app
from config import load_config

app = create_app()

if __name__ == "__main__":
    config = load_config()
    # Start with `python main.py` so PORT (default 5000) applies. Plain `uvicorn main:app` ignores
    # this block and falls back to uvicorn's own default port 8000.
    uvicorn.run("main:app", host="0.0.0.0", port=config.port, reload=config.debug)
