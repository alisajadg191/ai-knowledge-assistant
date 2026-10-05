#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
npm ci --prefix frontend
npm run build --prefix frontend
mkdir -p src/main/resources/static
find src/main/resources/static -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp -R frontend/dist/. src/main/resources/static/
./mvnw clean verify
printf '\nRun: java -jar target/ai-knowledge-assistant-1.0.0.jar\nOpen http://127.0.0.1:8081\n'
