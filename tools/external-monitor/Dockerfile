# Standalone external monitor. Deliberately a separate image/container so it
# keeps running (and can alert) even if the WAAD backend/frontend are down.
FROM node:20-alpine

WORKDIR /app

# No third-party dependencies; copy the source only.
COPY package.json ./
COPY monitor.js ./

# Run as non-root.
USER node

CMD ["node", "monitor.js"]
