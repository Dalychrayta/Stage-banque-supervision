FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
# Met a jour les paquets systeme de l'image de base (openssl, expat, libuuid...).
# Sans cette ligne on depend de la date a laquelle l'editeur de l'image republie
# la sienne : des correctifs publies restent absents pendant des semaines. En
# echange, la construction est un peu moins reproductible d'un jour a l'autre.
RUN apk upgrade --no-cache
COPY --from=build /app/dist/bct-dashboard/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
