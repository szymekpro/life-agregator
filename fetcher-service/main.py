import uvicorn

from app.factory import create_app
from config import load_config

app = create_app()

if __name__ == "__main__":
    config = load_config()
    uvicorn.run("main:app", host="0.0.0.0", port=5000, reload=config.debug)
