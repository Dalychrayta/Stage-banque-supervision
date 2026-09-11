FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
# La page d'accueil livrée avec nginx n'est jamais écrasée par le build
# Angular (qui produit index.csr.html, pas index.html) : on la supprime
# pour qu'elle ne puisse pas être servie à la place de l'application.
RUN rm -f /usr/share/nginx/html/index.html
COPY --from=build /app/dist/bct-dashboard/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
