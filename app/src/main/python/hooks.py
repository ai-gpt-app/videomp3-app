def make_progress_hook(callback):
    def progress_hook(d):
        if d['status'] == 'downloading':
            downloaded = d.get('downloaded_bytes', 0)
            total = d.get('total_bytes') or d.get('total_bytes_estimate', 0)
            if total > 0:
                percent = int(downloaded * 100 / total)
                callback.onProgress(percent)
        elif d['status'] == 'finished':
            callback.onProgress(100)
    return progress_hook