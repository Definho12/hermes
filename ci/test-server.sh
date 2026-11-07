#!/bin/bash
# A tiny video (3 s, picture + sound) served on http://127.0.0.1:8765/test.mp4 for the download test.
set -e
mkdir -p /tmp/www
ffmpeg -loglevel error -y -f lavfi -i testsrc=size=640x360:rate=25 -f lavfi -i sine=frequency=440:sample_rate=44100 \
  -t 3 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest "/tmp/www/Video de teste.mp4"
cd /tmp/www && nohup python3 -m http.server 8765 --bind 127.0.0.1 > /tmp/www.log 2>&1 &
sleep 1
curl -sI "http://127.0.0.1:8765/Video%20de%20teste.mp4" | head -1
