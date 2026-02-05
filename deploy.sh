#!/bin/bash

# SSL Expiry Checker Deployment Script
# Docker-based deployment for Linux/macOS

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

echo -e "${GREEN}=== SSL Expiry Checker Deployment Script ===${NC}"

if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi

DOCKER_COMPOSE_CMD=""
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker compose"
    print_info "Using Docker Compose V2 (plugin)"
elif command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker-compose"
    print_info "Using Docker Compose V1 (standalone)"
else
    print_error "Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

ensure_docker_running() {
    if docker info >/dev/null 2>&1; then
        print_info "Docker daemon is running"
        return
    fi

    OS_NAME="$(uname -s)"
    case "$OS_NAME" in
        Linux)
            if command -v systemctl >/dev/null 2>&1; then
                print_warning "Docker service is not running. Starting Docker..."
                sudo systemctl start docker
            else
                print_warning "systemctl is not available. Please start Docker manually."
            fi
            ;;
        Darwin)
            print_warning "Docker Desktop is not running. Attempting to start..."
            if ! pgrep -x "Docker" >/dev/null 2>&1; then
                if ! open -a Docker >/dev/null 2>&1; then
                    print_error "Could not launch Docker Desktop. Please start it manually."
                    exit 1
                fi
            fi
            print_info "Waiting for Docker Desktop to initialize..."
            ELAPSED=0
            MAX_WAIT=120
            until docker info >/dev/null 2>&1; do
                sleep 3
                ELAPSED=$((ELAPSED + 3))
                if [ "$ELAPSED" -ge "$MAX_WAIT" ]; then
                    print_error "Timed out waiting for Docker Desktop. Please ensure it is running."
                    exit 1
                fi
            done
            ;;
        *)
            print_warning "Automatic Docker start is not supported on this OS. Please ensure Docker is running."
            ;;
    esac

    if ! docker info >/dev/null 2>&1; then
        print_error "Docker is still not running. Please start Docker manually."
        exit 1
    fi

    print_info "Docker daemon is running"
}

ensure_docker_running

COMPOSE_FILE=""
if [ -f "./docker-compose.yml" ]; then
    COMPOSE_FILE="./docker-compose.yml"
elif [ -f "./compose.yaml" ]; then
    COMPOSE_FILE="./compose.yaml"
else
    print_error "No docker compose file found (docker-compose.yml or compose.yaml)."
    exit 1
fi

COMPOSE="$DOCKER_COMPOSE_CMD -f $COMPOSE_FILE"

SERVICES="$($COMPOSE config --services 2>/dev/null || true)"
APP_SERVICE="${APP_SERVICE:-}"
if [ -z "$APP_SERVICE" ] && [ -n "$SERVICES" ]; then
    if echo "$SERVICES" | grep -qx "ssl-expiry-checker"; then
        APP_SERVICE="ssl-expiry-checker"
    elif echo "$SERVICES" | grep -qx "app"; then
        APP_SERVICE="app"
    else
        APP_SERVICE="$(echo "$SERVICES" | head -n 1)"
    fi
fi

APP_PORT="${APP_PORT:-8011}"
APP_HEALTH_URL="${APP_HEALTH_URL:-http://localhost:${APP_PORT}/actuator/health}"

print_info "Docker and Docker Compose are installed and running"
echo ""
echo "Select deployment action:"
echo "1) Build and start application"
echo "2) Stop application"
echo "3) Restart application"
echo "4) View logs"
echo "5) Rebuild application (after code changes)"
echo "6) Check application status"
echo "7) Run one-off test profile"
echo "8) Exit"
read -p "Enter your choice [1-8]: " choice

case $choice in
    1)
        print_info "Building and starting application..."
        $COMPOSE build
        if [ -n "$APP_SERVICE" ]; then
            $COMPOSE up -d "$APP_SERVICE"
        else
            $COMPOSE up -d
        fi
        print_info "Application started successfully"
        $COMPOSE ps
        ;;
    2)
        print_info "Stopping application..."
        if [ -n "$APP_SERVICE" ]; then
            $COMPOSE stop "$APP_SERVICE"
        else
            $COMPOSE stop
        fi
        print_info "Application stopped"
        ;;
    3)
        print_info "Restarting application..."
        if [ -n "$APP_SERVICE" ]; then
            $COMPOSE restart "$APP_SERVICE"
        else
            $COMPOSE restart
        fi
        print_info "Application restarted"
        $COMPOSE ps
        ;;
    4)
        print_info "Displaying logs (Ctrl+C to exit)..."
        if [ -n "$APP_SERVICE" ]; then
            $COMPOSE logs -f "$APP_SERVICE"
        else
            $COMPOSE logs -f
        fi
        ;;
    5)
        print_info "Rebuilding application..."
        if [ -n "$APP_SERVICE" ]; then
            $COMPOSE stop "$APP_SERVICE" || true
            $COMPOSE rm -f "$APP_SERVICE" || true
        else
            $COMPOSE down
        fi
        $COMPOSE build --no-cache
        if [ -n "$APP_SERVICE" ]; then
            $COMPOSE up -d "$APP_SERVICE"
        else
            $COMPOSE up -d
        fi
        print_info "Application rebuilt and started"
        $COMPOSE ps
        ;;
    6)
        print_info "Application status:"
        $COMPOSE ps
        echo ""
        if [ -n "$APP_SERVICE" ]; then
            print_info "Checking health endpoint..."
            if command -v curl >/dev/null 2>&1; then
                if curl -s "$APP_HEALTH_URL" > /dev/null; then
                    echo -e "${GREEN}Application is responding${NC}"
                    curl -s "$APP_HEALTH_URL"
                    echo ""
                else
                    echo -e "${RED}Application is not responding at ${APP_HEALTH_URL}${NC}"
                fi
            else
                print_warning "curl is not installed; skipping health check."
            fi
        else
            print_warning "No application service detected in $COMPOSE_FILE. Only infrastructure services are running."
        fi
        ;;
    7)
        if [ -n "$APP_SERVICE" ]; then
            print_info "Rebuilding and running one-off test profile..."
            $COMPOSE build --no-cache "$APP_SERVICE"
            $COMPOSE run --rm -e SPRING_PROFILES_ACTIVE=test "$APP_SERVICE"
        else
            print_warning "No application service detected. Add the app to docker-compose and set APP_SERVICE."
        fi
        ;;
    8)
        print_info "Exiting..."
        exit 0
        ;;
    *)
        print_error "Invalid choice"
        exit 1
        ;;
esac
