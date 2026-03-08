#!/bin/bash
# Deploy Aura backend to Google Cloud Run
#
# Prerequisites:
#   - gcloud CLI installed and authenticated
#   - A Google Cloud project with billing enabled
#   - Cloud Run API enabled: gcloud services enable run.googleapis.com
#   - Artifact Registry API enabled: gcloud services enable artifactregistry.googleapis.com
#
# Usage:
#   chmod +x deploy.sh
#   ./deploy.sh

set -e

# ─── Configuration ─────────────────────────────────────────────────
PROJECT_ID=$(gcloud config get-value project)
REGION="us-central1"
SERVICE_NAME="aura-backend"
IMAGE_NAME="gcr.io/${PROJECT_ID}/${SERVICE_NAME}"

echo "🚀 Deploying Aura Backend to Cloud Run"
echo "   Project: ${PROJECT_ID}"
echo "   Region:  ${REGION}"
echo "   Service: ${SERVICE_NAME}"
echo ""

# ─── Build & Push Container ────────────────────────────────────────
echo "📦 Building container image..."
gcloud builds submit --tag "${IMAGE_NAME}" .

# ─── Deploy to Cloud Run ───────────────────────────────────────────
echo "☁️  Deploying to Cloud Run..."
gcloud run deploy "${SERVICE_NAME}" \
    --image "${IMAGE_NAME}" \
    --region "${REGION}" \
    --platform managed \
    --allow-unauthenticated \
    --set-env-vars "GOOGLE_GENAI_API_KEY=$(grep GOOGLE_GENAI_API_KEY aura_agent/.env | cut -d= -f2)" \
    --set-env-vars "OPENWEATHER_API_KEY=$(grep OPENWEATHER_API_KEY aura_agent/.env | cut -d= -f2)" \
    --memory 1Gi \
    --cpu 1 \
    --timeout 300 \
    --max-instances 10 \
    --session-affinity

# ─── Get URL ───────────────────────────────────────────────────────
SERVICE_URL=$(gcloud run services describe "${SERVICE_NAME}" --region "${REGION}" --format "value(status.url)")

echo ""
echo "✅ Deployment complete!"
echo "   URL: ${SERVICE_URL}"
echo ""
echo "   Test with:"
echo "   curl ${SERVICE_URL}/health"
echo ""
echo "   Update your Android app's BASE_URL to: ${SERVICE_URL}"
