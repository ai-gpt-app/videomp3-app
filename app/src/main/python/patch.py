import subprocess
import yt_dlp.utils

# Prevent yt-dlp from spawning subprocesses on Android
_original_popen = subprocess.Popen

class SafePopen:
    def __init__(self, *args, **kwargs):
        raise OSError("Subprocess not supported on Android")

# Only patch JS-related subprocess calls
try:
    import yt_dlp.jsinterp
    yt_dlp.jsinterp.Popen = SafePopen
except Exception:
    pass