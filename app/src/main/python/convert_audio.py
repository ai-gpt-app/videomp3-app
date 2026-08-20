import subprocess
from imageio_ffmpeg import get_ffmpeg_exe


def convert_to_mp3(input_path: str, output_path: str, quality: str = "medium") -> str:
    ffmpeg_exe = get_ffmpeg_exe()

    bitrate = {
        "low": "64k",
        "medium": "128k",
        "high": "192k",
    }.get((quality or "medium").lower(), "128k")

    subprocess.run([
        ffmpeg_exe,
        '-y',
        '-i', input_path,
        '-vn',
        '-ar', '44100',
        '-ac', '2',
        '-b:a', bitrate,
        output_path,
    ], check=True)
    return output_path
