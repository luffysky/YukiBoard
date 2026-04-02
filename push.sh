#!/bin/bash
# YukiBoard - Push to GitHub script
set -e

REMOTE_URL="https://github.com/luffysky/YukiBoard.git"
BRANCH="main"

cd "$(dirname "$0")"

# Initialize git repo if not already
if [ ! -d .git ]; then
    echo "Initializing git repository..."
    git init
    git branch -M "$BRANCH"
fi

# Set remote
if git remote get-url origin >/dev/null 2>&1; then
    git remote set-url origin "$REMOTE_URL"
else
    git remote add origin "$REMOTE_URL"
fi

# Stage all files
git add -A

# Commit
git commit -m "Initial commit: YukiBoard IME project scaffold

- Full Android IME service with MVVM architecture
- Lottie character animation system with placeholder idle animation
- OpenGL ES 2.0 flow light effects
- Dual-track input routing (English QWERTY + Bopomofo)
- Theme system (dark/light) with ThemeManager
- Settings activity with theme, appearance mode, and language options
- Launcher icons for all densities (mdpi through xxxhdpi)
- Speech input engine integration
- Candidate word management system"

# Push
echo "Pushing to $REMOTE_URL ..."
git push -u origin "$BRANCH"

echo "Done! Pushed to $REMOTE_URL"
