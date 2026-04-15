export type KeycloakTokenResponse = {
  access_token: string;
};

export type KeycloakErrorResponse = {
  error?: string;
  error_description?: string;
};
