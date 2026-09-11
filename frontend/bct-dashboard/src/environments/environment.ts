export const environment = {
  apiBaseUrl: 'http://localhost:8080/api',
  keycloak: {
    // Adresse de Keycloak vue par le NAVIGATEUR (doit correspondre à
    // KC_HOSTNAME dans docker-compose, c'est elle qui devient l'émetteur
    // du jeton). Les services, eux, passent par le réseau interne Docker.
    issuer: 'http://localhost:8180/realms/bct',
    clientId: 'bct-frontend'
  }
};
