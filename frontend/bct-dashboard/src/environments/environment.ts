export const environment = {
  apiBaseUrl: 'http://localhost:8080/api',
  keycloak: {
    // Doit être identique à l'émetteur (iss) des jetons — cf. KC_HOSTNAME
    // dans docker-compose. Joignable depuis le navigateur ET les conteneurs.
    issuer: 'http://host.docker.internal:8180/realms/bct',
    clientId: 'bct-frontend'
  }
};
